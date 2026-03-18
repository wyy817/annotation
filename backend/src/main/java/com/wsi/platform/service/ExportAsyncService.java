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
 * Orchestrates: generate-mask → extract-patches → ZIP packaging → ExportTask update.
 *
 * ZIP contents:
 *   thumbnail.jpg       — slide thumbnail
 *   mask.png            — semantic mask (pixel = class index)
 *   patches/            — one PNG per annotation ROI (256×256)
 *   annotations.json    — COCO-style metadata
 *   report.txt          — summary statistics
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

            // ── Step 2: extract patches ───────────────────────────────────────
            String patchDir = null;
            int patchCount = 0;
            try {
                PythonServiceClient.ExtractPatchesResponse r = pythonClient.extractPatches(
                        slide.getId(), slide.getOriginalPath(), annDicts, 256);
                patchDir = r.getPatchDir();
                patchCount = r.getPatchCount();
            } catch (Exception e) {
                log.warn("extract-patches skipped for slide {}: {}", slide.getId(), e.getMessage());
            }

            // ── Step 3: package ZIP ───────────────────────────────────────────
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

                if (patchDir != null) {
                    Path pd = Paths.get(patchDir);
                    if (Files.isDirectory(pd)) {
                        try (var stream = Files.list(pd)) {
                            stream.filter(p -> p.toString().endsWith(".png"))
                                  .sorted()
                                  .forEach(p -> {
                                      try { addFile(zos, p, "patches/" + p.getFileName()); }
                                      catch (IOException ex) {
                                          log.warn("Skipping patch {}: {}", p, ex.getMessage());
                                      }
                                  });
                        }
                    }
                }

                addString(zos, buildCocoJson(slide, labels, annotations, labelMap), "annotations.json");
                addString(zos, buildReport(slide, labels, annotations, patchCount), "report.txt");
            }

            task.setStatus("DONE");
            task.setZipPath(zipPath.toString());
            task.setTotal(annotations.size());
            task.setReady(patchCount);
            exportTaskMapper.updateById(task);
            log.info("Export task {} DONE → {}", taskId, zipPath);

        } catch (Exception e) {
            log.error("Export task {} FAILED: {}", taskId, e.getMessage(), e);
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

    private String buildCocoJson(Slide slide, List<Label> labels,
                                  List<Annotation> anns, Map<Long, Label> labelMap) {
        try {
            Map<String, Object> coco = new LinkedHashMap<>();
            coco.put("slide_id", slide.getId());
            coco.put("file_name", slide.getFileName());
            coco.put("width", slide.getWidth());
            coco.put("height", slide.getHeight());

            List<Map<String, Object>> cats = new ArrayList<>();
            for (Label l : labels) {
                Map<String, Object> cat = new LinkedHashMap<>();
                cat.put("id", l.getId());
                cat.put("name", l.getName());
                cat.put("color", l.getColor());
                cat.put("class_index", l.getClassIndex());
                cats.add(cat);
            }
            coco.put("categories", cats);

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
            coco.put("annotations", annList);

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(coco);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String buildReport(Slide slide, List<Label> labels,
                                List<Annotation> anns, int patchCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("WSI Annotation Export Report\n");
        sb.append("============================\n");
        sb.append("Slide     : ").append(slide.getFileName()).append("\n");
        sb.append("Dimensions: ").append(slide.getWidth()).append(" x ").append(slide.getHeight()).append("\n");
        sb.append("Annotations: ").append(anns.size()).append("\n");
        sb.append("Patches    : ").append(patchCount).append("\n");
        sb.append("Labels:\n");
        for (Label l : labels) {
            long cnt = anns.stream().filter(a -> l.getId().equals(a.getLabelId())).count();
            sb.append("  [").append(l.getClassIndex()).append("] ")
              .append(l.getName()).append(" — ").append(cnt).append(" annotation(s)\n");
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
