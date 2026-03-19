package com.wsi.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wsi.platform.common.Result;
import com.wsi.platform.entity.Annotation;
import com.wsi.platform.entity.Label;
import com.wsi.platform.entity.Slide;
import com.wsi.platform.mapper.AnnotationMapper;
import com.wsi.platform.mapper.LabelMapper;
import com.wsi.platform.mapper.SlideMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

/**
 * GET /api/statistics/annotations — annotation statistics across slides
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class StatisticsController {

    private final AnnotationMapper annotationMapper;
    private final LabelMapper labelMapper;
    private final SlideMapper slideMapper;
    private final ObjectMapper objectMapper;

    @Data
    public static class LabelStats {
        private String name;
        private String color;
        private int count;
        private double area; // estimated area in pixels²
    }

    @Data
    public static class TypeStats {
        private String type;
        private int count;
    }

    @Data
    public static class SlideAnnoStats {
        private Long slideId;
        private String fileName;
        private int annotationCount;
    }

    @Data
    public static class StatsResponse {
        private List<LabelStats> labelStats = new ArrayList<>();
        private List<TypeStats> typeStats = new ArrayList<>();
        private List<SlideAnnoStats> slideStats = new ArrayList<>();
        private int totalAnnotations;
        private int totalSlides;
    }

    @GetMapping("/api/statistics/annotations")
    public Result<StatsResponse> annotationStats(
            @RequestParam(required = false) Long workspaceId) {

        // 1. Get relevant slides
        LambdaQueryWrapper<Slide> slideQuery = new LambdaQueryWrapper<Slide>()
                .eq(Slide::getStatus, "READY");
        if (workspaceId != null) {
            slideQuery.eq(Slide::getWorkspaceId, workspaceId);
        }
        List<Slide> slides = slideMapper.selectList(slideQuery);
        Set<Long> slideIds = slides.stream().map(Slide::getId).collect(Collectors.toSet());

        if (slideIds.isEmpty()) {
            return Result.success(new StatsResponse());
        }

        // 2. Load all annotations for these slides
        List<Annotation> annotations = annotationMapper.selectList(
                new LambdaQueryWrapper<Annotation>()
                        .in(Annotation::getSlideId, slideIds));

        // 3. Load all labels for these slides
        List<Label> labels = labelMapper.selectList(
                new LambdaQueryWrapper<Label>()
                        .in(Label::getSlideId, slideIds));
        Map<Long, Label> labelMap = labels.stream()
                .collect(Collectors.toMap(Label::getId, l -> l, (a, b) -> a));

        // 4. Aggregate per-label stats
        Map<String, LabelStats> labelStatsMap = new LinkedHashMap<>();
        Map<String, Integer> typeCounts = new LinkedHashMap<>();

        for (Annotation ann : annotations) {
            // Type stats
            typeCounts.merge(ann.getAnnoType(), 1, Integer::sum);

            // Label stats
            Label label = ann.getLabelId() != null ? labelMap.get(ann.getLabelId()) : null;
            String labelName = label != null ? label.getName() : "未分类";
            String labelColor = label != null ? label.getColor() : "#999999";

            LabelStats ls = labelStatsMap.computeIfAbsent(labelName, k -> {
                LabelStats s = new LabelStats();
                s.setName(k);
                s.setColor(labelColor);
                return s;
            });
            ls.setCount(ls.getCount() + 1);
            ls.setArea(ls.getArea() + estimateArea(ann));
        }

        // 5. Per-slide annotation count
        Map<Long, Integer> slideAnnoCounts = annotations.stream()
                .collect(Collectors.groupingBy(Annotation::getSlideId,
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));
        Map<Long, Slide> slideMap = slides.stream()
                .collect(Collectors.toMap(Slide::getId, s -> s));

        List<SlideAnnoStats> slideStats = slideAnnoCounts.entrySet().stream()
                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                .limit(20) // top 20
                .map(e -> {
                    SlideAnnoStats ss = new SlideAnnoStats();
                    ss.setSlideId(e.getKey());
                    Slide sl = slideMap.get(e.getKey());
                    ss.setFileName(sl != null ? sl.getFileName() : "#" + e.getKey());
                    ss.setAnnotationCount(e.getValue());
                    return ss;
                })
                .collect(Collectors.toList());

        // 6. Build response
        StatsResponse resp = new StatsResponse();
        resp.setLabelStats(new ArrayList<>(labelStatsMap.values()));
        resp.setTypeStats(typeCounts.entrySet().stream().map(e -> {
            TypeStats ts = new TypeStats();
            ts.setType(e.getKey());
            ts.setCount(e.getValue());
            return ts;
        }).collect(Collectors.toList()));
        resp.setSlideStats(slideStats);
        resp.setTotalAnnotations(annotations.size());
        resp.setTotalSlides(slideIds.size());

        return Result.success(resp);
    }

    /**
     * Estimate annotation area in pixels².
     * - polygon: Shoelace formula
     * - rect: width × height from bounding coordinates
     * - brush: sum of stroke lengths × brush size
     */
    private double estimateArea(Annotation ann) {
        try {
            String type = ann.getAnnoType();
            String coords = ann.getCoordinates();
            if (coords == null || coords.isBlank()) return 0;

            if ("polygon".equals(type)) {
                List<List<Double>> points = objectMapper.readValue(coords,
                        new TypeReference<List<List<Double>>>() {});
                return shoelaceArea(points);
            } else if ("rect".equals(type)) {
                List<List<Double>> points = objectMapper.readValue(coords,
                        new TypeReference<List<List<Double>>>() {});
                if (points.size() >= 2) {
                    double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
                    double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
                    for (List<Double> p : points) {
                        minX = Math.min(minX, p.get(0));
                        minY = Math.min(minY, p.get(1));
                        maxX = Math.max(maxX, p.get(0));
                        maxY = Math.max(maxY, p.get(1));
                    }
                    return (maxX - minX) * (maxY - minY);
                }
            } else if ("brush".equals(type)) {
                // brush: [{"points":[[x,y],...],"size":20}, ...]
                List<Map<String, Object>> strokes = objectMapper.readValue(coords,
                        new TypeReference<List<Map<String, Object>>>() {});
                double totalArea = 0;
                for (Map<String, Object> stroke : strokes) {
                    Number size = (Number) stroke.get("size");
                    double brushSize = size != null ? size.doubleValue() : 10;
                    @SuppressWarnings("unchecked")
                    List<List<Number>> pts = (List<List<Number>>) stroke.get("points");
                    if (pts != null && pts.size() >= 2) {
                        double length = 0;
                        for (int i = 1; i < pts.size(); i++) {
                            double dx = pts.get(i).get(0).doubleValue() - pts.get(i - 1).get(0).doubleValue();
                            double dy = pts.get(i).get(1).doubleValue() - pts.get(i - 1).get(1).doubleValue();
                            length += Math.sqrt(dx * dx + dy * dy);
                        }
                        totalArea += length * brushSize;
                    }
                }
                return totalArea;
            }
        } catch (Exception e) {
            log.debug("Failed to estimate area for annotation {}: {}", ann.getId(), e.getMessage());
        }
        return 0;
    }

    private double shoelaceArea(List<List<Double>> points) {
        if (points.size() < 3) return 0;
        double area = 0;
        int n = points.size();
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            area += points.get(i).get(0) * points.get(j).get(1);
            area -= points.get(j).get(0) * points.get(i).get(1);
        }
        return Math.abs(area) / 2.0;
    }
}
