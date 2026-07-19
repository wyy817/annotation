package com.wsi.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("clinical_case_audit_events")
public class ClinicalCaseAuditEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long caseId;
    private String eventType;
    private Long actorId;
    private String actorName;
    private String detailKey;
    private LocalDateTime createdAt;
}
