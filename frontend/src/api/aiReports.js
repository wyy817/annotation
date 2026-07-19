import http from './index'

export function getLatestAiReport(analysisId, caseId) {
  return http.get(`/ai-analyses/${analysisId}/reports/latest`, { params: { caseId } })
}

export function getAiReport(reportId) {
  return http.get(`/ai-reports/${reportId}`)
}

export function startAiReport(analysisId, payload) {
  return http.post(`/ai-analyses/${analysisId}/reports`, payload)
}
