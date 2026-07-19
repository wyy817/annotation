package com.wsi.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class AiReportContentValidator {

    private static final Set<String> CONCORDANCE_VALUES =
            Set.of("concordant", "discordant", "indeterminate");

    private final ObjectMapper objectMapper;

    public AiReportContentValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ValidationResult validateAndNormalize(JsonNode input, String language) {
        List<String> errors = new ArrayList<>();
        ObjectNode normalized = objectMapper.createObjectNode();
        if (input == null || !input.isObject()) {
            return new ValidationResult(null, List.of("report must be a JSON object"));
        }

        copyRequiredText(input, normalized, "impression", 1_200, errors);
        copyRequiredText(input, normalized, "microscopic_description", 2_000, errors);
        copyStringArray(input, normalized, "key_findings", 1, 6, 700, errors);

        String concordance = text(input, "clinicopathologic_concordance");
        if (concordance == null
                || !CONCORDANCE_VALUES.contains(concordance.toLowerCase(Locale.ROOT))) {
            errors.add("clinicopathologic_concordance must be concordant, discordant, or indeterminate");
        } else {
            normalized.put("clinicopathologic_concordance", concordance.toLowerCase(Locale.ROOT));
        }

        copyRequiredText(input, normalized, "draft_assessment", 1_200, errors);
        copyStringArray(input, normalized, "suggested_review_steps", 0, 5, 500, errors);
        ArrayNode limitations = copyStringArray(
                input, normalized, "limitations", 1, 6, 700, errors
        );

        if (errors.isEmpty()) {
            String requiredLimitation = isChinese(language)
                    ? "CLAM 注意力属于研究用途的模型证据，不是校准后的病变概率或肿瘤分割；本报告为医生复核用草稿。"
                    : "CLAM attention is research-use model evidence, not calibrated lesion probability or tumor segmentation; this report is a clinician-review draft.";
            boolean present = false;
            for (JsonNode limitation : limitations) {
                String value = limitation.asText().toLowerCase(Locale.ROOT);
                if (value.contains("clam") || value.contains("校准") || value.contains("calibrated")) {
                    present = true;
                    break;
                }
            }
            if (!present) limitations.add(requiredLimitation);
            normalized.put("report_status", "DRAFT");
            normalized.put("clinician_review_required", true);
        }

        return new ValidationResult(errors.isEmpty() ? normalized : null, List.copyOf(errors));
    }

    private void copyRequiredText(JsonNode source,
                                  ObjectNode target,
                                  String field,
                                  int maxLength,
                                  List<String> errors) {
        String value = text(source, field);
        if (value == null || value.isBlank()) {
            errors.add(field + " is required");
            return;
        }
        if (value.length() > maxLength) {
            errors.add(field + " exceeds " + maxLength + " characters");
            return;
        }
        target.put(field, value.trim());
    }

    private ArrayNode copyStringArray(JsonNode source,
                                      ObjectNode target,
                                      String field,
                                      int minItems,
                                      int maxItems,
                                      int maxItemLength,
                                      List<String> errors) {
        ArrayNode output = target.putArray(field);
        JsonNode value = source.get(field);
        if (value == null || !value.isArray()) {
            errors.add(field + " must be an array");
            return output;
        }
        if (value.size() < minItems || value.size() > maxItems) {
            errors.add(field + " must contain " + minItems + " to " + maxItems + " items");
            return output;
        }
        for (JsonNode item : value) {
            if (!item.isTextual() || item.asText().isBlank()) {
                errors.add(field + " must contain non-empty strings");
                continue;
            }
            String text = item.asText().trim();
            if (text.length() > maxItemLength) {
                errors.add(field + " contains an item longer than " + maxItemLength + " characters");
                continue;
            }
            output.add(text);
        }
        return output;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.asText() : null;
    }

    private static boolean isChinese(String language) {
        return language != null && language.toLowerCase(Locale.ROOT).startsWith("zh");
    }

    public record ValidationResult(JsonNode report, List<String> errors) {
    }
}
