import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import { caseApi } from '@/api/cases'
import { IS_DEMO_MODE } from '@/config/product'
import { createDemoCases } from '@/data/demoCases'

const STORAGE_KEY = 'pathorchestra_demo_state_v3'
const STORAGE_VERSION = 3

function loadDemoCases() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return createDemoCases()
    const parsed = JSON.parse(raw)
    return parsed.version === STORAGE_VERSION && Array.isArray(parsed.cases)
      ? parsed.cases
      : createDemoCases()
  } catch {
    return createDemoCases()
  }
}

function nextFeedbackVersion(currentVersion) {
  const value = Number.parseInt(String(currentVersion || 'v0').replace('v', ''), 10)
  return 'v' + (Number.isFinite(value) ? value + 1 : 1)
}

export const useCaseStore = defineStore('cases', () => {
  const cases = ref(IS_DEMO_MODE ? loadDemoCases() : [])
  const loading = ref(false)
  const error = ref(null)

  const highRiskCount = computed(() => cases.value.filter((item) => (item.ai.risk || 0) >= 0.8).length)
  const attentionCount = computed(() => cases.value.filter((item) => (
    ['ready', 'returned', 'senior_review'].includes(item.workflow.status)
  )).length)
  const inReviewCount = computed(() => cases.value.filter((item) => (
    ['in_review', 'senior_review'].includes(item.workflow.status)
  )).length)
  const completedCount = computed(() => cases.value.filter((item) => item.workflow.status === 'completed').length)
  const feedbackCases = computed(() => cases.value.filter((item) => (
    ['queued', 'validated'].includes(item.feedback.status)
  )))

  if (IS_DEMO_MODE) {
    watch(cases, (value) => {
      localStorage.setItem(STORAGE_KEY, JSON.stringify({ version: STORAGE_VERSION, cases: value }))
    }, { deep: true })
  }

  function getCaseById(caseId) {
    return cases.value.find((item) => item.id === caseId)
  }

  function upsert(item) {
    if (!item) return null
    const index = cases.value.findIndex((candidate) => candidate.id === item.id)
    if (index >= 0) cases.value.splice(index, 1, item)
    else cases.value.push(item)
    return item
  }

  async function loadCases() {
    if (IS_DEMO_MODE) return cases.value
    loading.value = true
    error.value = null
    try {
      const response = await caseApi.list()
      cases.value = response.data || []
      return cases.value
    } catch (cause) {
      error.value = cause
      return []
    } finally {
      loading.value = false
    }
  }

  async function loadCase(caseId) {
    if (IS_DEMO_MODE) return getCaseById(caseId)
    loading.value = true
    error.value = null
    try {
      const response = await caseApi.get(caseId)
      return upsert(response.data)
    } catch (cause) {
      error.value = cause
      return null
    } finally {
      loading.value = false
    }
  }

  async function createCase(payload) {
    const response = await caseApi.create(payload)
    return upsert(response.data)
  }

  function appendAudit(caseItem, type, actor, detailKey, at) {
    caseItem.auditTrail.push({ type, actor, detailKey, at })
    caseItem.workflow.lastUpdatedAt = at
  }

  async function startReview(caseId, actor) {
    if (!IS_DEMO_MODE) {
      const response = await caseApi.startReview(caseId)
      return upsert(response.data)
    }
    const caseItem = getCaseById(caseId)
    if (!caseItem) return null
    if (!['ready', 'returned'].includes(caseItem.workflow.status)) return caseItem
    const at = new Date().toISOString()
    caseItem.workflow.status = 'in_review'
    if (!caseItem.owner) caseItem.owner = actor.displayName
    appendAudit(caseItem, 'review_started', actor.displayName, 'po.audit.reviewStarted', at)
    return caseItem
  }

  async function recordPathologistDecision(caseId, decision, actor, language = 'en') {
    if (!IS_DEMO_MODE) {
      const response = await caseApi.recordDecision(caseId, { ...decision, language })
      const item = upsert(response.data)
      return { ok: true, status: item.workflow.status, caseItem: item }
    }
    const caseItem = getCaseById(caseId)
    if (!caseItem || !['accept', 'modify', 'reject', 'escalate'].includes(decision.type)) {
      return { ok: false, reason: 'invalid_decision' }
    }
    const at = new Date().toISOString()
    const triggers = new Set(caseItem.workflow.reviewTriggers)
    if (decision.type === 'escalate') triggers.add('explicit_escalation')
    if (decision.type === 'reject') triggers.add('image_text_discordance')
    caseItem.workflow.reviewTriggers = [...triggers]
    caseItem.workflow.reviewRequired = triggers.size > 0
    caseItem.workflow.decision = { ...decision, actor: actor.displayName, actorRole: actor.role, at }
    const detailKeys = {
      accept: 'po.audit.decisionAccepted',
      modify: 'po.audit.decisionModified',
      reject: 'po.audit.decisionRejected',
      escalate: 'po.audit.decisionEscalated',
    }
    appendAudit(caseItem, 'decision_recorded', actor.displayName, detailKeys[decision.type], at)
    if (triggers.size) {
      caseItem.workflow.status = 'senior_review'
      caseItem.workflow.seniorReview = { status: 'pending', reviewer: null, at: null, note: '' }
      caseItem.feedback = { eligible: false, status: 'awaiting_review', version: caseItem.feedback.version }
      appendAudit(caseItem, 'senior_review_queued', 'System', 'po.audit.seniorReviewQueued', at)
      return { ok: true, status: 'senior_review', caseItem }
    }
    caseItem.workflow.status = 'completed'
    caseItem.feedback = { eligible: true, status: 'queued', version: nextFeedbackVersion(caseItem.feedback.version) }
    appendAudit(caseItem, 'feedback_queued', 'System', 'po.audit.feedbackQueued', at)
    return { ok: true, status: 'completed', caseItem }
  }

  async function recordSeniorReview(caseId, review, actor, language = 'en') {
    if (!IS_DEMO_MODE) {
      const response = await caseApi.recordSeniorReview(caseId, { ...review, language })
      const item = upsert(response.data)
      return { ok: true, status: item.workflow.status, caseItem: item }
    }
    const caseItem = getCaseById(caseId)
    if (!caseItem || !['approve', 'return'].includes(review.type)) return { ok: false }
    const at = new Date().toISOString()
    caseItem.workflow.seniorReview = {
      status: review.type === 'approve' ? 'approved' : 'returned',
      reviewer: actor.displayName,
      at,
      note: review.note || '',
    }
    if (review.type === 'approve') {
      caseItem.workflow.status = 'completed'
      caseItem.feedback = { eligible: true, status: 'queued', version: nextFeedbackVersion(caseItem.feedback.version) }
      appendAudit(caseItem, 'senior_review_approved', actor.displayName, 'po.audit.seniorReviewApproved', at)
      appendAudit(caseItem, 'feedback_queued', 'System', 'po.audit.feedbackQueued', at)
      return { ok: true, status: 'completed', caseItem }
    }
    caseItem.workflow.status = 'returned'
    caseItem.feedback = { eligible: false, status: 'not_eligible', version: caseItem.feedback.version }
    appendAudit(caseItem, 'senior_review_returned', actor.displayName, 'po.audit.seniorReviewReturned', at)
    return { ok: true, status: 'returned', caseItem }
  }

  async function validateFeedback(caseId, actor) {
    if (!IS_DEMO_MODE) {
      const response = await caseApi.validateFeedback(caseId)
      return Boolean(upsert(response.data))
    }
    const caseItem = getCaseById(caseId)
    if (!caseItem || caseItem.feedback.status !== 'queued') return false
    const at = new Date().toISOString()
    caseItem.feedback.status = 'validated'
    appendAudit(caseItem, 'feedback_validated', actor.displayName, 'po.audit.feedbackValidated', at)
    return true
  }

  function resetDemoData() {
    if (!IS_DEMO_MODE) return
    cases.value = createDemoCases()
    localStorage.removeItem(STORAGE_KEY)
  }

  return {
    cases,
    loading,
    error,
    highRiskCount,
    attentionCount,
    inReviewCount,
    completedCount,
    feedbackCases,
    getCaseById,
    loadCases,
    loadCase,
    createCase,
    startReview,
    recordPathologistDecision,
    recordSeniorReview,
    validateFeedback,
    resetDemoData,
  }
})
