package com.wsi.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("clinical_case_decisions")
public class ClinicalCaseDecision {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long caseId;
    private String decisionStage;
    private String decisionType;
    private String note;
    private Long actorId;
    private String actorName;
    private String actorRole;
    private LocalDateTime createdAt;
}
