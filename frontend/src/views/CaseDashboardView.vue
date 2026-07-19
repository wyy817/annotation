<template>
  <ProductShell wide>
    <section class="po-queue-page">
      <header class="po-page-heading">
        <div>
          <div class="po-heading-kicker">
            <span>{{ t(scopeKey) }}</span>
            <span aria-hidden="true">/</span>
            <span>{{ t('po.mode.demoData') }}</span>
          </div>
          <h1>{{ t('po.queue.title') }}</h1>
          <p>{{ t(subtitleKey) }}</p>
        </div>

        <div class="po-heading-actions">
          <el-button
            v-if="productStore.isDemoMode"
            :icon="RefreshRight"
            @click="resetDemoData"
          >
            {{ t('po.queue.resetData') }}
          </el-button>
          <el-button
            v-if="!productStore.isDemoMode && canCreateCase"
            type="primary"
            :icon="Plus"
            @click="showCaseCreate = true"
          >
            {{ t('po.caseCreate.newCase') }}
          </el-button>
          <el-button
            v-if="!productStore.isDemoMode"
            :icon="FolderOpened"
            @click="router.push({ name: 'workspaces' })"
          >
            {{ t('po.queue.openSlideLibrary') }}
          </el-button>
        </div>
      </header>

      <div class="po-profile-line">
        <span class="po-profile-accent" aria-hidden="true"></span>
        <strong>{{ t('po.disease.breastLymphNode') }}</strong>
        <span>{{ t('po.disease.axillaryNode') }}</span>
        <span>H&amp;E</span>
        <span>{{ t('po.disease.profileVersion') }}</span>
      </div>

      <section class="po-summary-band" :aria-label="t('po.queue.title')">
        <div v-for="metric in summaryMetrics" :key="metric.labelKey" class="po-summary-item">
          <el-icon :class="metric.tone" aria-hidden="true">
            <component :is="metric.icon" />
          </el-icon>
          <div>
            <strong class="po-tabular">{{ metric.value }}</strong>
            <span>{{ t(metric.labelKey) }}</span>
          </div>
        </div>
      </section>

      <section class="po-queue-tool">
        <header class="po-queue-toolbar">
          <div class="po-search-field">
            <label class="po-sr-only" for="case-search">{{ t('po.queue.searchPlaceholder') }}</label>
            <el-input
              id="case-search"
              v-model="search"
              :placeholder="t('po.queue.searchPlaceholder')"
              :prefix-icon="Search"
              clearable
            />
          </div>

          <div class="po-filter-row">
            <label class="po-sr-only" for="risk-filter">{{ t('po.queue.riskFilter') }}</label>
            <el-select
              id="risk-filter"
              v-model="riskFilter"
              :aria-label="t('po.queue.riskFilter')"
              class="po-filter-select"
            >
              <el-option :label="t('po.queue.allRisks')" value="all" />
              <el-option :label="t('po.risk.high')" value="high" />
              <el-option :label="t('po.risk.intermediate')" value="intermediate" />
              <el-option :label="t('po.risk.low')" value="low" />
            </el-select>

            <label class="po-sr-only" for="status-filter">{{ t('po.queue.statusFilter') }}</label>
            <el-select
              id="status-filter"
              v-model="statusFilter"
              :aria-label="t('po.queue.statusFilter')"
              class="po-filter-select"
            >
              <el-option :label="t('po.queue.allStatuses')" value="all" />
              <el-option
                v-for="status in statusOptions"
                :key="status"
                :label="t('po.status.' + status)"
                :value="status"
              />
            </el-select>

            <label class="po-sr-only" for="sort-filter">{{ t('po.queue.sortLabel') }}</label>
            <el-select
              id="sort-filter"
              v-model="sortBy"
              :aria-label="t('po.queue.sortLabel')"
              class="po-sort-select"
            >
              <el-option :label="t('po.queue.sortRiskDesc')" value="risk-desc" />
              <el-option :label="t('po.queue.sortDueAsc')" value="due-asc" />
              <el-option :label="t('po.queue.sortReceivedDesc')" value="received-desc" />
            </el-select>
          </div>

          <div class="po-result-count" aria-live="polite">
            {{ t('po.queue.resultCount', { count: filteredCases.length }) }}
          </div>
        </header>

        <div v-if="filteredCases.length" class="po-table-wrap">
          <table class="po-case-table">
            <caption class="po-sr-only">{{ t('po.queue.title') }}</caption>
            <thead>
              <tr>
                <th scope="col">{{ t('po.queue.tableCase') }}</th>
                <th scope="col">{{ t('po.queue.tableSpecimen') }}</th>
                <th scope="col">{{ t('po.queue.tableRisk') }}</th>
                <th scope="col">{{ t('po.queue.tableConfidence') }}</th>
                <th scope="col">{{ t('po.queue.tableEvidence') }}</th>
                <th scope="col">{{ t('po.queue.tableStatus') }}</th>
                <th scope="col">{{ t('po.queue.tableOwner') }}</th>
                <th scope="col">{{ t('po.queue.tableDue') }}</th>
                <th scope="col"><span class="po-sr-only">{{ t('common.actions') }}</span></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="caseItem in filteredCases" :key="caseItem.id">
                <td>
                  <router-link
                    class="po-case-identity"
                    :to="{ name: 'caseReview', params: { caseId: caseItem.id } }"
                  >
                    <strong>{{ caseItem.id }}</strong>
                    <span>{{ caseItem.accession }}</span>
                  </router-link>
                </td>
                <td>
                  <div class="po-specimen-cell">
                    <strong>{{ caseItem.specimen.node }}</strong>
                    <span>{{ t(caseItem.specimen.siteKey) }} / {{ caseItem.specimen.stain }}</span>
                  </div>
                </td>
                <td><CaseRiskBadge :score="caseItem.ai.risk" /></td>
                <td>
                  <div class="po-confidence-cell">
                    <strong class="po-tabular">{{ formatPercent(caseItem.ai.confidence, locale) }}</strong>
                    <span v-if="caseItem.ai.confidence < 0.72">
                      <el-icon aria-hidden="true"><Warning /></el-icon>
                      {{ t('po.confidence.low') }}
                    </span>
                  </div>
                </td>
                <td>
                  <div v-if="evidenceLabels(caseItem).length" class="po-evidence-flags">
                    <span
                      v-for="label in evidenceLabels(caseItem).slice(0, 2)"
                      :key="label"
                    >
                      {{ t(label) }}
                    </span>
                    <span v-if="evidenceLabels(caseItem).length > 2" class="po-more-flags">
                      +{{ evidenceLabels(caseItem).length - 2 }}
                    </span>
                  </div>
                  <span v-else class="po-no-flags">{{ t('po.queue.noFlags') }}</span>
                </td>
                <td><CaseStatusBadge :status="caseItem.workflow.status" /></td>
                <td>
                  <span :class="{ 'po-unassigned': !caseItem.owner }">
                    {{ caseItem.owner || t('po.queue.unassigned') }}
                  </span>
                </td>
                <td>
                  <time class="po-due-time po-tabular" :datetime="caseItem.dueAt">
                    {{ formatDateTime(caseItem.dueAt, locale) }}
                  </time>
                </td>
                <td class="po-action-cell">
                  <el-tooltip
                    :content="t(isReadOnly ? 'po.queue.readCase' : 'po.queue.openCase')"
                    placement="left"
                  >
                    <el-button
                      :aria-label="t(isReadOnly ? 'po.queue.readCase' : 'po.queue.openCase')"
                      class="po-open-button"
                      :icon="ArrowRight"
                      @click="router.push({ name: 'caseReview', params: { caseId: caseItem.id } })"
                    />
                  </el-tooltip>
                </td>
              </tr>
            </tbody>
          </table>

          <div class="po-mobile-cases">
            <article v-for="caseItem in filteredCases" :key="caseItem.id" class="po-mobile-case">
              <router-link :to="{ name: 'caseReview', params: { caseId: caseItem.id } }">
                <div class="po-mobile-case-head">
                  <div>
                    <strong>{{ caseItem.id }}</strong>
                    <span>{{ caseItem.accession }}</span>
                  </div>
                  <CaseRiskBadge :score="caseItem.ai.risk" />
                </div>
                <div class="po-mobile-case-meta">
                  <span>{{ caseItem.specimen.node }} / {{ caseItem.specimen.stain }}</span>
                  <CaseStatusBadge :status="caseItem.workflow.status" />
                </div>
                <div class="po-mobile-case-foot">
                  <span>{{ caseItem.owner || t('po.queue.unassigned') }}</span>
                  <time :datetime="caseItem.dueAt">{{ formatDateTime(caseItem.dueAt, locale) }}</time>
                  <el-icon aria-hidden="true"><ArrowRight /></el-icon>
                </div>
              </router-link>
            </article>
          </div>
        </div>

        <div v-else class="po-empty-state">
          <el-icon aria-hidden="true"><Filter /></el-icon>
          <strong>{{ t('po.queue.noResults') }}</strong>
          <el-button @click="clearFilters">{{ t('po.queue.clearFilters') }}</el-button>
        </div>
      </section>
      <CaseCreateDrawer
        v-model="showCaseCreate"
        @created="openCreatedCase"
      />
    </section>
  </ProductShell>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowRight,
  Filter,
  FolderOpened,
  Plus,
  RefreshRight,
  Search,
  Warning,
} from '@element-plus/icons-vue'
import ProductShell from '@/components/ProductShell.vue'
import CaseRiskBadge from '@/components/CaseRiskBadge.vue'
import CaseStatusBadge from '@/components/CaseStatusBadge.vue'
import CaseCreateDrawer from '@/components/CaseCreateDrawer.vue'
import { isOperationalReadOnly } from '@/config/product'
import { useCaseStore } from '@/stores/caseStore'
import { useProductStore } from '@/stores/productStore'
import { formatDateTime, formatPercent, getRiskBand } from '@/utils/formatters'

const { t, locale } = useI18n()
const router = useRouter()
const caseStore = useCaseStore()
const productStore = useProductStore()

const search = ref('')
const riskFilter = ref('all')
const statusFilter = ref('all')
const sortBy = ref('risk-desc')
const showCaseCreate = ref(false)
const statusOptions = ['ready', 'in_review', 'senior_review', 'returned', 'completed']
const canCreateCase = computed(() => (
  ['AI_DATA_ADMIN', 'SYSTEM_ADMIN'].includes(productStore.effectiveRole)
))

function openCreatedCase(item) {
  router.push({ name: 'caseReview', params: { caseId: item.id } })
}

onMounted(() => caseStore.loadCases())

const scopeCases = computed(() => {
  if (productStore.effectiveRole === 'REVIEWER') {
    return caseStore.cases.filter((item) => item.workflow.status === 'senior_review')
  }
  return caseStore.cases
})

const filteredCases = computed(() => {
  const query = search.value.trim().toLowerCase()
  const filtered = scopeCases.value.filter((item) => {
    const matchesSearch = !query
      || item.id.toLowerCase().includes(query)
      || item.accession.toLowerCase().includes(query)
      || item.specimen.node.toLowerCase().includes(query)
    const matchesRisk = riskFilter.value === 'all' || getRiskBand(item.ai.risk) === riskFilter.value
    const matchesStatus = statusFilter.value === 'all' || item.workflow.status === statusFilter.value
    return matchesSearch && matchesRisk && matchesStatus
  })

  return [...filtered].sort((a, b) => {
    if (sortBy.value === 'due-asc') return new Date(a.dueAt) - new Date(b.dueAt)
    if (sortBy.value === 'received-desc') return new Date(b.receivedAt) - new Date(a.receivedAt)
    return b.ai.risk - a.ai.risk
  })
})

const summaryMetrics = computed(() => {
  const cases = scopeCases.value
  return [
    {
      labelKey: 'po.queue.summaryAttention',
      value: cases.filter((item) => ['ready', 'returned', 'senior_review'].includes(item.workflow.status)).length,
      icon: 'Bell',
      tone: 'po-tone-attention',
    },
    {
      labelKey: 'po.queue.summaryHighRisk',
      value: cases.filter((item) => item.ai.risk >= 0.8).length,
      icon: 'WarningFilled',
      tone: 'po-tone-risk',
    },
    {
      labelKey: 'po.queue.summaryInReview',
      value: cases.filter((item) => ['in_review', 'senior_review'].includes(item.workflow.status)).length,
      icon: 'View',
      tone: 'po-tone-primary',
    },
    {
      labelKey: 'po.queue.summaryCompleted',
      value: cases.filter((item) => item.workflow.status === 'completed').length,
      icon: 'CircleCheck',
      tone: 'po-tone-verified',
    },
  ]
})

const subtitleKey = computed(() => {
  if (productStore.effectiveRole === 'REVIEWER') return 'po.queue.subtitleReviewer'
  if (['AI_DATA_ADMIN', 'SYSTEM_ADMIN'].includes(productStore.effectiveRole)) return 'po.queue.subtitleAdmin'
  return 'po.queue.subtitlePathologist'
})

const scopeKey = computed(() => {
  if (productStore.effectiveRole === 'REVIEWER') return 'po.queue.scopeReviewer'
  if (['AI_DATA_ADMIN', 'SYSTEM_ADMIN'].includes(productStore.effectiveRole)) return 'po.queue.scopeAdmin'
  return 'po.queue.scopePathologist'
})

const isReadOnly = computed(() => isOperationalReadOnly(productStore.effectiveRole))

function evidenceLabels(caseItem) {
  if (caseItem.workflow.reviewTriggers.length) {
    return caseItem.workflow.reviewTriggers.map((reason) => 'po.reviewReasons.' + reason)
  }
  return caseItem.ai.flags.map((flag) => 'po.flags.' + flag)
}

function clearFilters() {
  search.value = ''
  riskFilter.value = 'all'
  statusFilter.value = 'all'
}

async function resetDemoData() {
  try {
    await ElMessageBox.confirm(
      t('po.queue.resetConfirm'),
      t('po.queue.resetTitle'),
      {
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
        type: 'warning',
      },
    )
    caseStore.resetDemoData()
    clearFilters()
    ElMessage.success(t('po.queue.resetDone'))
  } catch {
    // Cancellation is expected and needs no notification.
  }
}
</script>

<style scoped>
.po-queue-page {
  display: grid;
  gap: 22px;
}

.po-page-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
}

.po-heading-kicker {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 6px;
  color: var(--po-harbor);
  font-size: 11px;
  font-weight: 650;
}

.po-page-heading h1 {
  margin: 0;
  color: var(--po-ink);
  font-size: 28px;
  font-weight: 650;
  line-height: 1.2;
  letter-spacing: 0;
}

.po-page-heading p {
  margin: 7px 0 0;
  color: var(--po-ink-muted);
  font-size: 14px;
}

.po-heading-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.po-profile-line {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 36px;
  padding: 7px 12px;
  border-top: 1px solid var(--po-hairline);
  border-bottom: 1px solid var(--po-hairline);
  color: var(--po-ink-muted);
  font-size: 11px;
}

.po-profile-line strong {
  color: var(--po-ink);
  font-weight: 600;
}

.po-profile-line > span:not(.po-profile-accent)::before {
  margin-right: 10px;
  color: var(--po-hairline);
  content: "/";
}

.po-profile-accent {
  width: 8px;
  height: 8px;
  border: 2px solid var(--po-eosin);
  border-radius: 2px;
  background: var(--po-eosin-soft);
}

.po-summary-band {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  min-height: 72px;
  border-top: 1px solid var(--po-hairline);
  border-bottom: 1px solid var(--po-hairline);
  background: var(--po-paper);
}

.po-summary-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 18px;
}

.po-summary-item + .po-summary-item {
  border-left: 1px solid var(--po-hairline);
}

.po-summary-item > .el-icon {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: var(--po-radius-sm);
  font-size: 15px;
}

.po-summary-item div {
  display: grid;
  line-height: 1.2;
}

.po-summary-item strong {
  color: var(--po-ink);
  font-size: 20px;
  font-weight: 650;
}

.po-summary-item span {
  margin-top: 3px;
  color: var(--po-ink-muted);
  font-size: 11px;
  font-weight: 600;
}

.po-tone-attention {
  background: var(--po-attention-soft);
  color: var(--po-attention);
}

.po-tone-risk {
  background: var(--po-risk-soft);
  color: var(--po-risk);
}

.po-tone-primary {
  background: var(--po-harbor-soft);
  color: var(--po-harbor);
}

.po-tone-verified {
  background: var(--po-verified-soft);
  color: var(--po-verified);
}

.po-queue-tool {
  overflow: hidden;
  border: 1px solid var(--po-hairline);
  border-radius: var(--po-radius-md);
  background: var(--po-paper);
}

.po-queue-toolbar {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) auto auto;
  align-items: center;
  gap: 12px;
  min-height: 60px;
  padding: 11px 14px;
  border-bottom: 1px solid var(--po-hairline);
  background: var(--po-paper);
}

.po-search-field {
  max-width: 380px;
}

.po-filter-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.po-filter-select {
  width: 142px;
}

.po-sort-select {
  width: 190px;
}

.po-result-count {
  color: var(--po-ink-muted);
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}

.po-table-wrap {
  overflow-x: auto;
}

.po-case-table {
  width: 100%;
  min-width: 1220px;
  border-collapse: collapse;
  table-layout: fixed;
}

.po-case-table th {
  height: 38px;
  padding: 0 12px;
  border-bottom: 1px solid var(--po-hairline);
  background: var(--po-surface-subtle);
  color: var(--po-ink-muted);
  font-size: 10px;
  font-weight: 650;
  text-align: left;
}

.po-case-table th:nth-child(1) { width: 150px; }
.po-case-table th:nth-child(2) { width: 160px; }
.po-case-table th:nth-child(3) { width: 130px; }
.po-case-table th:nth-child(4) { width: 116px; }
.po-case-table th:nth-child(5) { width: 190px; }
.po-case-table th:nth-child(6) { width: 128px; }
.po-case-table th:nth-child(7) { width: 128px; }
.po-case-table th:nth-child(8) { width: 132px; }
.po-case-table th:nth-child(9) { width: 52px; }

.po-case-table td {
  height: 62px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--po-hairline);
  color: var(--po-ink);
  font-size: 12px;
  vertical-align: middle;
}

.po-case-table tbody tr:last-child td {
  border-bottom: 0;
}

.po-case-table tbody tr {
  transition: background-color var(--po-motion-fast) var(--po-ease-state);
}

.po-case-table tbody tr:hover {
  background: var(--po-canvas);
}

.po-case-identity,
.po-specimen-cell,
.po-confidence-cell {
  display: grid;
  line-height: 1.3;
}

.po-case-identity {
  color: var(--po-ink);
  text-decoration: none;
}

.po-case-identity:hover strong {
  color: var(--po-harbor);
  text-decoration: underline;
}

.po-case-identity:focus-visible {
  border-radius: 2px;
  outline: 3px solid var(--po-harbor-soft);
  outline-offset: 2px;
}

.po-case-identity strong {
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 12px;
  font-weight: 600;
}

.po-case-identity span,
.po-specimen-cell span {
  margin-top: 3px;
  color: var(--po-ink-muted);
  font-size: 10px;
}

.po-specimen-cell strong {
  font-size: 12px;
  font-weight: 600;
}

.po-confidence-cell strong {
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 12px;
  font-weight: 600;
}

.po-confidence-cell span {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  margin-top: 3px;
  color: var(--po-attention);
  font-size: 10px;
  font-weight: 600;
}

.po-evidence-flags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.po-evidence-flags > span {
  max-width: 172px;
  overflow: hidden;
  padding: 2px 5px;
  border: 1px solid oklch(0.89 0.065 350);
  border-radius: 3px;
  background: var(--po-eosin-soft);
  color: oklch(0.4 0.12 350);
  font-size: 9px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.po-evidence-flags .po-more-flags {
  border-color: var(--po-hairline);
  background: var(--po-surface-subtle);
  color: var(--po-ink-muted);
}

.po-no-flags,
.po-unassigned {
  color: var(--po-ink-muted);
}

.po-due-time {
  color: var(--po-ink-muted);
  font-size: 11px;
}

.po-action-cell {
  text-align: right;
}

.po-open-button {
  width: 34px;
  min-height: 34px;
  padding: 0;
  border-color: var(--po-hairline);
  border-radius: var(--po-radius-sm);
}

.po-mobile-cases {
  display: none;
}

.po-empty-state {
  display: grid;
  place-items: center;
  gap: 10px;
  min-height: 260px;
  padding: 32px;
  color: var(--po-ink-muted);
  text-align: center;
}

.po-empty-state > .el-icon {
  font-size: 24px;
}

.po-empty-state strong {
  color: var(--po-ink);
  font-size: 14px;
}

@media (max-width: 1100px) {
  .po-queue-toolbar {
    grid-template-columns: 1fr auto;
  }

  .po-result-count {
    grid-column: 1 / -1;
  }
}

@media (max-width: 1180px) and (min-width: 761px) {
  .po-case-table {
    min-width: 880px;
  }

  .po-case-table th:nth-child(4),
  .po-case-table td:nth-child(4),
  .po-case-table th:nth-child(5),
  .po-case-table td:nth-child(5) {
    display: none;
  }
}

@media (max-width: 760px) {
  .po-page-heading {
    align-items: flex-start;
  }

  .po-page-heading h1 {
    font-size: 24px;
  }

  .po-heading-actions {
    flex: 0 0 auto;
  }

  .po-heading-actions .el-button {
    width: 36px;
    padding: 0;
  }

  .po-heading-actions .el-button :deep(span) {
    display: none;
  }

  .po-profile-line {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .po-summary-band {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .po-summary-item:nth-child(3) {
    border-left: 0;
    border-top: 1px solid var(--po-hairline);
  }

  .po-summary-item:nth-child(4) {
    border-top: 1px solid var(--po-hairline);
  }

  .po-queue-toolbar {
    grid-template-columns: 1fr;
  }

  .po-search-field {
    max-width: none;
  }

  .po-filter-row {
    display: grid;
    grid-template-columns: 1fr 1fr;
  }

  .po-sort-select {
    grid-column: 1 / -1;
    width: 100%;
  }

  .po-filter-select {
    width: 100%;
  }

  .po-case-table {
    display: none;
  }

  .po-mobile-cases {
    display: grid;
  }

  .po-mobile-case + .po-mobile-case {
    border-top: 1px solid var(--po-hairline);
  }

  .po-mobile-case a {
    display: grid;
    gap: 12px;
    padding: 14px;
    color: var(--po-ink);
    text-decoration: none;
  }

  .po-mobile-case a:focus-visible {
    outline: 3px solid var(--po-harbor-soft);
    outline-offset: -3px;
  }

  .po-mobile-case-head,
  .po-mobile-case-meta,
  .po-mobile-case-foot {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
  }

  .po-mobile-case-head > div {
    display: grid;
  }

  .po-mobile-case-head strong {
    font-family: "SFMono-Regular", Consolas, monospace;
    font-size: 12px;
  }

  .po-mobile-case-head span,
  .po-mobile-case-meta > span,
  .po-mobile-case-foot {
    color: var(--po-ink-muted);
    font-size: 10px;
  }

  .po-mobile-case-foot {
    padding-top: 10px;
    border-top: 1px solid var(--po-hairline);
  }

  .po-mobile-case-foot .el-icon {
    color: var(--po-harbor);
    font-size: 14px;
  }
}

@media (max-width: 480px) {
  .po-page-heading {
    position: relative;
    display: block;
    padding-right: 44px;
  }

  .po-heading-actions {
    position: absolute;
    top: 22px;
    right: 0;
  }

  .po-profile-line > span:nth-child(3),
  .po-profile-line > span:last-child {
    display: none;
  }

  .po-summary-item {
    padding: 10px 12px;
  }

  .po-summary-item strong {
    font-size: 18px;
  }

  .po-mobile-case-head,
  .po-mobile-case-meta {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .po-mobile-case-head > div {
    min-width: 0;
  }
}
</style>
