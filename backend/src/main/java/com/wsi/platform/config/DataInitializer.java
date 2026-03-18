package com.wsi.platform.config;

import com.wsi.platform.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Creates default test accounts on startup if they don't already exist.
 *   reviewer  / review123  (REVIEWER)
 *   annotator / anno123    (ANNOTATOR)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserService userService;

    @Override
    public void run(String... args) {
        userService.createIfAbsent("reviewer",  "review123", "REVIEWER");
        userService.createIfAbsent("annotator", "anno123",   "ANNOTATOR");
        log.info("Default accounts ready: reviewer / annotator");
    }
}
