package com.wsi.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wsi.platform.config.StorageProperties;
import com.wsi.platform.entity.Annotation;
import com.wsi.platform.entity.ExportTask;
import com.wsi.platform.entity.Label;
import com.wsi.platform.entity.Slide;
import com.wsi.platform.mapper.AnnotationMapper;
import com.wsi.platform.mapper.ExportTaskMapper;
import com.wsi.platform.mapper.LabelMapper;
import com.wsi.platform.mapper.SlideMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/**
 * Async export processor — kept in a separate bean so that @Async proxying works.
 *
 * Single export ZIP contents:
 *   thumbnail.jpg       — slide thumbnail
 *   mask.png            — semantic mask (pixel = class index)
 *   annotations.json    — COCO-style metadata
 *   report.txt          — summary statistics
 *
 * Batch export ZIP contents:
 *   image/{slideId}.jpg — thumbnail per slide
 *   mask/{slideId}.png  — mask per slide
 *   annotations.json    — combined metadata for all slides
 *   report.txt          — combined summary
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

            // ── Step 1: generate semantic mask ────────────────────────────────
            String maskPath = null;
            try {
                PythonServiceClient.GenerateMaskResponse r = pythonClient.generateMask(
                        slide.getId(), slide.getWidth(), slide.getHeight(), annDicts);
                maskPath = r.getMaskPath();
            } catch (Exception e) {
                log.warn("generate-mask skipped for slide {}: {}", slide.getId(), e.getMessage());
            }

            // ── Step 2: package ZIP ───────────────────────────────────────────
            Path exportDir = Paths.get(props.getStorage().getExportDir())
                    .resolve(String.valueOf(slide.getId())).toAbsolutePath();
            Files.createDirectories(exportDir);
            Path zipPath = exportDir.resolve("export_" + taskId + ".zip");

            try (ZipOutputStream zos = new ZipOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(zipPath)))) {

                if (slide.getThumbnailPath() != null)
                    addFile(zos, Paths.get(slide.getThumbnailPath()), "thumbnail.jpg");

                if (maskPath != null)
                    addFile(zos, Paths.get(maskPath), "mask.png");

                addString(zos, buildCocoJson(slide, labels, annotations, labelMap), "annotations.json");
                addString(zos, buildReport(slide, labels, annotations), "report.txt");
            }

            task.setStatus("DONE");
            task.setZipPath(zipPath.toString());
            task.setTotal(annotations.size());
            exportTaskMapper.updateById(task);
            log.info("Export task {} DONE → {}", taskId, zipPath);

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
            Path exportDir = Paths.get(props.getStorage().getExportDir())
                    .resolve("batch_" + taskId).toAbsolutePath();
            Files.createDirectories(exportDir);
            Path zipPath = exportDir.resolve("batch_export_" + taskId + ".zip");

            List<Map<String, Object>> allSlideData = new ArrayList<>();
            int totalAnnotations = 0;
            int processedSlides = 0;

            try (ZipOutputStream zos = new ZipOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(zipPath)))) {

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
                    List<Map<String, Object>> annDicts = buildAnnDicts(annotations, labelMap);

                    // image/{slideId}.jpg
                    if (slide.getThumbnailPath() != null) {
                        addFile(zos, Paths.get(slide.getThumbnailPath()), "image/" + slideId + ".jpg");
                    }

                    // mask/{slideId}.png
                    try {
                        PythonServiceClient.GenerateMaskResponse r = pythonClient.generateMask(
                                slide.getId(), slide.getWidth(), slide.getHeight(), annDicts);
                        addFile(zos, Paths.get(r.getMaskPath()), "mask/" + slideId + ".png");
                    } catch (Exception e) {
                        log.warn("generate-mask skipped for slide {} in batch {}: {}", slideId, taskId, e.getMessage());
                    }

                    totalAnnotations += annotations.size();
                    processedSlides++;
                    allSlideData.add(buildSlideAnnotationMap(slide, labels, annotations, labelMap));
                }

                // One combined annotations.json and report.txt
                addString(zos, buildCombinedCocoJson(allSlideData), "annotations.json");
                addString(zos, buildBatchReport(slideIds.size(), processedSlides, totalAnnotations), "report.txt");
            }

            task.setStatus("DONE");
            task.setZipPath(zipPath.toString());
            task.setTotal(totalAnnotations);
            exportTaskMapper.updateById(task);
            log.info("Batch export task {} DONE → {} ({} slides)", taskId, zipPath, processedSlides);

        } catch (Exception e) {
            log.error("Batch export task {} FAILED: {}", taskId, e.getMessage(), e);
            task.setStatus("ERROR");
            exportTaskMapper.updateById(task);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

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

    private Map<String, Object> buildSlideAnnotationMap(Slide slide, List<Label> labels,
                                                          List<Annotation> anns,
                                                          Map<Long, Label> labelMap) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("slide_id", slide.getId());
        m.put("file_name", slide.getFileName());
        m.put("width", slide.getWidth());
        m.put("height", slide.getHeight());

        List<Map<String, Object>> cats = new ArrayList<>();
        for (Label l : labels) {
            Map<String, Object> cat = new LinkedHashMap<>();
            cat.put("id", l.getId());
            cat.put("name", l.getName());
            cat.put("color", l.getColor());
            cat.put("class_index", l.getClassIndex());
            cats.add(cat);
        }
        m.put("categories", cats);

        List<Map<String, Object>> annList = new ArrayList<>();
        for (Annotation ann : anns) {
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("id", ann.getId());
            a.put("type", ann.getAnnoType());
            a.put("label_id", ann.getLabelId());
            Label l = labelMap.get(ann.getLabelId());
            a.put("label_name", l != null ? l.getName() : null);
            a.put("coordinates", parseJson(ann.getCoordinates()));
            annList.add(a);
        }
        m.put("annotations", annList);
        return m;
    }

    private String buildCocoJson(Slide slide, List<Label> labels,
                                  List<Annotation> anns, Map<Long, Label> labelMap) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(buildSlideAnnotationMap(slide, labels, anns, labelMap));
        } catch (Exception e) {
            return "{}";
        }
    }

    private String buildCombinedCocoJson(List<Map<String, Object>> allSlideData) {
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("slides", allSlideData);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String buildReport(Slide slide, List<Label> labels, List<Annotation> anns) {
        StringBuilder sb = new StringBuilder();
        sb.append("WSI Annotation Export Report\n");
        sb.append("============================\n");
        sb.append("Slide     : ").append(slide.getFileName()).append("\n");
        sb.append("Dimensions: ").append(slide.getWidth()).append(" x ").append(slide.getHeight()).append("\n");
        sb.append("Annotations: ").append(anns.size()).append("\n");
        sb.append("Labels:\n");
        for (Label l : labels) {
            long cnt = anns.stream().filter(a -> l.getId().equals(a.getLabelId())).count();
            sb.append("  [").append(l.getClassIndex()).append("] ")
              .append(l.getName()).append(" — ").append(cnt).append(" annotation(s)\n");
        }
        return sb.toString();
    }

    private String buildBatchReport(int requestedCount, int processedCount, int totalAnnotations) {
        StringBuilder sb = new StringBuilder();
        sb.append("WSI Batch Export Report\n");
        sb.append("=======================\n");
        sb.append("Requested slides : ").append(requestedCount).append("\n");
        sb.append("Processed slides : ").append(processedCount).append("\n");
        sb.append("Total annotations: ").append(totalAnnotations).append("\n");
        sb.append("\nContents:\n");
        sb.append("  image/   — slide thumbnails (one per slide)\n");
        sb.append("  mask/    — semantic masks   (one per slide)\n");
        sb.append("  annotations.json — combined annotation metadata\n");
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
