package com.wsi.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("users")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String displayName;

    @JsonIgnore
    private String passwordHash;

    /** ANNOTATOR | REVIEWER | AI_DATA_ADMIN | SYSTEM_ADMIN */
    private String role;

    /** PENDING | ACTIVE | DISABLED */
    private String status;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
