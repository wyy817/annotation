export const BREAST_LYMPH_NODE_HE = Object.freeze({
  id: 'breast-lymph-node-he',
  version: '1.0.0-demo',
  nameKey: 'po.disease.breastLymphNode',
  specimenKey: 'po.disease.lymphNodeSpecimen',
  stain: 'H&E',
  task: 'metastasis-screening',
  riskThresholds: {
    high: 0.8,
    intermediate: 0.4,
  },
  confidenceThreshold: 0.72,
  annotationLabels: [
    'metastatic-focus',
    'isolated-tumor-cells',
    'lymphoid-tissue',
    'artifact',
  ],
  evidenceLayers: [
    'metastasis-likelihood',
    'focus-boundary',
    'quality-mask',
  ],
})

export const DISEASE_PROFILES = Object.freeze({
  [BREAST_LYMPH_NODE_HE.id]: BREAST_LYMPH_NODE_HE,
})

export function getDiseaseProfile(profileId) {
  return DISEASE_PROFILES[profileId] || BREAST_LYMPH_NODE_HE
}
