package com.wsi.platform.controller;

import com.wsi.platform.entity.Slide;
import com.wsi.platform.mapper.SlideMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.*;

/**
 * Serves DeepZoom tiles, DZI descriptor, and thumbnails for a READY slide.
 *
 * GET /api/slides/{id}/dzi                       → DZI XML descriptor
 * GET /api/slides/{id}/tiles/{level}/{filename}  → JPEG tile
 * GET /api/slides/{id}/thumbnail                 → JPEG thumbnail
 */
@RestController
@RequiredArgsConstructor
public class TileController {

    private final SlideMapper slideMapper;

    @GetMapping(value = "/api/slides/{id}/dzi", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> getDzi(@PathVariable Long id) throws IOException {
        Slide slide = slideMapper.selectById(id);
        if (slide == null) return ResponseEntity.notFound().build();
        if (slide.getTileDir() == null) return ResponseEntity.status(503).build();

        Path dzi = Paths.get(slide.getTileDir(), "slide.dzi");
        if (!Files.exists(dzi)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Files.readString(dzi));
    }

    @SuppressWarnings("null")
    @GetMapping("/api/slides/{id}/tiles/{level}/{filename}")
    public ResponseEntity<byte[]> getTile(@PathVariable Long id,
                                          @PathVariable String level,
                                          @PathVariable String filename) throws IOException {
        Slide slide = slideMapper.selectById(id);
        if (slide == null) return ResponseEntity.notFound().build();
        if (slide.getTileDir() == null) return ResponseEntity.status(503).build();

        Path tile = resolveTilePath(slide.getTileDir(), level, filename);
        if (tile == null) return ResponseEntity.notFound().build();

        String lower = tile.getFileName().toString().toLowerCase();
        MediaType mt = lower.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok().contentType(mt).body(Files.readAllBytes(tile));
    }

    /** 兼容 jpg / jpeg / png 扩展名不一致的情况 */
    private static Path resolveTilePath(String tileDir, String level, String filename) {
        Path base = Paths.get(tileDir, "slide_files", level);
        Path exact = base.resolve(filename);
        if (Files.exists(exact)) return exact;

        // 去掉扩展名后依次尝试常见格式
        String stem = filename.contains(".")
                ? filename.substring(0, filename.lastIndexOf('.'))
                : filename;
        for (String ext : new String[]{".jpg", ".jpeg", ".png"}) {
            Path candidate = base.resolve(stem + ext);
            if (Files.exists(candidate)) return candidate;
        }
        return null;
    }

    @GetMapping("/api/slides/{id}/thumbnail")
    public ResponseEntity<byte[]> getThumbnail(@PathVariable Long id) throws IOException {
        Slide slide = slideMapper.selectById(id);
        if (slide == null) return ResponseEntity.notFound().build();
        if (slide.getThumbnailPath() == null) return ResponseEntity.status(503).build();

        Path thumb = Paths.get(slide.getThumbnailPath());
        if (!Files.exists(thumb)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
                .body(Files.readAllBytes(thumb));
    }
}
