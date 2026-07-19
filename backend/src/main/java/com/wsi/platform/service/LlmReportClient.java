package com.wsi.platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wsi.platform.config.LlmProperties;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;

@Service
public class LlmReportClient {

    private static final String SYSTEM_PROMPT = """
            You create a structured clinical-assistance draft for a pathologist reviewing a breast
            cancer lymph-node H&E whole-slide image. Use only the supplied quantitative model evidence,
            clinician decision, clinician note, and attached evidence images. The clinician decision is
            authoritative. Never invent patient history, tumor subtype, measurements, stage, ancillary
            studies, or morphology that is not explicitly supported. CLAM attention is research-use model
            evidence, not calibrated lesion probability or tumor segmentation; candidate area is an
            approximation. Return one JSON object only, without Markdown, with exactly these fields:
            impression (string), microscopic_description (string), key_findings (array of 1-6 strings),
            clinicopathologic_concordance (concordant|discordant|indeterminate), draft_assessment (string),
            suggested_review_steps (array of 0-5 strings), limitations (array of 1-6 strings).
            State uncertainty plainly and make clear that a pathologist must finalize the report.
            """;

    private final LlmProperties properties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public LlmReportClient(LlmProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.max(1, properties.getConnectTimeoutSeconds()) * 1_000);
        factory.setReadTimeout(Math.max(1, properties.getReadTimeoutSeconds()) * 1_000);
        this.restTemplate = new RestTemplate(factory);
    }

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    public GenerationResult generate(JsonNode context, List<ImageAttachment> images) {
        if (!properties.isConfigured()) {
            throw new LlmUnavailableException("LLM relay is not configured");
        }

        ObjectNode request = objectMapper.createObjectNode();
        request.put("model", properties.getModel().trim());
        request.put("stream", false);
        request.putObject("response_format").put("type", "json_object");
        ArrayNode messages = request.putArray("messages");
        messages.addObject().put("role", "system").put("content", SYSTEM_PROMPT);

        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        ArrayNode content = userMessage.putArray("content");
        content.addObject()
                .put("type", "text")
                .put("text", "Requested output language and evidence context:\n" + context.toString());

        for (ImageAttachment image : images.stream().limit(4).toList()) {
            String dataUrl = encodeImage(image);
            if (dataUrl == null) continue;
            content.addObject().put("type", "text").put("text", "Evidence image: " + image.label());
            content.addObject()
                    .put("type", "image_url")
                    .putObject("image_url")
                    .put("url", dataUrl)
                    .put("detail", "low");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getApiKey().trim());

        JsonNode response;
        try {
            response = restTemplate.postForObject(
                    completionUrl(), new HttpEntity<>(request, headers), JsonNode.class
            );
        } catch (RestClientResponseException e) {
            throw new LlmUnavailableException(
                    "LLM relay returned HTTP " + e.getStatusCode().value(), e
            );
        } catch (RuntimeException e) {
            throw new LlmUnavailableException("LLM relay request failed", e);
        }
        if (response == null) throw new LlmUnavailableException("LLM relay returned an empty body");

        String contentText = extractContent(response);
        try {
            String responseModel = response.path("model").asText("").trim();
            return new GenerationResult(
                    objectMapper.readTree(extractJsonObject(contentText)),
                    response,
                    responseModel.isEmpty() ? properties.getModel().trim() : responseModel
            );
        } catch (JsonProcessingException e) {
            throw new LlmUnavailableException("LLM relay returned invalid JSON content", e);
        }
    }

    private String completionUrl() {
        String base = properties.getBaseUrl().trim().replaceAll("/+$", "");
        if (base.endsWith("/chat/completions")) return base;
        if (base.endsWith("/v1")) return base + "/chat/completions";
        return base + "/v1/chat/completions";
    }

    private String extractContent(JsonNode response) {
        JsonNode content = response.path("choices").path(0).path("message").path("content");
        if (content.isTextual()) return content.asText();
        if (content.isArray()) {
            List<String> parts = new ArrayList<>();
            for (JsonNode item : content) {
                if (item.path("text").isTextual()) parts.add(item.path("text").asText());
            }
            if (!parts.isEmpty()) return String.join("\n", parts);
        }
        throw new LlmUnavailableException("LLM relay response has no message content");
    }

    private String extractJsonObject(String content) {
        String trimmed = content == null ? "" : content.trim();
        int first = trimmed.indexOf('{');
        int last = trimmed.lastIndexOf('}');
        if (first < 0 || last < first) {
            throw new LlmUnavailableException("LLM response does not contain a JSON object");
        }
        return trimmed.substring(first, last + 1);
    }

    private String encodeImage(ImageAttachment attachment) {
        if (attachment.bytes() == null || attachment.bytes().length == 0
                || attachment.bytes().length > 10_000_000) return null;
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(attachment.bytes()));
            if (source == null) return null;
            int maxDimension = Math.max(256, attachment.maxDimension());
            double scale = Math.min(1.0, maxDimension / (double) Math.max(source.getWidth(), source.getHeight()));
            int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
            BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = output.createGraphics();
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR
            );
            graphics.drawImage(source, 0, 0, width, height, null);
            graphics.dispose();

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
            if (!writers.hasNext()) return null;
            ImageWriter writer = writers.next();
            try (ImageOutputStream imageOutput = ImageIO.createImageOutputStream(bytes)) {
                writer.setOutput(imageOutput);
                ImageWriteParam parameters = writer.getDefaultWriteParam();
                if (parameters.canWriteCompressed()) {
                    parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    parameters.setCompressionQuality(0.84f);
                }
                writer.write(null, new IIOImage(output, null, null), parameters);
            } finally {
                writer.dispose();
            }
            return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    public record ImageAttachment(String label, byte[] bytes, int maxDimension) {
    }

    public record GenerationResult(JsonNode report, JsonNode rawResponse, String model) {
    }

    public static class LlmUnavailableException extends RuntimeException {
        public LlmUnavailableException(String message) {
            super(message);
        }

        public LlmUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
