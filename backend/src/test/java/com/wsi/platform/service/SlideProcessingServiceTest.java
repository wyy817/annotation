package com.wsi.platform.service;

import com.wsi.platform.entity.Slide;
import com.wsi.platform.mapper.SlideMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SlideProcessingServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void repairsReadySlideWithMissingViewerArtifacts() {
        SlideMapper mapper = mock(SlideMapper.class);
        PythonServiceClient python = mock(PythonServiceClient.class);
        SlideProcessingService service = new SlideProcessingService(mapper, python);

        Slide slide = slide(5L);
        when(mapper.selectById(5L)).thenReturn(slide);

        PythonServiceClient.ProcessSlideResponse response =
                new PythonServiceClient.ProcessSlideResponse();
        response.setTileDir(tempDir.resolve("tiles-5").toString());
        response.setThumbnailPath(tempDir.resolve("thumb-5.jpg").toString());
        response.setWidth(97_792);
        response.setHeight(221_184);
        response.setMaxZoom(18);
        when(python.processSlide(5L, slide.getOriginalPath(), 256)).thenReturn(response);

        Slide repaired = service.ensureArtifacts(5L);

        assertThat(repaired.getStatus()).isEqualTo("READY");
        assertThat(repaired.getTileDir()).isEqualTo(response.getTileDir());
        assertThat(repaired.getThumbnailPath()).isEqualTo(response.getThumbnailPath());
        assertThat(repaired.getMaxZoom()).isEqualTo(18);
        verify(mapper).updateById(slide);
    }

    @Test
    void reusesViewerArtifactsAlreadyPresentOnDisk() throws Exception {
        SlideMapper mapper = mock(SlideMapper.class);
        PythonServiceClient python = mock(PythonServiceClient.class);
        SlideProcessingService service = new SlideProcessingService(mapper, python);

        Path tileDir = Files.createDirectories(tempDir.resolve("tiles-4"));
        Files.writeString(tileDir.resolve("slide.dzi"), "<Image />");
        Path thumbnail = Files.write(tempDir.resolve("thumb-4.jpg"), new byte[]{1, 2, 3});

        Slide slide = slide(4L);
        slide.setTileDir(tileDir.toString());
        slide.setThumbnailPath(thumbnail.toString());
        when(mapper.selectById(4L)).thenReturn(slide);

        Slide result = service.ensureArtifacts(4L);

        assertThat(result).isSameAs(slide);
        verifyNoInteractions(python);
        verify(mapper, never()).updateById(any(Slide.class));
    }

    private static Slide slide(long id) {
        Slide slide = new Slide();
        slide.setId(id);
        slide.setStatus("READY");
        slide.setOriginalPath("D:/data/camelyon/slide-" + id + ".tif");
        return slide;
    }
}
