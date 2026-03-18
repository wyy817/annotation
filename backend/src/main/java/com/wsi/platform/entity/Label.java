package com.wsi.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("labels")
public class Label {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long slideId;

    private String name;

    /** Hex color string, e.g. "#FF0000" */
    private String color;

    /** Integer class index used in semantic mask encoding (1, 2, 3, ...) */
    private Integer classIndex;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
