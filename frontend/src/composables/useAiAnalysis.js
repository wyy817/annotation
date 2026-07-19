import { onScopeDispose, ref } from 'vue'
import {
  getAiAnalysis,
  getAiArtifact,
  getLatestCaseAiAnalysis,
  startCaseAiAnalysis,
} from '@/api/aiAnalysis'

const ACTIVE_STATUSES = new Set(['QUEUED', 'RUNNING'])

export function useAiAnalysis() {
  const analysis = ref(null)
  const loading = ref(false)
  const starting = ref(false)
  const error = ref(null)
  const artifactError = ref(null)
  const artifactsLoading = ref(false)
  const artifactUrls = ref({})

  let pollTimer = null
  let requestVersion = 0

  function clearPoll() {
    if (pollTimer) window.clearTimeout(pollTimer)
    pollTimer = null
  }

  function clearArtifacts() {
    Object.values(artifactUrls.value).forEach((url) => {
      if (String(url).startsWith('blob:')) URL.revokeObjectURL(url)
    })
    artifactUrls.value = {}
    artifactError.value = null
    artifactsLoading.value = false
  }

  async function loadArtifacts(item, version) {
    clearArtifacts()
    if (!item?.artifactUrls || item.status !== 'SUCCEEDED') return

    if (item.isPrecomputed) {
      artifactUrls.value = { ...item.artifactUrls }
      return
    }

    const entries = Object.entries(item.artifactUrls)
    artifactsLoading.value = true
    const resolved = await Promise.all(entries.map(async ([key, path]) => {
      try {
        const blob = await getAiArtifact(path)
        return [key, URL.createObjectURL(blob)]
      } catch (cause) {
        return [key, null, cause]
      }
    }))
    if (version !== requestVersion) {
      resolved.forEach(([, url]) => {
        if (url) URL.revokeObjectURL(url)
      })
      return
    }
    artifactUrls.value = Object.fromEntries(
      resolved.filter(([, url]) => url).map(([key, url]) => [key, url]),
    )
    artifactError.value = resolved.find(([, url]) => !url)?.[2] || null
    artifactsLoading.value = false
  }

  function schedulePoll() {
    clearPoll()
    if (!ACTIVE_STATUSES.has(analysis.value?.status)) return
    pollTimer = window.setTimeout(() => refresh(), 2000)
  }

  async function applyResponse(response, version) {
    if (version !== requestVersion) return
    analysis.value = response?.data || null
    if (analysis.value?.status === 'SUCCEEDED') {
      await loadArtifacts(analysis.value, version)
    }
    schedulePoll()
  }

  async function loadLatest(caseReference) {
    clearPoll()
    clearArtifacts()
    const version = ++requestVersion
    loading.value = true
    error.value = null
    analysis.value = null
    try {
      const response = await getLatestCaseAiAnalysis(caseReference)
      await applyResponse(response, version)
    } catch (cause) {
      if (version === requestVersion) error.value = cause
    } finally {
      if (version === requestVersion) loading.value = false
    }
  }

  function loadPrecomputed(item) {
    clearPoll()
    clearArtifacts()
    requestVersion += 1
    loading.value = false
    starting.value = false
    error.value = null
    analysis.value = item
    if (item) artifactUrls.value = { ...item.artifactUrls }
  }

  function reset() {
    clearPoll()
    clearArtifacts()
    requestVersion += 1
    analysis.value = null
    loading.value = false
    starting.value = false
    error.value = null
  }

  async function refresh() {
    if (!analysis.value?.id) return
    const version = requestVersion
    try {
      const response = await getAiAnalysis(analysis.value.id)
      await applyResponse(response, version)
    } catch (cause) {
      if (version === requestVersion) error.value = cause
      schedulePoll()
    }
  }

  async function start(caseReference) {
    clearPoll()
    const version = ++requestVersion
    starting.value = true
    error.value = null
    try {
      const response = await startCaseAiAnalysis(caseReference)
      await applyResponse(response, version)
    } catch (cause) {
      if (version === requestVersion) error.value = cause
    } finally {
      if (version === requestVersion) starting.value = false
    }
  }

  onScopeDispose(() => {
    requestVersion += 1
    clearPoll()
    clearArtifacts()
  })

  return {
    analysis,
    artifactUrls,
    loading,
    starting,
    error,
    artifactError,
    artifactsLoading,
    loadLatest,
    loadPrecomputed,
    refresh,
    reset,
    start,
  }
}
