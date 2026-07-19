import http from './index'

export function getLatestAiAnalysis(slideId) {
  return http.get(`/slides/${slideId}/ai-analyses/latest`)
}

export function getLatestCaseAiAnalysis(caseReference) {
  return http.get(`/cases/${caseReference}/ai-analysis/latest`)
}

export function getAiAnalysis(analysisId) {
  return http.get(`/ai-analyses/${analysisId}`)
}

export function startAiAnalysis(slideId, payload = {}) {
  return http.post(`/slides/${slideId}/ai-analyses`, payload)
}

export function startCaseAiAnalysis(caseReference) {
  return http.post(`/cases/${caseReference}/ai-analysis`)
}

export function getAiArtifact(relativeUrl) {
  const path = relativeUrl.replace(/^\/api/, '')
  return http.get(path, { responseType: 'blob' })
}
