package com.wsi.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wsi.platform.common.Result;
import com.wsi.platform.entity.AuditLog;
import com.wsi.platform.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * GET /api/audit-logs
 *   ?slideId=  (optional)
 *   ?userId=   (optional)
 *   ?action=   (optional) SAVE|SUBMIT|RESUBMIT|APPROVE|REJECT|IMPORT
 *   ?from=     (optional) yyyy-MM-dd HH:mm:ss
 *   ?to=       (optional) yyyy-MM-dd HH:mm:ss
 *   ?page=0    (0-based)
 *   ?size=20
 */
@RestController
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogMapper auditLogMapper;

    @GetMapping("/api/audit-logs")
    public Result<Page<AuditLog>> list(
            @RequestParam(required = false) Long slideId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<AuditLog>()
                .eq(slideId != null, AuditLog::getSlideId, slideId)
                .eq(userId  != null, AuditLog::getUserId,  userId)
                .eq(action  != null, AuditLog::getAction,  action)
                .ge(from    != null, AuditLog::getCreatedAt, from)
                .le(to      != null, AuditLog::getCreatedAt, to)
                .orderByDesc(AuditLog::getCreatedAt);

        Page<AuditLog> result = auditLogMapper.selectPage(new Page<>(page + 1, size), wrapper);
        return Result.success(result);
    }
}
