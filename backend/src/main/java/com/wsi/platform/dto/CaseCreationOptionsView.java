package com.wsi.platform.dto;

import java.util.List;

public record CaseCreationOptionsView(
        String defaultDiseaseProfileId,
        String defaultSpecimenSiteKey,
        String defaultStain,
        List<SlideOption> slides,
        List<UserOption> pathologists
) {
    public record SlideOption(
            Long id,
            String fileName,
            String format,
            Long fileSize,
            Integer width,
            Integer height,
            String status
    ) {}

    public record UserOption(Long id, String username, String displayName) {}
}
