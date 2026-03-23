package com.wsi.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wsi.platform.common.MessageUtil;
import com.wsi.platform.entity.Annotation;
import com.wsi.platform.entity.Label;
import com.wsi.platform.entity.Slide;
import com.wsi.platform.mapper.AnnotationMapper;
import com.wsi.platform.mapper.LabelMapper;
import com.wsi.platform.mapper.SlideMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Phase-1 import service: supports CUSTOM_JSON (platform-native) and COCO JSON.
 *
 * CUSTOM_JSON format:
 * {
 *   "labels": [ { "id", "name", "color", "classIndex" } ],
 *   "annotations": [ { "labelId", "annoType", "coordinates" } ]
 * }
 *
 * COCO format: standard instances_*.json with images / annotations / categories.
 *
 * Import mode REPLACE: deletes all existing annotations before inserting.
 * Import mode MERGE:   appends new annotations on top of existing ones.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportService {

    private final LabelMapper labelMapper;
    private final AnnotationMapper annotationMapper;
    private final SlideMapper slideMapper;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;
    private final MessageUtil messageUtil;

    /** Auto-assign palette for imported categories that carry no color info. */
    private static final String[] PALETTE = {
            "#EF4444", "#F97316", "#EAB308", "#22C55E", "#14B8A6",
            "#3B82F6", "#8B5CF6", "#EC4899", "#6366F1", "#84CC16"
    };

    public record ImportResult(int annotationCount, int labelCount, String mode) {}

    @Transactional
    public ImportResult importAnnotations(Long slideId, InputStream inputStream,
                                          String format, String mode) throws Exception {
        return importAnnotations(slideId, inputStream, null, format, mode);
    }

    @Transactional
    public ImportResult importAnnotations(Long slideId, InputStream inputStream,
                                          InputStream classesStream,
                                          String format, String mode) throws Exception {
        Slide slide = slideMapper.selectById(slideId);
        if (slide == null) throw new IllegalArgumentException("Slide not found: " + slideId);

        ImportResult result = switch (format.toUpperCase()) {
            case "CUSTOM_JSON" -> importCustomJson(slideId, objectMapper.readTree(inputStream), mode);
            case "COCO"        -> importCoco(slideId, slide, objectMapper.readTree(inputStream), mode);
            case "YOLO"        -> importYolo(slideId, slide, inputStream, classesStream, mode);
            case "VOC"         -> importVoc(slideId, inputStream, mode);
            case "GEOJSON"     -> importGeoJson(slideId, objectMapper.readTree(inputStream), mode);
            default -> throw new IllegalArgumentException("Unsupported import format: " + format);
        };

        auditLogService.log("IMPORT", slideId, Map.of(
                "format", format,
                "mode", mode,
                "annotations", result.annotationCount(),
                "labels", result.labelCount()));

        return result;
    }

    // ── Custom JSON ───────────────────────────────────────────────────────────

    private ImportResult importCustomJson(Long slideId, JsonNode root, String mode) throws Exception {
        JsonNode labelsNode = root.path("labels");
        JsonNode annsNode   = root.path("annotations");

        Map<String, Label> existingByName = loadExistingLabelsByName(slideId);
        int maxClassIndex = maxClassIndex(existingByName.values());

        // Map file-local label IDs → DB Label entities
        Map<Long, Label> labelIdMap = new HashMap<>();
        int newLabelCount = 0;

        for (JsonNode labelNode : labelsNode) {
            long fileId    = labelNode.path("id").asLong();
            String name    = labelNode.path("name").asText();
            String color   = labelNode.path("color").asText("");
            int classIndex = labelNode.path("classIndex").asInt(0);

            Label label = existingByName.get(name);
            if (label == null) {
                label = new Label();
                label.setSlideId(slideId);
                label.setName(name);
                label.setColor(color.isBlank() ? PALETTE[newLabelCount % PALETTE.length] : color);
                label.setClassIndex(classIndex > 0 ? classIndex : ++maxClassIndex);
                labelMapper.insert(label);
                existingByName.put(name, label);
                newLabelCount++;
            }
            labelIdMap.put(fileId, label);
        }

        List<Annotation> importedAnns = new ArrayList<>();
        for (JsonNode annNode : annsNode) {
            long fileLabelId = annNode.path("labelId").asLong(-1);
            Label label = labelIdMap.get(fileLabelId);
            if (label == null) continue;

            Annotation ann = new Annotation();
            ann.setSlideId(slideId);
            ann.setLabelId(label.getId());
            ann.setAnnoType(annNode.path("annoType").asText("polygon"));
            ann.setCoordinates(annNode.path("coordinates").asText("[]"));
            importedAnns.add(ann);
        }

        doSave(slideId, importedAnns, mode);
        return new ImportResult(importedAnns.size(), newLabelCount, mode);
    }

    // ── COCO JSON ─────────────────────────────────────────────────────────────

    private ImportResult importCoco(Long slideId, Slide slide, JsonNode root, String mode) throws Exception {
        JsonNode images     = root.path("images");
        JsonNode annsNode   = root.path("annotations");
        JsonNode categories = root.path("categories");

        // Determine target image_id: match by file_name, or use first image
        long targetImageId = -1;
        if (images.size() == 1) {
            targetImageId = images.get(0).path("id").asLong();
        } else {
            for (JsonNode img : images) {
                if (slide.getFileName().equals(img.path("file_name").asText())) {
                    targetImageId = img.path("id").asLong();
                    break;
                }
            }
            if (targetImageId == -1 && images.size() > 0) {
                targetImageId = images.get(0).path("id").asLong();
                log.warn("COCO import: no image matched fileName '{}', using first image (id={})",
                        slide.getFileName(), targetImageId);
            }
        }
        if (targetImageId == -1) {
            throw new IllegalArgumentException("No images found in COCO JSON");
        }

        Map<String, Label> existingByName = loadExistingLabelsByName(slideId);
        int maxClassIndex = maxClassIndex(existingByName.values());

        // Build COCO category_id → DB Label
        Map<Long, Label> categoryMap = new HashMap<>();
        int newLabelCount = 0;

        for (JsonNode cat : categories) {
            long catId  = cat.path("id").asLong();
            String name = cat.path("name").asText("unknown");

            Label label = existingByName.get(name);
            if (label == null) {
                label = new Label();
                label.setSlideId(slideId);
                label.setName(name);
                label.setColor(PALETTE[newLabelCount % PALETTE.length]);
                label.setClassIndex(++maxClassIndex);
                labelMapper.insert(label);
                existingByName.put(name, label);
                newLabelCount++;
            }
            categoryMap.put(catId, label);
        }

        List<Annotation> importedAnns = new ArrayList<>();
        final long tid = targetImageId;

        for (JsonNode annNode : annsNode) {
            if (annNode.path("image_id").asLong() != tid) continue;

            long catId = annNode.path("category_id").asLong(-1);
            Label label = categoryMap.get(catId);
            if (label == null) continue;

            String annoType;
            String coordinates;

            JsonNode segNode  = annNode.path("segmentation");
            JsonNode bboxNode = annNode.path("bbox");

            if (!segNode.isMissingNode() && segNode.isArray() && segNode.size() > 0
                    && segNode.get(0).isArray()) {
                // COCO flat polygon [x1,y1,x2,y2,...] → [[x1,y1],[x2,y2],...]
                annoType = "polygon";
                JsonNode flatPoly = segNode.get(0);
                List<List<Double>> pts = new ArrayList<>();
                for (int i = 0; i + 1 < flatPoly.size(); i += 2) {
                    pts.add(List.of(flatPoly.get(i).asDouble(), flatPoly.get(i + 1).asDouble()));
                }
                coordinates = objectMapper.writeValueAsString(pts);
            } else if (!bboxNode.isMissingNode() && bboxNode.isArray() && bboxNode.size() == 4) {
                // COCO bbox [x,y,w,h] → rect [[x1,y1],[x2,y2]]
                annoType = "rect";
                double x = bboxNode.get(0).asDouble();
                double y = bboxNode.get(1).asDouble();
                double w = bboxNode.get(2).asDouble();
                double h = bboxNode.get(3).asDouble();
                coordinates = objectMapper.writeValueAsString(
                        List.of(List.of(x, y), List.of(x + w, y + h)));
            } else {
                continue; // no usable geometry
            }

            Annotation ann = new Annotation();
            ann.setSlideId(slideId);
            ann.setLabelId(label.getId());
            ann.setAnnoType(annoType);
            ann.setCoordinates(coordinates);
            importedAnns.add(ann);
        }

        doSave(slideId, importedAnns, mode);
        return new ImportResult(importedAnns.size(), newLabelCount, mode);
    }

    // ── YOLO TXT ──────────────────────────────────────────────────────────────

    private ImportResult importYolo(Long slideId, Slide slide,
                                    InputStream annotationsStream,
                                    InputStream classesStream,
                                    String mode) throws Exception {
        if (classesStream == null) {
            throw new IllegalArgumentException(messageUtil.msg("error.import.yoloNeedsClasses"));
        }
        int slideWidth  = slide.getWidth()  != null ? slide.getWidth()  : 0;
        int slideHeight = slide.getHeight() != null ? slide.getHeight() : 0;
        if (slideWidth == 0 || slideHeight == 0) {
            throw new IllegalArgumentException(messageUtil.msg("error.import.yoloSizeUnknown"));
        }

        // Read class names (one per line)
        List<String> classNames = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(classesStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.strip();
                if (!line.isEmpty()) classNames.add(line);
            }
        }

        // Build or reuse Labels
        Map<String, Label> existingByName = loadExistingLabelsByName(slideId);
        int maxClassIndex = maxClassIndex(existingByName.values());
        Map<Integer, Label> indexToLabel = new HashMap<>();
        int newLabelCount = 0;

        for (int i = 0; i < classNames.size(); i++) {
            String name = classNames.get(i);
            Label label = existingByName.get(name);
            if (label == null) {
                label = new Label();
                label.setSlideId(slideId);
                label.setName(name);
                label.setColor(PALETTE[newLabelCount % PALETTE.length]);
                label.setClassIndex(++maxClassIndex);
                labelMapper.insert(label);
                existingByName.put(name, label);
                newLabelCount++;
            }
            indexToLabel.put(i, label);
        }

        // Parse annotation lines: <class_id> <cx_norm> <cy_norm> <w_norm> <h_norm>
        List<Annotation> importedAnns = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(annotationsStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\\s+");
                if (parts.length < 5) continue;

                int classId = Integer.parseInt(parts[0]);
                double cxNorm = Double.parseDouble(parts[1]);
                double cyNorm = Double.parseDouble(parts[2]);
                double wNorm  = Double.parseDouble(parts[3]);
                double hNorm  = Double.parseDouble(parts[4]);

                Label label = indexToLabel.get(classId);
                if (label == null) continue;

                double cx = cxNorm * slideWidth;
                double cy = cyNorm * slideHeight;
                double w  = wNorm  * slideWidth;
                double h  = hNorm  * slideHeight;
                double x1 = cx - w / 2.0;
                double y1 = cy - h / 2.0;
                double x2 = cx + w / 2.0;
                double y2 = cy + h / 2.0;

                String coordinates = objectMapper.writeValueAsString(
                        List.of(List.of(x1, y1), List.of(x2, y2)));

                Annotation ann = new Annotation();
                ann.setSlideId(slideId);
                ann.setLabelId(label.getId());
                ann.setAnnoType("rect");
                ann.setCoordinates(coordinates);
                importedAnns.add(ann);
            }
        }

        doSave(slideId, importedAnns, mode);
        return new ImportResult(importedAnns.size(), newLabelCount, mode);
    }

    // ── Pascal VOC XML ────────────────────────────────────────────────────────

    private ImportResult importVoc(Long slideId, InputStream inputStream, String mode) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().parse(inputStream);
        doc.getDocumentElement().normalize();

        Map<String, Label> existingByName = loadExistingLabelsByName(slideId);
        int maxClassIndex = maxClassIndex(existingByName.values());
        int newLabelCount = 0;

        List<Annotation> importedAnns = new ArrayList<>();
        NodeList objects = doc.getElementsByTagName("object");

        for (int i = 0; i < objects.getLength(); i++) {
            Element obj = (Element) objects.item(i);

            String name = textContent(obj, "name");
            if (name.isBlank()) continue;

            NodeList bndboxList = obj.getElementsByTagName("bndbox");
            if (bndboxList.getLength() == 0) continue;
            Element bndbox = (Element) bndboxList.item(0);

            double xmin = doubleContent(bndbox, "xmin");
            double ymin = doubleContent(bndbox, "ymin");
            double xmax = doubleContent(bndbox, "xmax");
            double ymax = doubleContent(bndbox, "ymax");

            Label label = existingByName.get(name);
            if (label == null) {
                label = new Label();
                label.setSlideId(slideId);
                label.setName(name);
                label.setColor(PALETTE[newLabelCount % PALETTE.length]);
                label.setClassIndex(++maxClassIndex);
                labelMapper.insert(label);
                existingByName.put(name, label);
                newLabelCount++;
            }

            Annotation ann = new Annotation();
            ann.setSlideId(slideId);
            ann.setLabelId(label.getId());
            ann.setAnnoType("rect");
            ann.setCoordinates(objectMapper.writeValueAsString(
                    List.of(List.of(xmin, ymin), List.of(xmax, ymax))));
            importedAnns.add(ann);
        }

        doSave(slideId, importedAnns, mode);
        return new ImportResult(importedAnns.size(), newLabelCount, mode);
    }

    // ── GeoJSON ───────────────────────────────────────────────────────────────

    private ImportResult importGeoJson(Long slideId, JsonNode root, String mode) throws Exception {
        JsonNode features = root.path("features");
        if (!features.isArray()) {
            throw new IllegalArgumentException(messageUtil.msg("error.import.geojsonFormat"));
        }

        Map<String, Label> existingByName = loadExistingLabelsByName(slideId);
        int maxClassIndex = maxClassIndex(existingByName.values());
        int newLabelCount = 0;

        List<Annotation> importedAnns = new ArrayList<>();

        for (JsonNode feature : features) {
            JsonNode geometry   = feature.path("geometry");
            JsonNode properties = feature.path("properties");
            if (geometry.isMissingNode() || geometry.isNull()) continue;

            String  geoType = geometry.path("type").asText("");
            JsonNode coords  = geometry.path("coordinates");

            // Resolve label name from common property keys
            String labelName = null;
            for (String key : new String[]{"label", "name", "category", "class"}) {
                JsonNode val = properties.path(key);
                if (!val.isMissingNode() && !val.isNull() && !val.asText().isBlank()) {
                    labelName = val.asText().strip();
                    break;
                }
            }
            if (labelName == null || labelName.isBlank()) labelName = "default";

            Label label = existingByName.get(labelName);
            if (label == null) {
                label = new Label();
                label.setSlideId(slideId);
                label.setName(labelName);
                label.setColor(PALETTE[newLabelCount % PALETTE.length]);
                label.setClassIndex(++maxClassIndex);
                labelMapper.insert(label);
                existingByName.put(labelName, label);
                newLabelCount++;
            }

            importedAnns.addAll(buildGeoJsonAnnotations(slideId, label, geoType, coords));
        }

        doSave(slideId, importedAnns, mode);
        return new ImportResult(importedAnns.size(), newLabelCount, mode);
    }

    private List<Annotation> buildGeoJsonAnnotations(Long slideId, Label label,
                                                     String geoType, JsonNode coords) throws Exception {
        List<Annotation> result = new ArrayList<>();
        switch (geoType) {
            case "Polygon" -> {
                // coords[0] = exterior ring: [[x,y], ...]
                JsonNode ring = coords.get(0);
                if (ring == null || ring.size() < 3) break;
                List<List<Double>> pts = new ArrayList<>();
                for (JsonNode pt : ring) {
                    pts.add(List.of(pt.get(0).asDouble(), pt.get(1).asDouble()));
                }
                Annotation ann = new Annotation();
                ann.setSlideId(slideId);
                ann.setLabelId(label.getId());
                ann.setAnnoType("polygon");
                ann.setCoordinates(objectMapper.writeValueAsString(pts));
                result.add(ann);
            }
            case "MultiPolygon" -> {
                // coords: [ [ ring: [[x,y]] ], ... ]
                for (JsonNode polygon : coords) {
                    JsonNode ring = polygon.get(0);
                    if (ring == null || ring.size() < 3) continue;
                    List<List<Double>> pts = new ArrayList<>();
                    for (JsonNode pt : ring) {
                        pts.add(List.of(pt.get(0).asDouble(), pt.get(1).asDouble()));
                    }
                    Annotation ann = new Annotation();
                    ann.setSlideId(slideId);
                    ann.setLabelId(label.getId());
                    ann.setAnnoType("polygon");
                    ann.setCoordinates(objectMapper.writeValueAsString(pts));
                    result.add(ann);
                }
            }
            case "Point" -> {
                double x = coords.get(0).asDouble();
                double y = coords.get(1).asDouble();
                Annotation ann = new Annotation();
                ann.setSlideId(slideId);
                ann.setLabelId(label.getId());
                ann.setAnnoType("point");
                ann.setCoordinates(objectMapper.writeValueAsString(List.of(List.of(x, y))));
                result.add(ann);
            }
            default -> log.debug("GeoJSON: skipping unsupported geometry type '{}'", geoType);
        }
        return result;
    }

    private String textContent(Element parent, String tag) {
        NodeList nl = parent.getElementsByTagName(tag);
        return nl.getLength() > 0 ? nl.item(0).getTextContent().strip() : "";
    }

    private double doubleContent(Element parent, String tag) {
        String v = textContent(parent, tag);
        return v.isBlank() ? 0.0 : Double.parseDouble(v);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, Label> loadExistingLabelsByName(Long slideId) {
        List<Label> existing = labelMapper.selectList(
                new LambdaQueryWrapper<Label>().eq(Label::getSlideId, slideId));
        Map<String, Label> map = new LinkedHashMap<>();
        existing.forEach(l -> map.put(l.getName(), l));
        return map;
    }

    private int maxClassIndex(Collection<Label> labels) {
        return labels.stream()
                .mapToInt(l -> l.getClassIndex() != null ? l.getClassIndex() : 0)
                .max().orElse(0);
    }

    private void doSave(Long slideId, List<Annotation> importedAnns, String mode) {
        if ("REPLACE".equalsIgnoreCase(mode)) {
            annotationMapper.delete(new LambdaQueryWrapper<Annotation>()
                    .eq(Annotation::getSlideId, slideId));
        }
        for (Annotation ann : importedAnns) {
            ann.setId(null);
            annotationMapper.insert(ann);
        }

        // Update slide annoStatus
        long total;
        if ("REPLACE".equalsIgnoreCase(mode)) {
            total = importedAnns.size();
        } else {
            total = annotationMapper.selectCount(
                    new LambdaQueryWrapper<Annotation>().eq(Annotation::getSlideId, slideId));
        }
        Slide slide = slideMapper.selectById(slideId);
        if (slide != null) {
            slide.setAnnoStatus(total == 0 ? "NOT_STARTED" : "IN_PROGRESS");
            slideMapper.updateById(slide);
        }
    }
}
