package com.wsi.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("export_tasks")
public class ExportTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long slideId;

    /**
     * Export format: COCO | YOLO | VOC | GEOJSON | MASK_ONLY | PATCHES
     */
    private String format;

    /**
     * Task status: PENDING | PROCESSING | DONE | ERROR
     */
    private String status;

    /** Absolute path to the generated ZIP file */
    private String zipPath;

    /** Patch size for PATCHES format (128/256/512), default 256 */
    private Integer patchSize;

    /** Mask type for MASK_ONLY format: SEMANTIC | COLOR | INSTANCE */
    private String maskType;

    /** Mask downsample scale for MASK_ONLY format (1/2/4/8), default 1 */
    private Integer maskScale;

    private Integer total;
    private Integer ready;
    private Integer errorCount;
    private Integer skipped;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
