package com.wsi.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("workspaces")
public class Workspace {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    /** Absolute path to the workspace directory on disk */
    private String dirPath;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
