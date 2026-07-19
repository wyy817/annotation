package com.wsi.platform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "pathorchestra.workflow")
public class CaseWorkflowProperties {
    private double lowConfidenceDisagreement = 0.15;
    private double boundaryEvidenceMin = 0.35;
    private double boundaryEvidenceMax = 0.65;
    private int randomQcPercentage = 10;
}
