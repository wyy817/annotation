package com.wsi.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("slides")
public class Slide {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workspaceId;

    private String fileName;

    /** Absolute path to the original uploaded file */
    private String originalPath;

    /** Directory containing DeepZoom tiles */
    private String tileDir;

    /** Path to the generated thumbnail image */
    private String thumbnailPath;

    /** File format: PNG, JPG, SVS */
    private String format;

    private Long fileSize;

    private Integer width;

    private Integer height;

    private Integer tileSize;

    private Integer maxZoom;

    /**
     * Processing status of the slide (tile generation).
     * Values: PENDING | PROCESSING | READY | ERROR
     */
    private String status;

    /**
     * Annotation status.
     * Values: NOT_STARTED | IN_PROGRESS | DONE
     */
    private String annoStatus;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
