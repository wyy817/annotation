package com.wsi.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wsi.platform.entity.ClinicalCaseAuditEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ClinicalCaseAuditEventMapper extends BaseMapper<ClinicalCaseAuditEvent> {
}
