package com.wsi.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wsi.platform.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
