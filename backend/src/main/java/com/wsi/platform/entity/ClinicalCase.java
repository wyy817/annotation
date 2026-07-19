package com.wsi.platform.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("clinical_cases")
public class ClinicalCase {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String caseReference;
    private String accessionReference;
    private String diseaseProfileId;
    private String specimenName;
    private String specimenSiteKey;
    private String stain;
    private Integer patientAge;
    private LocalDateTime receivedAt;
    private LocalDateTime dueAt;
    private Long assignedPathologistId;
    private String status;
    private Boolean reviewRequired;
    private Boolean qcSample;
    private String feedbackStatus;
    private Integer feedbackVersion;
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
