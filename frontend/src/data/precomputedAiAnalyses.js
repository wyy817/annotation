const clinicalNotice = 'Research prototype only. Model evidence and candidate areas are not calibrated clinical probabilities or tumor segmentation. A pathologist must make the final decision.'

function assetUrl(slideKey, fileName) {
  return `${import.meta.env.BASE_URL}demo-ai/${slideKey}/${fileName}`
}

function positiveCandidates() {
  return [
    ['R01', 19, 0.073584, 0.991112, 0.999986, 0.961648, 0.970258, [70016, 131904, 72320, 134464], [71195, 133352]],
    ['R02', 6, 0.023237, 0.985496, 0.999935, 0.956199, 0.970208, [72320, 123712, 73088, 124480], [72661, 124096]],
    ['R03', 68, 0.263352, 0.979465, 0.999717, 0.950347, 0.969997, [70016, 124224, 74368, 127040], [72041, 125692]],
    ['R04', 5, 0.019364, 0.972725, 0.994304, 0.943807, 0.964745, [68224, 126016, 68992, 127040], [68608, 126554]],
    ['R05', 5, 0.019364, 0.970848, 0.990065, 0.941986, 0.960632, [71808, 122688, 72832, 123456], [72294, 123021]],
  ].map((value, index) => ({
    id: index + 1,
    regionId: value[0],
    evidenceTier: 'high',
    patchCount: value[1],
    areaProxyMm2: value[2],
    meanAttentionPercentile: value[3],
    maxAttentionPercentile: value[4],
    meanEvidenceIndex: value[5],
    maxEvidenceIndex: value[6],
    bboxLevel0: value[7],
    centroidLevel0: value[8],
    evidencePatchUrl: index < 3
      ? assetUrl('camelyon16-tumor-001', `evidence_patch_0${index + 1}.png`)
      : null,
  }))
}

function artifactUrls(slideKey, includePatches = true) {
  const urls = {
    modelEvidenceOverview: assetUrl(slideKey, 'model_evidence_overview.png'),
    relativeAttentionOverview: assetUrl(slideKey, 'relative_attention_overview.png'),
  }
  if (includePatches) {
    for (let index = 1; index <= 3; index += 1) {
      urls[`evidencePatch${index}`] = assetUrl(slideKey, `evidence_patch_0${index}.png`)
    }
  }
  return urls
}

function slideSummary(fileName, patchCount) {
  return {
    fileName,
    width: 97792,
    height: 221184,
    patchCount,
    mppX: 0.243094,
    mppY: 0.243094,
  }
}

const analyses = Object.freeze({
  'camelyon16-tumor-001': Object.freeze({
    id: 'public-camelyon16-tumor-001',
    slideId: null,
    status: 'SUCCEEDED',
    stage: 'complete',
    stageProgress: 1,
    message: 'Precomputed analysis completed',
    modelName: 'CAMELYON 40x CLAM 100% s1, 10-fold ensemble',
    predictedClass: 'tumor_associated_evidence',
    tumorEvidenceScore: 0.970272,
    evidenceBand: 'high_evidence',
    ensembleDisagreement: 0.020626,
    candidateRegionCount: 5,
    highRiskRegionCount: 5,
    candidateAreaProxyMm2: 0.398901,
    candidates: positiveCandidates(),
    artifactUrls: artifactUrls('camelyon16-tumor-001'),
    slide: slideSummary('tumor_001.tif', 36710),
    validation: {
      available: true,
      highAttentionOverlapsAnnotation: true,
      candidateRegionsOverlapAnnotation: true,
      attentionPrecisionProxy: 0.565925,
      annotationCoverageProxy: 0.438329,
      candidatePrecisionProxy: 0.773058,
      notice: 'Compatibility check against the public CAMELYON annotation; not a segmentation or clinical performance metric.',
    },
    runtime: { device: 'cuda', totalSeconds: 10.317283 },
    completedAt: '2026-07-18T09:32:39.290360Z',
    clinicalNotice,
    isPrecomputed: true,
  }),
  'camelyon16-normal-001': Object.freeze({
    id: 'public-camelyon16-normal-001',
    slideId: null,
    status: 'SUCCEEDED',
    stage: 'complete',
    stageProgress: 1,
    message: 'Precomputed analysis completed',
    modelName: 'CAMELYON 40x CLAM 100% s1, 10-fold ensemble',
    predictedClass: 'no_tumor_associated_evidence',
    tumorEvidenceScore: 0.03211,
    evidenceBand: 'low_evidence',
    ensembleDisagreement: 0.016621,
    candidateRegionCount: 0,
    highRiskRegionCount: 0,
    candidateAreaProxyMm2: 0,
    candidates: [],
    artifactUrls: artifactUrls('camelyon16-normal-001', false),
    slide: slideSummary('normal_001.tif', 7974),
    validation: { available: false },
    runtime: { device: 'cuda', totalSeconds: 10.154263 },
    completedAt: '2026-07-18T09:30:41.013771Z',
    clinicalNotice,
    isPrecomputed: true,
  }),
})

export function getPrecomputedAnalysis(sourceKey) {
  return analyses[sourceKey] || null
}
