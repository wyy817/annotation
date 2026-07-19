package com.wsi.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("clinical_case_review_triggers")
public class ClinicalCaseReviewTrigger {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long caseId;
    private String triggerCode;
    private LocalDateTime createdAt;
}
