package com.wsi.platform.config;

import com.wsi.platform.service.UserService;
import com.wsi.platform.service.ClinicalCaseService;
import com.wsi.platform.entity.Slide;
import com.wsi.platform.entity.User;
import com.wsi.platform.mapper.SlideMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
    private final SlideMapper slideMapper;
    private final ClinicalCaseService caseService;

    @Override
    public void run(String... args) {
        userService.createIfAbsent(
                "reviewer", "review123", "REVIEWER", "Dr. Elena Ortiz");
        User pathologist = userService.createIfAbsent(
                "annotator", "anno123", "ANNOTATOR", "Dr. Maya Lin");
        userService.createIfAbsent(
                "aiadmin", "data123", "AI_DATA_ADMIN", "Jordan Kim");
        userService.createIfAbsent(
                "sysadmin", "system123", "SYSTEM_ADMIN", "Alex Morgan");
        seedCase("tumor_001.tif", "PO-26-0717-001", "DEMO-AX-00417",
                "SLN-A1", 58, pathologist.getId());
        seedCase("normal_001.tif", "PO-26-0717-006", "DEMO-AX-00435",
                "SLN-F1", 62, pathologist.getId());
        log.info("Default accounts and available CAMELYON cases are ready");
    }

    private void seedCase(String fileName, String reference, String accession,
                          String specimen, int patientAge, Long pathologistId) {
        Slide slide = slideMapper.selectOne(new LambdaQueryWrapper<Slide>()
                .eq(Slide::getFileName, fileName)
                .eq(Slide::getStatus, "READY")
                .orderByAsc(Slide::getId)
                .last("LIMIT 1"));
        if (slide == null) return;
        try {
            caseService.createSeedIfAbsent(
                    reference, accession, specimen, patientAge, slide.getId(), pathologistId,
                    java.time.LocalDateTime.of(2026, 7, 17, 7, 12),
                    java.time.LocalDateTime.of(2026, 7, 17, 13, 10));
        } catch (IllegalStateException ignored) {
            log.info("Slide {} is already bound to a case", fileName);
        }
    }
}
