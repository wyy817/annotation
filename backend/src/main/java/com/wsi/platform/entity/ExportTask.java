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
     * Task status: PENDING | PROCESSING | DONE | ERROR
     */
    private String status;

    /** Absolute path to the generated ZIP file */
    private String zipPath;

    private Integer total;
    private Integer ready;
    private Integer errorCount;
    private Integer skipped;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
