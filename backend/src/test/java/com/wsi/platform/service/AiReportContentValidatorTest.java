package com.wsi.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiReportContentValidatorTest {

    private ObjectMapper objectMapper;
    private AiReportContentValidator validator;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        validator = new AiReportContentValidator(objectMapper);
    }

    @Test
    void normalizesValidReportAndAddsRequiredSafetyBoundary() throws Exception {
        JsonNode input = objectMapper.readTree("""
                {
                  "impression": "Elevated model evidence requires pathologist correlation.",
                  "microscopic_description": "Five model-evidence regions were identified.",
                  "key_findings": ["Slide-level evidence is elevated."],
                  "clinicopathologic_concordance": "concordant",
                  "draft_assessment": "The clinician accepted the highlighted evidence.",
                  "suggested_review_steps": ["Review the source WSI."],
                  "limitations": ["Candidate area is approximate."]
                }
                """);

        AiReportContentValidator.ValidationResult result =
                validator.validateAndNormalize(input, "en");

        assertThat(result.errors()).isEmpty();
        assertThat(result.report().path("report_status").asText()).isEqualTo("DRAFT");
        assertThat(result.report().path("clinician_review_required").asBoolean()).isTrue();
        assertThat(result.report().path("limitations").toString()).contains("CLAM attention");
    }

    @Test
    void rejectsMissingArraysAndUnsupportedConcordance() throws Exception {
        JsonNode input = objectMapper.readTree("""
                {
                  "impression": "Draft",
                  "microscopic_description": "Description",
                  "clinicopathologic_concordance": "certain",
                  "draft_assessment": "Assessment"
                }
                """);

        AiReportContentValidator.ValidationResult result =
                validator.validateAndNormalize(input, "en");

        assertThat(result.report()).isNull();
        assertThat(result.errors())
                .anyMatch(error -> error.contains("key_findings"))
                .anyMatch(error -> error.contains("clinicopathologic_concordance"))
                .anyMatch(error -> error.contains("limitations"));
    }
}
