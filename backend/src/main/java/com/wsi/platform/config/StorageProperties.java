package com.wsi.platform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the wsi.* section of application.yml.
 * Registered via @EnableConfigurationProperties in AppConfig.
 */
@Data
@ConfigurationProperties(prefix = "wsi")
public class StorageProperties {

    private Storage storage = new Storage();
    private PythonService pythonService = new PythonService();

    @Data
    public static class Storage {
        private String uploadDir = "./data/uploads";
        private String tileDir   = "./data/tiles";
        private String exportDir = "./data/exports";
    }

    @Data
    public static class PythonService {
        private String baseUrl = "http://localhost:8001";
    }
}
