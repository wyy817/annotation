import http from './index'

export const caseApi = {
  list: () => http.get('/cases'),
  get: (reference) => http.get(`/cases/${reference}`),
  creationOptions: () => http.get('/cases/creation/options'),
  create: (payload) => http.post('/cases', payload),
  startAnalysis: (reference) => http.post(`/cases/${reference}/ai-analysis`),
  latestAnalysis: (reference) => http.get(`/cases/${reference}/ai-analysis/latest`),
  startReview: (reference) => http.post(`/cases/${reference}/review/start`),
  recordDecision: (reference, payload) => http.post(`/cases/${reference}/decision`, payload),
  recordSeniorReview: (reference, payload) => http.post(`/cases/${reference}/senior-review`, payload),
  generateReport: (reference, payload) => http.post(`/cases/${reference}/report`, payload),
  validateFeedback: (reference) => http.post(`/cases/${reference}/feedback/validate`),
}
