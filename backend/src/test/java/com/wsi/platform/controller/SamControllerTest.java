package com.wsi.platform.controller;

import com.wsi.platform.common.MessageUtil;
import com.wsi.platform.common.Result;
import com.wsi.platform.entity.Slide;
import com.wsi.platform.mapper.SlideMapper;
import com.wsi.platform.service.PythonServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SamControllerTest {

    @Mock
    private SlideMapper slideMapper;

    @Mock
    private PythonServiceClient pythonServiceClient;

    @Mock
    private MessageUtil messageUtil;

    private SamController controller;

    @BeforeEach
    void setUp() {
        controller = new SamController(slideMapper, pythonServiceClient, messageUtil);

        Slide slide = new Slide();
        slide.setId(5L);
        slide.setStatus("READY");
        slide.setOriginalPath("D:/data/tumor_001.tif");
        when(slideMapper.selectById(5L)).thenReturn(slide);
    }

    @Test
    void mapsDownstreamServiceUnavailableToReadableResult() {
        when(messageUtil.msg("error.sam.unavailable")).thenReturn("SAM unavailable");
        when(pythonServiceClient.samPredict(any())).thenThrow(
                HttpServerErrorException.create(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Service Unavailable",
                        HttpHeaders.EMPTY,
                        "{\"detail\":\"SAM_CHECKPOINT env var is not set\"}"
                                .getBytes(StandardCharsets.UTF_8),
                        StandardCharsets.UTF_8
                )
        );

        Result<SamController.SamResponse> result = controller.predict(5L, pointRequest());

        assertThat(result.getCode()).isEqualTo(503);
        assertThat(result.getMessage()).isEqualTo("SAM unavailable");
    }

    @Test
    void mapsConnectionFailureToServiceUnavailable() {
        when(messageUtil.msg("error.sam.unavailable")).thenReturn("SAM unavailable");
        when(pythonServiceClient.samPredict(any()))
                .thenThrow(new ResourceAccessException("Connection refused"));

        Result<SamController.SamResponse> result = controller.predict(5L, pointRequest());

        assertThat(result.getCode()).isEqualTo(503);
        assertThat(result.getMessage()).isEqualTo("SAM unavailable");
    }

    private SamController.SamRequest pointRequest() {
        SamController.SamRequest request = new SamController.SamRequest();
        request.setPoints(List.of(List.of(71168.0, 133184.0)));
        request.setPointLabels(List.of(1));
        return request;
    }
}
