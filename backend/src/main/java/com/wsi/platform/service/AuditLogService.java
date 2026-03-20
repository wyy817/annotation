package com.wsi.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wsi.platform.entity.AuditLog;
import com.wsi.platform.mapper.AuditLogMapper;
import com.wsi.platform.security.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    /**
     * Record an audit event.
     *
     * @param action  SAVE | SUBMIT | RESUBMIT | APPROVE | REJECT | IMPORT
     * @param slideId the slide this event is about
     * @param detail  optional key-value pairs stored as JSONB
     */
    public void log(String action, Long slideId, Map<String, Object> detail) {
        AuditLog entry = new AuditLog();

        UserContext.UserInfo user = UserContext.get();
        if (user != null) {
            entry.setUserId(user.id());
            entry.setUsername(user.username());
        }

        entry.setAction(action);
        entry.setSlideId(slideId);
        entry.setCreatedAt(LocalDateTime.now());

        if (detail != null && !detail.isEmpty()) {
            try {
                entry.setDetail(objectMapper.writeValueAsString(detail));
            } catch (Exception e) {
                log.warn("AuditLogService: failed to serialize detail for action={}", action, e);
            }
        }

        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest req = attrs.getRequest();
                String ip = req.getHeader("X-Forwarded-For");
                if (ip == null || ip.isBlank()) ip = req.getRemoteAddr();
                entry.setIpAddress(ip);
            }
        } catch (Exception ignored) {}

        try {
            auditLogMapper.insert(entry);
        } catch (Exception e) {
            // Audit failures must never break business logic
            log.error("AuditLogService: failed to insert audit log action={} slideId={}", action, slideId, e);
        }
    }
}
