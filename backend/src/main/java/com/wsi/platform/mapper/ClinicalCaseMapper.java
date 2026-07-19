package com.wsi.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wsi.platform.entity.ClinicalCase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ClinicalCaseMapper extends BaseMapper<ClinicalCase> {
    @Select("SELECT nextval('clinical_case_reference_seq')")
    long nextReferenceSequence();
}
