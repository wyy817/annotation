package com.wsi.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import com.wsi.platform.config.LlmProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class LlmReportClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void recordsModelReportedByRelayInsteadOfRequestedAlias() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicReference<JsonNode> capturedRequest = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            capturedRequest.set(objectMapper.readTree(exchange.getRequestBody()));
            ObjectNode response = objectMapper.createObjectNode();
            response.put("model", "relay-routed-gpt-5.6");
            response.putArray("choices")
                    .addObject()
                    .putObject("message")
                    .put("content", "{\"impression\":\"draft\"}");
            byte[] body = objectMapper.writeValueAsBytes(response);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        LlmProperties properties = new LlmProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setApiKey("test-key");
        properties.setModel("configured-model-alias");
        LlmReportClient client = new LlmReportClient(properties, objectMapper);

        LlmReportClient.GenerationResult result = client.generate(
                objectMapper.createObjectNode(), List.of()
        );

        assertThat(capturedRequest.get().path("model").asText())
                .isEqualTo("configured-model-alias");
        assertThat(result.model()).isEqualTo("relay-routed-gpt-5.6");
        assertThat(result.report().path("impression").asText()).isEqualTo("draft");
    }

    @Test
    void fallsBackToConfiguredAliasWhenRelayOmitsModel() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] body = "{\"choices\":[{\"message\":{\"content\":\"{\\\"impression\\\":\\\"draft\\\"}\"}}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        LlmProperties properties = new LlmProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setApiKey("test-key");
        properties.setModel("configured-model-alias");

        LlmReportClient.GenerationResult result = new LlmReportClient(properties, objectMapper)
                .generate(objectMapper.createObjectNode(), List.of());

        assertThat(result.model()).isEqualTo("configured-model-alias");
    }
}
