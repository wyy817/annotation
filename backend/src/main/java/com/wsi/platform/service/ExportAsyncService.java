package com.wsi.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wsi.platform.config.StorageProperties;
import com.wsi.platform.entity.Annotation;
import com.wsi.platform.entity.ExportTask;
import com.wsi.platform.entity.Label;
import com.wsi.platform.entity.Slide;
import com.wsi.platform.enums.ExportFormat;
import com.wsi.platform.mapper.AnnotationMapper;
import com.wsi.platform.mapper.ExportTaskMapper;
import com.wsi.platform.mapper.LabelMapper;
import com.wsi.platform.mapper.SlideMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

/**
 * Async export processor — kept in a separate bean so that @Async proxying works.
 * Supports multiple export formats dispatched via ExportFormat enum.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportAsyncService {

    private final SlideMapper slideMapper;
    private final LabelMapper labelMapper;
    private final AnnotationMapper annotationMapper;
    private final ExportTaskMapper exportTaskMapper;
    private final PythonServiceClient pythonClient;
    private final StorageProperties props;
    private final ObjectMapper objectMapper;

    @Autowired
    @Qualifier("maskGenerationExecutor")
    private ExecutorService maskExecutor;

    // ── Data bundle for a single slide ─────────────────────────────────────────
    record SlideBundle(Slide slide, List<Label> labels, List<Annotation> annotations,
                       Map<Long, Label> labelMap, List<Map<String, Object>> annDicts) {}

    // ── Single-slide export ───────────────────────────────────────────────────

    @Async
    public void doExport(Long taskId) {
        ExportTask task = exportTaskMapper.selectById(taskId);
        if (task == null) return;

        task.setStatus("PROCESSING");
        exportTaskMapper.updateById(task);

        try {
            Slide slide = slideMapper.selectById(task.getSlideId());
            List<Label> labels = labelMapper.selectList(
                    new LambdaQueryWrapper<Label>().eq(Label::getSlideId, slide.getId()));
            List<Annotation> annotations = annotationMapper.selectList(
                    new LambdaQueryWrapper<Annotation>().eq(Annotation::getSlideId, slide.getId()));

            Map<Long, Label> labelMap = new HashMap<>();
            labels.forEach(l -> labelMap.put(l.getId(), l));
            List<Map<String, Object>> annDicts = buildAnnDicts(annotations, labelMap);
            SlideBundle bundle = new SlideBundle(slide, labels, annotations, labelMap, annDicts);

            ExportFormat format = ExportFormat.fromString(task.getFormat());
            int patchSize = task.getPatchSize() != null ? task.getPatchSize() : 256;
            String maskType = task.getMaskType() != null ? task.getMaskType() : "SEMANTIC";
            int maskScale = task.getMaskScale() != null ? task.getMaskScale() : 1;

            // ── Generate semantic mask (needed for COCO / MASK_ONLY) ───────────
            String maskPath = null;
            if (format == ExportFormat.COCO || format == ExportFormat.MASK_ONLY) {
                try {
                    PythonServiceClient.GenerateMaskResponse r = pythonClient.generateMask(
                            slide.getId(), slide.getWidth(), slide.getHeight(), annDicts,
                            maskType, maskScale);
                    maskPath = r.getMaskPath();
                } catch (Exception e) {
                    log.warn("generate-mask skipped for slide {}: {}", slide.getId(), e.getMessage());
                }
            }

            // ── Extract patches (PATCHES format) ──────────────────────────────
            String patchDir = null;
            if (format == ExportFormat.PATCHES) {
                try {
                    PythonServiceClient.ExtractPatchesResponse r = pythonClient.extractPatches(
                            slide.getId(), slide.getOriginalPath(), annDicts, patchSize);
                    patchDir = r.getPatchDir();
                } catch (Exception e) {
                    log.warn("extract-patches skipped for slide {}: {}", slide.getId(), e.getMessage());
                }
            }

            // ── Package ZIP ────────────────────────────────────────────────────
            Path exportDir = Paths.get(props.getStorage().getExportDir())
                    .resolve(String.valueOf(slide.getId())).toAbsolutePath();
            Files.createDirectories(exportDir);
            Path zipPath = exportDir.resolve("export_" + taskId + ".zip");

            try (ZipOutputStream zos = new ZipOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(zipPath)))) {
                packageSingle(zos, format, bundle, maskPath, patchDir, patchSize);
            }

            task.setStatus("DONE");
            task.setZipPath(zipPath.toString());
            task.setTotal(annotations.size());
            exportTaskMapper.updateById(task);
            log.info("Export task {} ({}) DONE → {}", taskId, format, zipPath);

        } catch (Exception e) {
            log.error("Export task {} FAILED: {}", taskId, e.getMessage(), e);
            task.setStatus("ERROR");
            exportTaskMapper.updateById(task);
        }
    }

    // ── Batch export (one combined ZIP) ──────────────────────────────────────

    @Async
    public void doBatchExport(Long taskId, List<Long> slideIds) {
        ExportTask task = exportTaskMapper.selectById(taskId);
        if (task == null) return;

        task.setStatus("PROCESSING");
        exportTaskMapper.updateById(task);

        try {
            ExportFormat format = ExportFormat.fromString(task.getFormat());

            Path exportDir = Paths.get(props.getStorage().getExportDir())
                    .resolve("batch_" + taskId).toAbsolutePath();
            Files.createDirectories(exportDir);
            Path zipPath = exportDir.resolve("batch_export_" + taskId + ".zip");

            // ── Load slide data ────────────────────────────────────────────────
            List<SlideBundle> bundles = new ArrayList<>();
            for (Long slideId : slideIds) {
                Slide slide = slideMapper.selectById(slideId);
                if (slide == null) {
                    log.warn("Batch export task {}: slide {} not found, skipping", taskId, slideId);
                    continue;
                }
                List<Label> labels = labelMapper.selectList(
                        new LambdaQueryWrapper<Label>().eq(Label::getSlideId, slide.getId()));
                List<Annotation> annotations = annotationMapper.selectList(
                        new LambdaQueryWrapper<Annotation>().eq(Annotation::getSlideId, slide.getId()));
                Map<Long, Label> labelMap = new HashMap<>();
                labels.forEach(l -> labelMap.put(l.getId(), l));
                bundles.add(new SlideBundle(slide, labels, annotations, labelMap,
                        buildAnnDicts(annotations, labelMap)));
            }

            int patchSize = task.getPatchSize() != null ? task.getPatchSize() : 256;
            String maskType = task.getMaskType() != null ? task.getMaskType() : "SEMANTIC";
            int maskScale = task.getMaskScale() != null ? task.getMaskScale() : 1;

            // ── Generate masks in parallel (COCO / MASK_ONLY) ──────────────────
            Map<Long, String> maskPaths = new ConcurrentHashMap<>();
            if (format == ExportFormat.COCO || format == ExportFormat.MASK_ONLY) {
                List<CompletableFuture<Void>> maskFutures = bundles.stream().map(b ->
                    CompletableFuture.runAsync(() -> {
                        try {
                            PythonServiceClient.GenerateMaskResponse r = pythonClient.generateMask(
                                    b.slide().getId(), b.slide().getWidth(), b.slide().getHeight(), b.annDicts(),
                                    maskType, maskScale);
                            maskPaths.put(b.slide().getId(), r.getMaskPath());
                        } catch (Exception e) {
                            log.warn("generate-mask skipped for slide {} in batch {}: {}",
                                    b.slide().getId(), taskId, e.getMessage());
                        }
                    }, maskExecutor)
                ).toList();
                CompletableFuture.allOf(maskFutures.toArray(new CompletableFuture[0])).join();
            }

            // ── Extract patches in parallel (PATCHES) ─────────────────────────
            Map<Long, String> patchDirs = new ConcurrentHashMap<>();
            if (format == ExportFormat.PATCHES) {
                List<CompletableFuture<Void>> patchFutures = bundles.stream().map(b ->
                    CompletableFuture.runAsync(() -> {
                        try {
                            PythonServiceClient.ExtractPatchesResponse r = pythonClient.extractPatches(
                                    b.slide().getId(), b.slide().getOriginalPath(), b.annDicts(), patchSize);
                            patchDirs.put(b.slide().getId(), r.getPatchDir());
                        } catch (Exception e) {
                            log.warn("extract-patches skipped for slide {} in batch {}: {}",
                                    b.slide().getId(), taskId, e.getMessage());
                        }
                    }, maskExecutor)
                ).toList();
                CompletableFuture.allOf(patchFutures.toArray(new CompletableFuture[0])).join();
            }

            // ── Package ZIP ────────────────────────────────────────────────────
            int totalAnnotations = 0;
            int processedSlides = 0;

            try (ZipOutputStream zos = new ZipOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(zipPath)))) {

                switch (format) {
                    case COCO -> {
                        // Standard COCO: one instances.json merging all slides
                        for (SlideBundle b : bundles) {
                            Long sid = b.slide().getId();
                            if (b.slide().getThumbnailPath() != null)
                                addFile(zos, Paths.get(b.slide().getThumbnailPath()), "images/" + sid + ".jpg");
                            String mp = maskPaths.get(sid);
                            if (mp != null)
                                addFile(zos, Paths.get(mp), "masks/" + sid + ".png");
                            totalAnnotations += b.annotations().size();
                            processedSlides++;
                        }
                        addString(zos, buildStandardCocoJson(bundles), "instances.json");
                        addString(zos, buildBatchReport(format, slideIds.size(), processedSlides, totalAnnotations), "report.txt");
                    }
                    case MASK_ONLY -> {
                        for (SlideBundle b : bundles) {
                            Long sid = b.slide().getId();
                            String mp = maskPaths.get(sid);
                            if (mp != null)
                                addFile(zos, Paths.get(mp), "masks/" + sid + ".png");
                            totalAnnotations += b.annotations().size();
                            processedSlides++;
                        }
                        addString(zos, buildBatchReport(format, slideIds.size(), processedSlides, totalAnnotations), "report.txt");
                    }
                    case YOLO -> {
                        for (SlideBundle b : bundles) {
                            Long sid = b.slide().getId();
                            // image
                            if (b.slide().getThumbnailPath() != null)
                                addFile(zos, Paths.get(b.slide().getThumbnailPath()), "images/" + sid + ".jpg");
                            // label txt
                            String baseName = String.valueOf(sid);
                            addString(zos, buildYoloLabel(b), "labels/" + baseName + ".txt");
                            totalAnnotations += b.annotations().size();
                            processedSlides++;
                        }
                        List<String> classNames = buildSortedClassNames(bundles);
                        addString(zos, String.join("\n", classNames) + "\n", "classes.txt");
                        addString(zos, buildDataYaml(classNames), "data.yaml");
                        addString(zos, buildBatchReport(format, slideIds.size(), processedSlides, totalAnnotations), "report.txt");
                    }
                    case VOC -> {
                        for (SlideBundle b : bundles) {
                            Long sid = b.slide().getId();
                            if (b.slide().getThumbnailPath() != null)
                                addFile(zos, Paths.get(b.slide().getThumbnailPath()), "JPEGImages/" + sid + ".jpg");
                            addString(zos, buildVocXml(b), "Annotations/" + sid + ".xml");
                            totalAnnotations += b.annotations().size();
                            processedSlides++;
                        }
                        addString(zos, buildBatchReport(format, slideIds.size(), processedSlides, totalAnnotations), "report.txt");
                    }
                    case GEOJSON -> {
                        for (SlideBundle b : bundles) {
                            Long sid = b.slide().getId();
                            addString(zos, buildGeoJson(b), sid + ".geojson");
                            totalAnnotations += b.annotations().size();
                            processedSlides++;
                        }
                        addString(zos, buildBatchReport(format, slideIds.size(), processedSlides, totalAnnotations), "report.txt");
                    }
                    case PATCHES -> {
                        // Merge all slide patches into one dataset
                        List<String[]> allCsvRows = new ArrayList<>(); // label, file
                        for (SlideBundle b : bundles) {
                            Long sid = b.slide().getId();
                            String pd = patchDirs.get(sid);
                            if (pd != null) {
                                List<String[]> rows = addPatchFiles(zos, pd, "patches/");
                                allCsvRows.addAll(rows);
                            }
                            totalAnnotations += b.annotations().size();
                            processedSlides++;
                        }
                        addString(zos, buildLabelsCsv(allCsvRows), "labels.csv");
                        addString(zos, buildBatchReport(format, slideIds.size(), processedSlides, totalAnnotations), "report.txt");
                    }
                }
            }

            task.setStatus("DONE");
            task.setZipPath(zipPath.toString());
            task.setTotal(totalAnnotations);
            exportTaskMapper.updateById(task);
            log.info("Batch export task {} ({}) DONE → {} ({} slides)", taskId, format, zipPath, processedSlides);

        } catch (Exception e) {
            log.error("Batch export task {} FAILED: {}", taskId, e.getMessage(), e);
            task.setStatus("ERROR");
            exportTaskMapper.updateById(task);
        }
    }

    // ── Format-specific packaging (single slide) ─────────────────────────────

    private void packageSingle(ZipOutputStream zos, ExportFormat format,
                               SlideBundle b, String maskPath,
                               String patchDir, int patchSize) throws IOException {
        switch (format) {
            case COCO -> {
                if (b.slide().getThumbnailPath() != null)
                    addFile(zos, Paths.get(b.slide().getThumbnailPath()), "images/" + b.slide().getFileName());
                if (maskPath != null)
                    addFile(zos, Paths.get(maskPath), "masks/" + b.slide().getId() + "_mask.png");
                addString(zos, buildStandardCocoJson(List.of(b)), "instances.json");
                addString(zos, buildSingleReport(format, b), "report.txt");
            }
            case MASK_ONLY -> {
                if (maskPath != null)
                    addFile(zos, Paths.get(maskPath), "mask.png");
                addString(zos, buildSingleReport(format, b), "report.txt");
            }
            case YOLO -> {
                if (b.slide().getThumbnailPath() != null)
                    addFile(zos, Paths.get(b.slide().getThumbnailPath()), "images/" + b.slide().getFileName());
                String labelTxt = buildYoloLabel(b);
                String baseName = stripExtension(b.slide().getFileName());
                addString(zos, labelTxt, "labels/" + baseName + ".txt");
                List<String> classNames = buildSortedClassNames(List.of(b));
                addString(zos, String.join("\n", classNames) + "\n", "classes.txt");
                addString(zos, buildDataYaml(classNames), "data.yaml");
                addString(zos, buildSingleReport(format, b), "report.txt");
            }
            case VOC -> {
                if (b.slide().getThumbnailPath() != null)
                    addFile(zos, Paths.get(b.slide().getThumbnailPath()), "JPEGImages/" + b.slide().getFileName());
                String baseName = stripExtension(b.slide().getFileName());
                addString(zos, buildVocXml(b), "Annotations/" + baseName + ".xml");
                addString(zos, buildSingleReport(format, b), "report.txt");
            }
            case GEOJSON -> {
                addString(zos, buildGeoJson(b), stripExtension(b.slide().getFileName()) + ".geojson");
                addString(zos, buildSingleReport(format, b), "report.txt");
            }
            case PATCHES -> {
                packagePatches(zos, patchDir, b, patchSize);
                addString(zos, buildSingleReport(format, b), "report.txt");
            }
        }
    }

    // ── Standard COCO JSON builder ───────────────────────────────────────────

    /**
     * Build a fully standard COCO-format JSON:
     * {
     *   "images": [ { "id", "file_name", "width", "height" } ],
     *   "annotations": [ { "id", "image_id", "category_id", "segmentation", "bbox", "area", "iscrowd" } ],
     *   "categories": [ { "id", "name", "supercategory" } ]
     * }
     */
    private String buildStandardCocoJson(List<SlideBundle> bundles) {
        try {
            // ── Collect unique categories across all slides ─────────────────
            // Key: classIndex → category entry (deduplicated across slides)
            Map<Integer, Map<String, Object>> categoryMap = new LinkedHashMap<>();
            for (SlideBundle b : bundles) {
                for (Label l : b.labels()) {
                    categoryMap.computeIfAbsent(l.getClassIndex(), ci -> {
                        Map<String, Object> cat = new LinkedHashMap<>();
                        cat.put("id", ci);
                        cat.put("name", l.getName());
                        cat.put("supercategory", "none");
                        return cat;
                    });
                }
            }

            List<Map<String, Object>> images = new ArrayList<>();
            List<Map<String, Object>> cocoAnnotations = new ArrayList<>();
            int annIdCounter = 1;

            for (SlideBundle b : bundles) {
                Slide slide = b.slide();

                // image entry
                Map<String, Object> img = new LinkedHashMap<>();
                img.put("id", slide.getId().intValue());
                img.put("file_name", slide.getFileName());
                img.put("width", slide.getWidth());
                img.put("height", slide.getHeight());
                images.add(img);

                // annotation entries
                for (Annotation ann : b.annotations()) {
                    Label label = b.labelMap().get(ann.getLabelId());
                    int categoryId = label != null ? label.getClassIndex() : 1;

                    Map<String, Object> cocoAnn = new LinkedHashMap<>();
                    cocoAnn.put("id", annIdCounter++);
                    cocoAnn.put("image_id", slide.getId().intValue());
                    cocoAnn.put("category_id", categoryId);

                    Object coords = parseJson(ann.getCoordinates());
                    String annoType = ann.getAnnoType();

                    if ("rect".equals(annoType)) {
                        // coords = [[x1,y1],[x2,y2]]
                        double[] bbox = rectToBbox(coords);
                        cocoAnn.put("bbox", bbox);
                        cocoAnn.put("area", bbox[2] * bbox[3]);
                        // segmentation: rectangle as polygon
                        cocoAnn.put("segmentation", List.of(rectToPolygon(bbox)));
                    } else if ("polygon".equals(annoType)) {
                        // coords = [[x1,y1],[x2,y2],...]
                        List<Double> flatPoly = flattenPolygon(coords);
                        double[] bbox = polygonToBbox(flatPoly);
                        cocoAnn.put("bbox", bbox);
                        cocoAnn.put("area", polygonArea(flatPoly));
                        cocoAnn.put("segmentation", List.of(flatPoly));
                    } else {
                        // brush — store as-is in segmentation, no standard bbox
                        cocoAnn.put("bbox", new double[]{0, 0, 0, 0});
                        cocoAnn.put("area", 0);
                        cocoAnn.put("segmentation", List.of());
                    }

                    cocoAnn.put("iscrowd", 0);
                    cocoAnnotations.add(cocoAnn);
                }
            }

            Map<String, Object> coco = new LinkedHashMap<>();
            coco.put("images", images);
            coco.put("annotations", cocoAnnotations);
            coco.put("categories", new ArrayList<>(categoryMap.values()));

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(coco);
        } catch (Exception e) {
            log.error("Failed to build COCO JSON: {}", e.getMessage(), e);
            return "{}";
        }
    }

    // ── Geometry helpers ─────────────────────────────────────────────────────

    /** rect coords [[x1,y1],[x2,y2]] → COCO bbox [x, y, width, height] */
    @SuppressWarnings("unchecked")
    private double[] rectToBbox(Object coords) {
        try {
            List<List<Number>> pts = (List<List<Number>>) coords;
            double x1 = pts.get(0).get(0).doubleValue();
            double y1 = pts.get(0).get(1).doubleValue();
            double x2 = pts.get(1).get(0).doubleValue();
            double y2 = pts.get(1).get(1).doubleValue();
            double x = Math.min(x1, x2), y = Math.min(y1, y2);
            return new double[]{x, y, Math.abs(x2 - x1), Math.abs(y2 - y1)};
        } catch (Exception e) {
            return new double[]{0, 0, 0, 0};
        }
    }

    /** COCO bbox → polygon [x1,y1,x2,y1,x2,y2,x1,y2] */
    private List<Double> rectToPolygon(double[] bbox) {
        double x = bbox[0], y = bbox[1], w = bbox[2], h = bbox[3];
        return List.of(x, y, x + w, y, x + w, y + h, x, y + h);
    }

    /** polygon coords [[x1,y1],[x2,y2],...] → flat [x1,y1,x2,y2,...] */
    @SuppressWarnings("unchecked")
    private List<Double> flattenPolygon(Object coords) {
        try {
            List<List<Number>> pts = (List<List<Number>>) coords;
            List<Double> flat = new ArrayList<>(pts.size() * 2);
            for (List<Number> pt : pts) {
                flat.add(pt.get(0).doubleValue());
                flat.add(pt.get(1).doubleValue());
            }
            return flat;
        } catch (Exception e) {
            return List.of();
        }
    }

    /** flat polygon [x1,y1,x2,y2,...] → COCO bbox [x, y, w, h] */
    private double[] polygonToBbox(List<Double> flat) {
        if (flat.isEmpty()) return new double[]{0, 0, 0, 0};
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (int i = 0; i < flat.size(); i += 2) {
            double x = flat.get(i), y = flat.get(i + 1);
            minX = Math.min(minX, x); minY = Math.min(minY, y);
            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
        }
        return new double[]{minX, minY, maxX - minX, maxY - minY};
    }

    /** Shoelace formula for polygon area from flat coordinates */
    private double polygonArea(List<Double> flat) {
        if (flat.size() < 6) return 0; // need at least 3 points
        double area = 0;
        int n = flat.size() / 2;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            double xi = flat.get(i * 2), yi = flat.get(i * 2 + 1);
            double xj = flat.get(j * 2), yj = flat.get(j * 2 + 1);
            area += xi * yj - xj * yi;
        }
        return Math.abs(area) / 2.0;
    }

    // ── YOLO helpers ────────────────────────────────────────────────────────

    /**
     * Build YOLO label text for one slide.
     * rect   → class_id cx cy w h           (normalized, detection)
     * polygon → class_id x1 y1 x2 y2 ... xn yn (normalized, segmentation)
     */
    @SuppressWarnings("unchecked")
    private String buildYoloLabel(SlideBundle b) {
        int imgW = b.slide().getWidth();
        int imgH = b.slide().getHeight();
        StringBuilder sb = new StringBuilder();

        for (Annotation ann : b.annotations()) {
            Label label = b.labelMap().get(ann.getLabelId());
            int classId = label != null ? label.getClassIndex() : 0;
            Object coords = parseJson(ann.getCoordinates());
            String annoType = ann.getAnnoType();

            if ("rect".equals(annoType)) {
                try {
                    List<List<Number>> pts = (List<List<Number>>) coords;
                    double x1 = pts.get(0).get(0).doubleValue();
                    double y1 = pts.get(0).get(1).doubleValue();
                    double x2 = pts.get(1).get(0).doubleValue();
                    double y2 = pts.get(1).get(1).doubleValue();
                    double cx = (x1 + x2) / 2.0 / imgW;
                    double cy = (y1 + y2) / 2.0 / imgH;
                    double w = Math.abs(x2 - x1) / imgW;
                    double h = Math.abs(y2 - y1) / imgH;
                    sb.append(String.format("%d %.6f %.6f %.6f %.6f\n", classId, cx, cy, w, h));
                } catch (Exception ignored) {}
            } else if ("polygon".equals(annoType)) {
                try {
                    List<List<Number>> pts = (List<List<Number>>) coords;
                    sb.append(classId);
                    for (List<Number> pt : pts) {
                        double nx = pt.get(0).doubleValue() / imgW;
                        double ny = pt.get(1).doubleValue() / imgH;
                        sb.append(String.format(" %.6f %.6f", nx, ny));
                    }
                    sb.append("\n");
                } catch (Exception ignored) {}
            }
            // brush annotations are skipped — no standard YOLO representation
        }
        return sb.toString();
    }

    /** Collect sorted class names across all bundles (ordered by classIndex). */
    private List<String> buildSortedClassNames(List<SlideBundle> bundles) {
        Map<Integer, String> indexToName = new TreeMap<>();
        for (SlideBundle b : bundles) {
            for (Label l : b.labels()) {
                indexToName.putIfAbsent(l.getClassIndex(), l.getName());
            }
        }
        return new ArrayList<>(indexToName.values());
    }

    /** Build YOLO data.yaml content. */
    private String buildDataYaml(List<String> classNames) {
        StringBuilder sb = new StringBuilder();
        sb.append("# YOLO dataset configuration\n");
        sb.append("path: .\n");
        sb.append("train: images\n");
        sb.append("val: images\n\n");
        sb.append("nc: ").append(classNames.size()).append("\n");
        sb.append("names: [");
        for (int i = 0; i < classNames.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("'").append(classNames.get(i)).append("'");
        }
        sb.append("]\n");
        return sb.toString();
    }

    /** Remove file extension: "foo.svs" → "foo" */
    private String stripExtension(String fileName) {
        if (fileName == null) return "unknown";
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    // ── VOC XML builder ──────────────────────────────────────────────────────

    /**
     * Build Pascal VOC XML for one slide.
     * Structure: <annotation><folder/><filename/><size/><object*><name/><bndbox/></object></annotation>
     */
    @SuppressWarnings("unchecked")
    private String buildVocXml(SlideBundle b) {
        StringBuilder xml = new StringBuilder();
        xml.append("<annotation>\n");
        xml.append("  <folder>JPEGImages</folder>\n");
        xml.append("  <filename>").append(escapeXml(b.slide().getFileName())).append("</filename>\n");
        xml.append("  <size>\n");
        xml.append("    <width>").append(b.slide().getWidth()).append("</width>\n");
        xml.append("    <height>").append(b.slide().getHeight()).append("</height>\n");
        xml.append("    <depth>3</depth>\n");
        xml.append("  </size>\n");

        for (Annotation ann : b.annotations()) {
            Label label = b.labelMap().get(ann.getLabelId());
            String name = label != null ? label.getName() : "unknown";
            Object coords = parseJson(ann.getCoordinates());
            String annoType = ann.getAnnoType();

            double xmin, ymin, xmax, ymax;
            if ("rect".equals(annoType)) {
                double[] bbox = rectToBbox(coords);
                xmin = bbox[0]; ymin = bbox[1];
                xmax = bbox[0] + bbox[2]; ymax = bbox[1] + bbox[3];
            } else if ("polygon".equals(annoType)) {
                List<Double> flat = flattenPolygon(coords);
                double[] bbox = polygonToBbox(flat);
                xmin = bbox[0]; ymin = bbox[1];
                xmax = bbox[0] + bbox[2]; ymax = bbox[1] + bbox[3];
            } else {
                continue; // brush annotations have no bounding box
            }

            // Clamp to image bounds
            xmin = Math.max(0, Math.round(xmin));
            ymin = Math.max(0, Math.round(ymin));
            xmax = Math.min(b.slide().getWidth(), Math.round(xmax));
            ymax = Math.min(b.slide().getHeight(), Math.round(ymax));

            xml.append("  <object>\n");
            xml.append("    <name>").append(escapeXml(name)).append("</name>\n");
            xml.append("    <pose>Unspecified</pose>\n");
            xml.append("    <truncated>0</truncated>\n");
            xml.append("    <difficult>0</difficult>\n");
            xml.append("    <bndbox>\n");
            xml.append("      <xmin>").append((int) xmin).append("</xmin>\n");
            xml.append("      <ymin>").append((int) ymin).append("</ymin>\n");
            xml.append("      <xmax>").append((int) xmax).append("</xmax>\n");
            xml.append("      <ymax>").append((int) ymax).append("</ymax>\n");
            xml.append("    </bndbox>\n");
            xml.append("  </object>\n");
        }

        xml.append("</annotation>\n");
        return xml.toString();
    }

    private String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    // ── GeoJSON builder ─────────────────────────────────────────────────────

    /**
     * Build GeoJSON FeatureCollection for one slide.
     * Uses image pixel coordinates (not geographic CRS).
     * rect    → Polygon geometry (closed rectangle)
     * polygon → Polygon geometry (closed ring)
     * brush   → Point at (0,0) with raw coordinates in properties
     */
    @SuppressWarnings("unchecked")
    private String buildGeoJson(SlideBundle b) {
        try {
            Map<String, Object> fc = new LinkedHashMap<>();
            fc.put("type", "FeatureCollection");

            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("slide_id", b.slide().getId());
            meta.put("file_name", b.slide().getFileName());
            meta.put("width", b.slide().getWidth());
            meta.put("height", b.slide().getHeight());
            meta.put("crs", "image_pixels");
            fc.put("metadata", meta);

            List<Map<String, Object>> features = new ArrayList<>();

            for (Annotation ann : b.annotations()) {
                Label label = b.labelMap().get(ann.getLabelId());
                Object coords = parseJson(ann.getCoordinates());
                String annoType = ann.getAnnoType();

                Map<String, Object> feature = new LinkedHashMap<>();
                feature.put("type", "Feature");

                // properties
                Map<String, Object> props = new LinkedHashMap<>();
                props.put("annotation_id", ann.getId());
                props.put("label", label != null ? label.getName() : "unknown");
                props.put("class_index", label != null ? label.getClassIndex() : 0);
                props.put("anno_type", annoType);
                feature.put("properties", props);

                // geometry
                Map<String, Object> geometry = new LinkedHashMap<>();

                if ("rect".equals(annoType)) {
                    try {
                        List<List<Number>> pts = (List<List<Number>>) coords;
                        double x1 = pts.get(0).get(0).doubleValue();
                        double y1 = pts.get(0).get(1).doubleValue();
                        double x2 = pts.get(1).get(0).doubleValue();
                        double y2 = pts.get(1).get(1).doubleValue();
                        // GeoJSON Polygon: array of rings, each ring is array of [x,y] positions, first==last
                        List<List<double[]>> rings = List.of(List.of(
                                new double[]{x1, y1}, new double[]{x2, y1},
                                new double[]{x2, y2}, new double[]{x1, y2},
                                new double[]{x1, y1} // close ring
                        ));
                        geometry.put("type", "Polygon");
                        geometry.put("coordinates", rings);
                    } catch (Exception e) {
                        geometry.put("type", "Point");
                        geometry.put("coordinates", new double[]{0, 0});
                    }
                } else if ("polygon".equals(annoType)) {
                    try {
                        List<List<Number>> pts = (List<List<Number>>) coords;
                        List<double[]> ring = new ArrayList<>(pts.size() + 1);
                        for (List<Number> pt : pts) {
                            ring.add(new double[]{pt.get(0).doubleValue(), pt.get(1).doubleValue()});
                        }
                        // close ring if not already closed
                        if (!ring.isEmpty()) {
                            double[] first = ring.get(0);
                            double[] last = ring.get(ring.size() - 1);
                            if (first[0] != last[0] || first[1] != last[1]) {
                                ring.add(new double[]{first[0], first[1]});
                            }
                        }
                        geometry.put("type", "Polygon");
                        geometry.put("coordinates", List.of(ring));
                    } catch (Exception e) {
                        geometry.put("type", "Point");
                        geometry.put("coordinates", new double[]{0, 0});
                    }
                } else {
                    // brush — store as Point with raw data in properties
                    geometry.put("type", "Point");
                    geometry.put("coordinates", new double[]{0, 0});
                    props.put("brush_data", coords);
                }

                feature.put("geometry", geometry);
                features.add(feature);
            }

            fc.put("features", features);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(fc);
        } catch (Exception e) {
            log.error("Failed to build GeoJSON: {}", e.getMessage(), e);
            return "{}";
        }
    }

    // ── Patch helpers ────────────────────────────────────────────────────────

    /**
     * Package patch files from a single slide's patchDir into the ZIP.
     * Reorganizes flat files ({label}_patch_{nnnn}.png) into patches/{label}/patch_{nnnn}.png.
     * Returns CSV rows [label, relativePath] for labels.csv.
     */
    private void packagePatches(ZipOutputStream zos, String patchDir, SlideBundle b,
                                int patchSize) throws IOException {
        List<String[]> csvRows = new ArrayList<>();
        if (patchDir != null) {
            csvRows = addPatchFiles(zos, patchDir, "patches/");
        }
        addString(zos, buildLabelsCsv(csvRows), "labels.csv");
    }

    /**
     * Scan patchDir for PNG files, add them to ZIP under prefix/{label}/filename,
     * and return CSV rows [label, zipEntryPath].
     */
    private List<String[]> addPatchFiles(ZipOutputStream zos, String patchDir,
                                          String zipPrefix) throws IOException {
        List<String[]> rows = new ArrayList<>();
        Path dir = Paths.get(patchDir);
        if (!Files.isDirectory(dir)) return rows;

        try (var stream = Files.list(dir)) {
            List<Path> patchFiles = stream
                    .filter(p -> p.toString().toLowerCase().endsWith(".png"))
                    .sorted()
                    .toList();

            for (Path patchFile : patchFiles) {
                String fileName = patchFile.getFileName().toString();
                // Parse label from filename pattern: {label}_patch_{nnnn}.png
                String label = "unknown";
                int patchIdx = fileName.indexOf("_patch_");
                if (patchIdx > 0) {
                    label = fileName.substring(0, patchIdx);
                }
                String entryName = zipPrefix + label + "/" + fileName;
                addFile(zos, patchFile, entryName);
                rows.add(new String[]{label, entryName});
            }
        }
        return rows;
    }

    /** Build labels.csv content: header + rows of (file, label). */
    private String buildLabelsCsv(List<String[]> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("file,label\n");
        for (String[] row : rows) {
            sb.append(row[1]).append(",").append(row[0]).append("\n");
        }
        return sb.toString();
    }

    // ── Common helpers ───────────────────────────────────────────────────────

    private List<Map<String, Object>> buildAnnDicts(List<Annotation> anns,
                                                     Map<Long, Label> labelMap) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Annotation ann : anns) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("id", ann.getId());
            d.put("type", ann.getAnnoType());
            d.put("coordinates", parseJson(ann.getCoordinates()));
            Label label = labelMap.get(ann.getLabelId());
            if (label != null) {
                d.put("label_class_index", label.getClassIndex());
                d.put("label_name", label.getName());
            } else {
                d.put("label_class_index", 1);
                d.put("label_name", "unknown");
            }
            result.add(d);
        }
        return result;
    }

    private Object parseJson(String json) {
        if (json == null) return List.of();
        try { return objectMapper.readValue(json, Object.class); }
        catch (Exception e) { return List.of(); }
    }

    private String buildSingleReport(ExportFormat format, SlideBundle b) {
        StringBuilder sb = new StringBuilder();
        sb.append("WSI Annotation Export Report\n");
        sb.append("============================\n");
        sb.append("Format    : ").append(format).append("\n");
        sb.append("Slide     : ").append(b.slide().getFileName()).append("\n");
        sb.append("Dimensions: ").append(b.slide().getWidth()).append(" x ").append(b.slide().getHeight()).append("\n");
        sb.append("Annotations: ").append(b.annotations().size()).append("\n");
        sb.append("Labels:\n");
        for (Label l : b.labels()) {
            long cnt = b.annotations().stream().filter(a -> l.getId().equals(a.getLabelId())).count();
            sb.append("  [").append(l.getClassIndex()).append("] ")
              .append(l.getName()).append(" — ").append(cnt).append(" annotation(s)\n");
        }
        return sb.toString();
    }

    private String buildBatchReport(ExportFormat format, int requestedCount, int processedCount, int totalAnnotations) {
        StringBuilder sb = new StringBuilder();
        sb.append("WSI Batch Export Report\n");
        sb.append("=======================\n");
        sb.append("Format           : ").append(format).append("\n");
        sb.append("Requested slides : ").append(requestedCount).append("\n");
        sb.append("Processed slides : ").append(processedCount).append("\n");
        sb.append("Total annotations: ").append(totalAnnotations).append("\n");
        if (format == ExportFormat.COCO) {
            sb.append("\nContents:\n");
            sb.append("  images/         — slide thumbnails (one per slide)\n");
            sb.append("  masks/          — semantic masks   (one per slide)\n");
            sb.append("  instances.json  — standard COCO annotation format\n");
        } else if (format == ExportFormat.MASK_ONLY) {
            sb.append("\nContents:\n");
            sb.append("  masks/          — semantic masks   (one per slide)\n");
        } else if (format == ExportFormat.YOLO) {
            sb.append("\nContents:\n");
            sb.append("  images/         — slide thumbnails (one per slide)\n");
            sb.append("  labels/         — YOLO label files (one per slide)\n");
            sb.append("  classes.txt     — class name list\n");
            sb.append("  data.yaml       — YOLO dataset configuration\n");
        } else if (format == ExportFormat.VOC) {
            sb.append("\nContents:\n");
            sb.append("  JPEGImages/     — slide thumbnails (one per slide)\n");
            sb.append("  Annotations/    — Pascal VOC XML   (one per slide)\n");
        } else if (format == ExportFormat.GEOJSON) {
            sb.append("\nContents:\n");
            sb.append("  *.geojson       — GeoJSON FeatureCollection (one per slide)\n");
        } else if (format == ExportFormat.PATCHES) {
            sb.append("\nContents:\n");
            sb.append("  patches/{label}/ — cropped patch images organized by label\n");
            sb.append("  labels.csv       — file-to-label mapping\n");
        }
        return sb.toString();
    }

    private void addFile(ZipOutputStream zos, Path file, String entryName) throws IOException {
        if (!Files.exists(file)) return;
        zos.putNextEntry(new ZipEntry(entryName));
        Files.copy(file, zos);
        zos.closeEntry();
    }

    private void addString(ZipOutputStream zos, String content, String entryName) throws IOException {
        zos.putNextEntry(new ZipEntry(entryName));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }
}
