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
@TableName(value = "ai_analysis_runs", autoResultMap = true)
public class AiAnalysisRun {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long slideId;

    private String caseReference;

    private String pythonJobId;

    private String status;

    private String stage;

    private Double stageProgress;

    private String message;

    private String modelName;

    private String predictedClass;

    private Double tumorEvidenceScore;

    private String evidenceBand;

    private Double ensembleDisagreement;

    private Integer candidateRegionCount;

    private Integer highRiskRegionCount;

    private Double candidateAreaProxyMm2;

    @TableField(typeHandler = StringJsonbTypeHandler.class)
    private String resultPayload;

    @TableField(typeHandler = StringJsonbTypeHandler.class)
    private String artifacts;

    private Long startedBy;

    private String errorMessage;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
