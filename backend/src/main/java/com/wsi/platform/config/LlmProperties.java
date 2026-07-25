package com.wsi.platform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "parvisight.llm")
public class LlmProperties {

    private boolean enabled = false;
    private String baseUrl = "";
    private String apiKey = "";
    private String model = "gpt5.6";
    private int connectTimeoutSeconds = 10;
    private int readTimeoutSeconds = 75;

    public boolean isConfigured() {
        return enabled
                && baseUrl != null && !baseUrl.isBlank()
                && apiKey != null && !apiKey.isBlank()
                && model != null && !model.isBlank();
    }
}
