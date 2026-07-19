package com.wsi.platform.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wsi.platform.common.StringJsonbTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "ai_candidate_regions", autoResultMap = true)
public class AiCandidateRegion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long analysisRunId;

    private String regionKey;

    private String evidenceTier;

    private Integer patchCount;

    private Double areaProxyMm2;

    private Double meanAttentionPercentile;

    private Double maxAttentionPercentile;

    private Double meanEvidenceIndex;

    private Double maxEvidenceIndex;

    @TableField(typeHandler = StringJsonbTypeHandler.class)
    private String bboxLevel0;

    @TableField(typeHandler = StringJsonbTypeHandler.class)
    private String centroidLevel0;

    private String representativePatchPath;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
