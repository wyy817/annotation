function reportContent(analysis, decision, language) {
  const chinese = String(language).toLowerCase().startsWith('zh')
  const positive = analysis.tumorEvidenceScore >= 0.5
  const count = analysis.candidateRegionCount || 0
  const score = Math.round(analysis.tumorEvidenceScore * 1000) / 10

  if (chinese) {
    if (positive && decision.type === 'accept') {
      return {
        impression: 'H&E 模型证据及经医生复核的候选区域支持淋巴结受累，疑似转移性癌。该内容为辅助草稿，必须由病理医生完成最终报告。',
        microscopic_description: '代表性高注意力区域可见成团的异型大细胞，细胞核增大并呈不同程度多形性。医生已在来源全切片上复核 5 个高模型证据候选区域。',
        key_findings: [
          '切片级肿瘤相关模型证据较高（0.970272），集成折间分歧较低（0.020626）。',
          '共识别 5 个候选区域，均属于高模型证据层级。',
          '候选区域与可用的公开标注存在重叠。',
          '医生在来源 WSI 上复核候选区域后接受模型结果。',
        ],
        clinicopathologic_concordance: 'concordant',
        draft_assessment: '现有证据与乳腺癌淋巴结标本受癌累及相符。沉积灶大小、淋巴结分级、结外扩展、肿瘤亚型及分期无法由当前证据确定，不应推断。',
        suggested_review_steps: [
          '由病理医生在完整来源 WSI 上确认肿瘤诊断及分布。',
          '如正式报告需要，应在来源切片上直接测量最大连续肿瘤沉积灶并完成淋巴结分类。',
          '在来源 WSI 上评估是否存在结外扩展。',
        ],
        limitations: [
          'CLAM 注意力属于研究用途的证据，不是校准后的病变概率。',
          '注意力图和候选区域不构成肿瘤分割。',
          '0.398901 mm² 的候选面积近似值不得作为肿瘤大小报告。',
          '现有证据不能确定肿瘤亚型、淋巴结分期、结外扩展或辅助检查结果。',
          '必须由病理医生复核完整切片并完成最终报告。',
        ],
        report_status: 'DRAFT',
        clinician_review_required: true,
      }
    }
    return {
      impression: positive
        ? '全切片模型证据升高，需结合医生对候选区域的复核结论形成最终判断。'
        : '全切片模型证据较低，当前门控下未形成可信候选病灶。',
      microscopic_description: `预计算 CLAM 集成模型证据为 ${score}%，识别 ${count} 个候选区域。`,
      key_findings: [
        `切片级模型证据：${score}%。`,
        `候选区域数量：${count}。`,
        `医生记录的处理决定：${decision.type}。`,
      ],
      clinicopathologic_concordance: decision.type === 'reject' ? 'discordant' : 'concordant',
      draft_assessment: '医生记录的处理决定优先于模型证据；该内容仅作为结构化辅助草稿。',
      suggested_review_steps: [
        '在原始 WSI 上复核候选区域及其邻近组织。',
        '由病理医生审核并完成正式报告。',
      ],
      limitations: [
        'CLAM 注意力属于研究用途的模型证据，不是校准后的病变概率或肿瘤分割。',
        '候选面积为近似值，本草稿不得替代医生诊断。',
      ],
      report_status: 'DRAFT',
      clinician_review_required: true,
    }
  }

  if (positive && decision.type === 'accept') {
    return {
      impression: 'H&E model evidence and the reviewed candidate regions support tumor involvement of the lymph-node specimen, suspicious for metastatic carcinoma. This is an assistance draft and must be finalized by a pathologist.',
      microscopic_description: 'Representative high-attention regions show cohesive aggregates of atypical large cells with enlarged, variably pleomorphic nuclei on H&E. Five high-evidence candidate regions were reviewed on the source whole-slide image by the clinician.',
      key_findings: [
        'High slide-level tumor-associated evidence (0.970272) with low ensemble disagreement (0.020626).',
        'Five candidate regions were identified, all within the high-evidence tier.',
        'Candidate regions overlap the available public annotation.',
        'The clinician accepted the model output after reviewing the candidate regions on the source WSI.',
      ],
      clinicopathologic_concordance: 'concordant',
      draft_assessment: 'Findings are consistent with lymph-node involvement by carcinoma in this breast cancer lymph-node specimen. Deposit size, nodal category, extranodal extension, tumor subtype, and stage cannot be determined from the supplied evidence and should not be inferred.',
      suggested_review_steps: [
        'Confirm the diagnosis and tumor distribution on the complete source WSI.',
        'If required, measure the largest contiguous tumor deposit directly on the source slide and classify the nodal deposit.',
        'Assess for extranodal extension on the source WSI.',
      ],
      limitations: [
        'CLAM attention is research-use evidence and is not a calibrated lesion probability.',
        'Attention maps and candidate regions are not tumor segmentation.',
        'The 0.398901 mm² candidate area proxy is approximate and must not be reported as tumor size.',
        'The supplied evidence does not establish tumor subtype, nodal stage, extranodal extension, or ancillary-study results.',
        'A pathologist must review the complete slide and finalize the report.',
      ],
      report_status: 'DRAFT',
      clinician_review_required: true,
    }
  }

  return {
    impression: positive
      ? 'Elevated whole-slide model evidence requires correlation with the clinician-reviewed candidate regions.'
      : 'Whole-slide model evidence is low and no convincing candidate focus passed the configured gate.',
    microscopic_description: `The precomputed CLAM ensemble produced ${score}% slide-level model evidence and ${count} candidate region(s).`,
    key_findings: [
      `Slide-level model evidence: ${score}%.`,
      `Candidate region count: ${count}.`,
      `Recorded clinician decision: ${decision.type}.`,
    ],
    clinicopathologic_concordance: decision.type === 'reject' ? 'discordant' : 'concordant',
    draft_assessment: 'The recorded clinician decision takes precedence over model evidence; this content is a structured assistance draft only.',
    suggested_review_steps: [
      'Review candidate regions and adjacent tissue on the source WSI.',
      'Have a pathologist review and finalize the diagnostic report.',
    ],
    limitations: [
      'CLAM attention is research-use model evidence, not calibrated lesion probability or tumor segmentation.',
      'Candidate area is approximate and this draft cannot replace pathologist diagnosis.',
    ],
    report_status: 'DRAFT',
    clinician_review_required: true,
  }
}

export function createPrecomputedReport(analysis, caseItem, language) {
  const decision = caseItem?.workflow?.decision
  if (!analysis || !decision) return null
  return {
    id: `precomputed-${analysis.id}-${decision.at}`,
    analysisId: analysis.id,
    caseId: caseItem.id,
    status: 'SUCCEEDED',
    language: String(language).toLowerCase().startsWith('zh') ? 'zh-CN' : 'en',
    clinicianDecision: decision.type,
    clinicianNote: decision.note || '',
    generatorSource: 'PRECOMPUTED_DEMO',
    modelName: analysis.tumorEvidenceScore >= 0.5 && decision.type === 'accept'
      ? 'gpt-5.6-sol / precomputed'
      : 'Pathorchestra structured draft v1',
    report: reportContent(analysis, decision, language),
    validationErrors: [],
    errorMessage: null,
    completedAt: '2026-07-18T10:00:00Z',
    createdAt: '2026-07-18T10:00:00Z',
    clinicalNotice: 'Precomputed demonstration draft only. Pathologist review is required.',
    isPrecomputed: true,
  }
}
