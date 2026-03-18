package com.wsi.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.wsi.platform.common.StringJsonbTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("annotations")
public class Annotation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long slideId;

    private Long labelId;

    /**
     * Annotation type: polygon | rect | brush
     */
    private String annoType;

    /**
     * Vector coordinate data stored as JSON string.
     * Maps to PostgreSQL JSONB column.
     * For polygon/rect: [[x1,y1],[x2,y2],...]
     * For brush: [{"points":[[x,y],...],"size":20},...]
     */
    @TableField(typeHandler = StringJsonbTypeHandler.class)
    private String coordinates;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
