package com.wsi.platform.controller;

import com.wsi.platform.entity.Slide;
import com.wsi.platform.mapper.SlideMapper;
import com.wsi.platform.service.PythonServiceClient;
import com.wsi.platform.service.SlideProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Serves DeepZoom metadata, thumbnails, and cached/on-demand WSI tiles. */
@Slf4j
@RestController
@RequiredArgsConstructor
public class TileController {

    private static final Pattern TILE_FILE = Pattern.compile(
            "^(\\d+)_(\\d+)\\.(?:jpg|jpeg|png)$",
            Pattern.CASE_INSENSITIVE
    );

    private final SlideMapper slideMapper;
    private final SlideProcessingService processingService;
    private final PythonServiceClient pythonClient;

    @GetMapping(value = "/api/slides/{id}/dzi", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> getDzi(@PathVariable Long id) throws IOException {
        Slide slide = slideMapper.selectById(id);
        if (slide == null) return ResponseEntity.notFound().build();
        try {
            slide = processingService.ensureArtifacts(id);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(503).build();
        }

        Path dzi = Paths.get(slide.getTileDir(), "slide.dzi");
        if (!Files.isRegularFile(dzi)) return ResponseEntity.status(503).build();
        return ResponseEntity.ok(Files.readString(dzi));
    }

    @GetMapping("/api/slides/{id}/tiles/{level}/{filename}")
    public ResponseEntity<byte[]> getTile(@PathVariable Long id,
                                          @PathVariable String level,
                                          @PathVariable String filename) throws IOException {
        TileCoordinate coordinate = parseTileCoordinate(level, filename);
        if (coordinate == null) return ResponseEntity.notFound().build();

        Slide slide = slideMapper.selectById(id);
        if (slide == null) return ResponseEntity.notFound().build();
        if (slide.getTileDir() == null || slide.getTileDir().isBlank()) {
            try {
                slide = processingService.ensureArtifacts(id);
            } catch (IllegalStateException e) {
                return ResponseEntity.status(503).build();
            }
        }

        Path cached = resolveTilePath(slide.getTileDir(), coordinate);
        if (cached != null) return tileResponse(Files.readAllBytes(cached));
        if (slide.getOriginalPath() == null || slide.getOriginalPath().isBlank()) {
            return ResponseEntity.status(503).build();
        }

        try {
            ResponseEntity<byte[]> generated = pythonClient.getSlideTile(
                    id,
                    slide.getOriginalPath(),
                    slide.getTileSize() == null ? 256 : slide.getTileSize(),
                    coordinate.level(),
                    coordinate.col(),
                    coordinate.row()
            );
            if (!generated.getStatusCode().is2xxSuccessful() || generated.getBody() == null) {
                return ResponseEntity.status(generated.getStatusCode()).build();
            }
            return tileResponse(generated.getBody());
        } catch (RestClientException e) {
            log.warn("Could not render tile for slide {} level={} col={} row={}: {}",
                    id, coordinate.level(), coordinate.col(), coordinate.row(), e.getMessage());
            return ResponseEntity.status(503).build();
        }
    }

    @GetMapping("/api/slides/{id}/thumbnail")
    public ResponseEntity<byte[]> getThumbnail(@PathVariable Long id) throws IOException {
        Slide slide = slideMapper.selectById(id);
        if (slide == null) return ResponseEntity.notFound().build();
        try {
            slide = processingService.ensureArtifacts(id);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(503).build();
        }

        Path thumbnail = Paths.get(slide.getThumbnailPath());
        if (!Files.isRegularFile(thumbnail)) return ResponseEntity.status(503).build();
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .body(Files.readAllBytes(thumbnail));
    }

    private static ResponseEntity<byte[]> tileResponse(byte[] payload) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .body(payload);
    }

    private static TileCoordinate parseTileCoordinate(String level, String filename) {
        Matcher matcher = TILE_FILE.matcher(filename);
        if (!matcher.matches()) return null;
        try {
            int parsedLevel = Integer.parseInt(level);
            int col = Integer.parseInt(matcher.group(1));
            int row = Integer.parseInt(matcher.group(2));
            if (parsedLevel < 0 || col < 0 || row < 0) return null;
            return new TileCoordinate(parsedLevel, col, row);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Path resolveTilePath(String tileDir, TileCoordinate coordinate) {
        Path base = Paths.get(
                tileDir,
                "slide_files",
                String.valueOf(coordinate.level())
        );
        String stem = coordinate.col() + "_" + coordinate.row();
        for (String extension : new String[]{".jpg", ".jpeg", ".png"}) {
            Path candidate = base.resolve(stem + extension);
            if (Files.isRegularFile(candidate)) return candidate;
        }
        return null;
    }

    private record TileCoordinate(int level, int col, int row) {
    }
}
