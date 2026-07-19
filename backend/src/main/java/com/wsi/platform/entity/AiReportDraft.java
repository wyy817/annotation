package com.wsi.platform.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wsi.platform.common.StringJsonbTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "ai_report_drafts", autoResultMap = true)
public class AiReportDraft {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long analysisRunId;
    private String caseId;
    private String status;
    private String language;
    private String clinicianDecision;
    private String clinicianNote;
    private String seniorDecision;
    private String seniorNote;
    private Boolean stale;

    @TableField(typeHandler = StringJsonbTypeHandler.class)
    private String requestPayload;

    @TableField(typeHandler = StringJsonbTypeHandler.class)
    private String responsePayload;

    @TableField(typeHandler = StringJsonbTypeHandler.class)
    private String reportPayload;

    private String generatorSource;
    private String modelName;

    @TableField(typeHandler = StringJsonbTypeHandler.class)
    private String validationErrors;

    private String errorMessage;
    private Long requestedBy;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
