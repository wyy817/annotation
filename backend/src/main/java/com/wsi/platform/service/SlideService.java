package com.wsi.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wsi.platform.config.StorageProperties;
import com.wsi.platform.entity.Slide;
import com.wsi.platform.entity.Workspace;
import com.wsi.platform.mapper.SlideMapper;
import com.wsi.platform.mapper.WorkspaceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Handles slide file storage and DB record management.
 * Async tile generation is handled separately by SlideProcessingService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlideService {

    private static final Set<String> SUPPORTED_EXT = Set.of(".png", ".jpg", ".jpeg", ".svs");

    private final SlideMapper slideMapper;
    private final WorkspaceMapper workspaceMapper;
    private final StorageProperties props;

    // ── Query ────────────────────────────────────────────────────────────────

    public List<Slide> listByWorkspace(Long workspaceId) {
        return slideMapper.selectList(
                new LambdaQueryWrapper<Slide>()
                        .eq(Slide::getWorkspaceId, workspaceId)
                        .orderByDesc(Slide::getCreatedAt));
    }

    public Slide getById(Long id) {
        return slideMapper.selectById(id);
    }

    public boolean delete(Long id) {
        return slideMapper.deleteById(id) > 0;
    }

    public boolean move(Long slideId, Long targetWorkspaceId) {
        Slide slide = slideMapper.selectById(slideId);
        if (slide == null) return false;
        if (workspaceMapper.selectById(targetWorkspaceId) == null) return false;
        slide.setWorkspaceId(targetWorkspaceId);
        slideMapper.updateById(slide);
        return true;
    }

    // ── Upload ───────────────────────────────────────────────────────────────

    /**
     * Save uploaded files under data/uploads/{workspaceId}/ and create Slide records.
     * Supported inputs:
     *   - Image files (.png / .jpg / .svs)  → one Slide each
     *   - .zip archive                        → extract, one Slide per image inside
     *   - .tar.gz / .tgz archive              → same
     * Returns the newly created Slide list (all status=PENDING).
     */
    public List<Slide> uploadFiles(Long workspaceId, List<MultipartFile> files) throws IOException {
        Workspace ws = workspaceMapper.selectById(workspaceId);
        if (ws == null) throw new IllegalArgumentException("Workspace not found: " + workspaceId);

        Path uploadDir = Paths.get(props.getStorage().getUploadDir())
                              .resolve(String.valueOf(workspaceId))
                              .toAbsolutePath();
        Files.createDirectories(uploadDir);

        List<Slide> created = new ArrayList<>();
        for (MultipartFile file : files) {
            String originalName = file.getOriginalFilename();
            if (originalName == null || originalName.isBlank()) continue;

            String lower = originalName.toLowerCase();
            if (lower.endsWith(".zip")) {
                created.addAll(extractZip(workspaceId, uploadDir, file.getInputStream()));
            } else if (lower.endsWith(".tar.gz") || lower.endsWith(".tgz")) {
                created.addAll(extractTarGz(workspaceId, uploadDir, file.getInputStream()));
            } else if (isSupportedImage(originalName)) {
                Slide slide = saveImageFile(workspaceId, uploadDir,
                        file.getInputStream(), originalName, file.getSize());
                created.add(slide);
            } else {
                log.warn("Unsupported file type, skipping: {}", originalName);
            }
        }
        return created;
    }

    // ── Archive extraction ───────────────────────────────────────────────────

    private List<Slide> extractZip(Long workspaceId, Path uploadDir,
                                   InputStream inputStream) throws IOException {
        List<Slide> slides = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) { zis.closeEntry(); continue; }
                String entryPath = entry.getName();
                if (isArchiveJunk(entryPath)) { zis.closeEntry(); continue; }
                String name = Paths.get(entryPath).getFileName().toString();
                if (!isSupportedImage(name)) { zis.closeEntry(); continue; }

                Path dest = uploadDir.resolve(UUID.randomUUID() + "_" + name);
                Files.copy(zis, dest, StandardCopyOption.REPLACE_EXISTING);
                slides.add(createSlideRecord(workspaceId, name, dest, Files.size(dest)));
                zis.closeEntry();
            }
        }
        return slides;
    }

    private List<Slide> extractTarGz(Long workspaceId, Path uploadDir,
                                     InputStream inputStream) throws IOException {
        List<Slide> slides = new ArrayList<>();
        try (TarArchiveInputStream tis = new TarArchiveInputStream(
                new GzipCompressorInputStream(inputStream))) {
            TarArchiveEntry entry;
            while ((entry = tis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String entryPath = entry.getName();
                if (isArchiveJunk(entryPath)) continue;
                String name = Paths.get(entryPath).getFileName().toString();
                if (!isSupportedImage(name)) continue;

                Path dest = uploadDir.resolve(UUID.randomUUID() + "_" + name);
                Files.copy(tis, dest, StandardCopyOption.REPLACE_EXISTING);
                slides.add(createSlideRecord(workspaceId, name, dest, Files.size(dest)));
            }
        }
        return slides;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Slide saveImageFile(Long workspaceId, Path uploadDir,
                                InputStream inputStream, String originalName,
                                long fileSize) throws IOException {
        Path dest = uploadDir.resolve(UUID.randomUUID() + "_" + originalName);
        Files.copy(inputStream, dest, StandardCopyOption.REPLACE_EXISTING);
        return createSlideRecord(workspaceId, originalName, dest, fileSize);
    }

    private Slide createSlideRecord(Long workspaceId, String fileName,
                                    Path filePath, long fileSize) {
        String ext = getExtension(fileName).toUpperCase().replace(".", "");
        if ("JPEG".equals(ext)) ext = "JPG";

        Slide slide = new Slide();
        slide.setWorkspaceId(workspaceId);
        slide.setFileName(fileName);
        slide.setOriginalPath(filePath.toString());
        slide.setFormat(ext);
        slide.setFileSize(fileSize);
        slide.setStatus("PENDING");
        slide.setAnnoStatus("NOT_STARTED");
        slideMapper.insert(slide);
        log.info("Created slide id={} file={} workspace={}", slide.getId(), fileName, workspaceId);
        return slide;
    }

    /** Skip macOS resource forks (._*) and __MACOSX directories inside archives. */
    private boolean isArchiveJunk(String entryPath) {
        String fileName = Paths.get(entryPath).getFileName().toString();
        return fileName.startsWith("._") || entryPath.contains("__MACOSX");
    }

    private boolean isSupportedImage(String name) {
        return SUPPORTED_EXT.contains(getExtension(name.toLowerCase()));
    }

    private String getExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot) : "";
    }
}
