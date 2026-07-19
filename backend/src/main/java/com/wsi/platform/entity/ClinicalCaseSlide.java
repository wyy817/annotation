package com.wsi.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("clinical_case_slides")
public class ClinicalCaseSlide {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long caseId;
    private Long slideId;
    private Boolean isPrimary;
    private LocalDateTime createdAt;
}
