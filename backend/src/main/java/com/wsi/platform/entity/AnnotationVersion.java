package com.wsi.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.wsi.platform.common.StringJsonbTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("annotation_versions")
public class AnnotationVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long slideId;

    private Integer versionNumber;

    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * Trigger event: SUBMIT | RESUBMIT | REJECT | APPROVE
     */
    private String trigger;

    /**
     * Full snapshot of all annotations at the time of this version.
     * Stored as JSONB — serialized as JSON string.
     */
    @TableField(typeHandler = StringJsonbTypeHandler.class)
    private String annotationsSnapshot;

    /**
     * Full snapshot of all labels at the time of this version.
     * Stored as JSONB — serialized as JSON string.
     */
    @TableField(typeHandler = StringJsonbTypeHandler.class)
    private String labelsSnapshot;

    private String reviewerComment;
}
