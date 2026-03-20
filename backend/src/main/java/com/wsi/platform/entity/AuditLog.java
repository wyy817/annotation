package com.wsi.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wsi.platform.common.StringJsonbTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audit_logs")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String username;

    /** SAVE | SUBMIT | RESUBMIT | APPROVE | REJECT | IMPORT */
    private String action;

    private Long slideId;

    @TableField(typeHandler = StringJsonbTypeHandler.class)
    private String detail;

    private String ipAddress;
    private LocalDateTime createdAt;
}
