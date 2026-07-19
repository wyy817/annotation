import { onScopeDispose, ref } from 'vue'
import { getAiReport, getLatestAiReport, startAiReport } from '@/api/aiReports'
import { caseApi } from '@/api/cases'

const ACTIVE_STATUSES = new Set(['QUEUED', 'RUNNING'])

export function useAiReport() {
  const report = ref(null)
  const loading = ref(false)
  const starting = ref(false)
  const error = ref(null)

  let pollTimer = null
  let requestVersion = 0

  function clearPoll() {
    if (pollTimer) window.clearTimeout(pollTimer)
    pollTimer = null
  }

  function schedulePoll() {
    clearPoll()
    if (!ACTIVE_STATUSES.has(report.value?.status)) return
    pollTimer = window.setTimeout(() => refresh(), 2000)
  }

  function applyResponse(response, version) {
    if (version !== requestVersion) return
    report.value = response?.data || null
    schedulePoll()
  }

  async function loadLatest(analysisId, caseId) {
    clearPoll()
    const version = ++requestVersion
    loading.value = true
    error.value = null
    report.value = null
    try {
      const response = await getLatestAiReport(analysisId, caseId)
      applyResponse(response, version)
    } catch (cause) {
      if (version === requestVersion) error.value = cause
    } finally {
      if (version === requestVersion) loading.value = false
    }
  }

  async function refresh() {
    if (!report.value?.id || report.value?.isPrecomputed) return
    const version = requestVersion
    try {
      const response = await getAiReport(report.value.id)
      applyResponse(response, version)
    } catch (cause) {
      if (version === requestVersion) error.value = cause
      schedulePoll()
    }
  }

  async function start(analysisId, payload) {
    clearPoll()
    const version = ++requestVersion
    starting.value = true
    error.value = null
    try {
      const response = await startAiReport(analysisId, payload)
      applyResponse(response, version)
    } catch (cause) {
      if (version === requestVersion) error.value = cause
    } finally {
      if (version === requestVersion) starting.value = false
    }
  }

  async function startForCase(caseReference, payload = {}) {
    clearPoll()
    const version = ++requestVersion
    starting.value = true
    error.value = null
    try {
      const response = await caseApi.generateReport(caseReference, payload)
      applyResponse(response, version)
    } catch (cause) {
      if (version === requestVersion) error.value = cause
    } finally {
      if (version === requestVersion) starting.value = false
    }
  }

  function loadPrecomputed(item) {
    clearPoll()
    requestVersion += 1
    report.value = item
    loading.value = false
    starting.value = false
    error.value = null
  }

  function reset() {
    clearPoll()
    requestVersion += 1
    report.value = null
    loading.value = false
    starting.value = false
    error.value = null
  }

  onScopeDispose(() => {
    requestVersion += 1
    clearPoll()
  })

  return {
    report,
    loading,
    starting,
    error,
    loadLatest,
    loadPrecomputed,
    refresh,
    reset,
    start,
    startForCase,
  }
}
