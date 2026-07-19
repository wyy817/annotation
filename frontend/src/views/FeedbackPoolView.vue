<template>
  <ProductShell>
    <section class="po-feedback-page">
      <header class="po-page-heading">
        <div>
          <div class="po-heading-kicker">
            <span>{{ t('po.mode.demoData') }}</span>
            <span aria-hidden="true">/</span>
            <span>{{ t('po.nav.feedback') }}</span>
          </div>
          <h1>{{ t('po.feedback.title') }}</h1>
          <p>{{ t('po.feedback.subtitle') }}</p>
        </div>
        <span class="po-record-count">{{ t('po.feedback.resultCount', { count: filteredRecords.length }) }}</span>
      </header>

      <div class="po-provenance-note" role="note">
        <el-icon aria-hidden="true"><InfoFilled /></el-icon>
        <span>{{ t('po.feedback.provenanceNote') }}</span>
      </div>

      <section class="po-feedback-tool">
        <header>
          <el-select
            v-model="statusFilter"
            :aria-label="t('po.queue.statusFilter')"
            class="po-feedback-filter"
          >
            <el-option :label="t('po.feedback.allStatuses')" value="all" />
            <el-option :label="t('po.feedback.queued')" value="queued" />
            <el-option :label="t('po.feedback.validated')" value="validated" />
          </el-select>
        </header>

        <div v-if="filteredRecords.length" class="po-feedback-table-wrap">
          <table>
            <caption class="po-sr-only">{{ t('po.feedback.title') }}</caption>
            <thead>
              <tr>
                <th scope="col">{{ t('po.feedback.tableCase') }}</th>
                <th scope="col">{{ t('po.feedback.tableDecision') }}</th>
                <th scope="col">{{ t('po.feedback.tableReview') }}</th>
                <th scope="col">{{ t('po.feedback.tableVersion') }}</th>
                <th scope="col">{{ t('po.feedback.tableStatus') }}</th>
                <th scope="col">{{ t('po.feedback.tableUpdated') }}</th>
                <th scope="col"><span class="po-sr-only">{{ t('common.actions') }}</span></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="caseItem in filteredRecords" :key="caseItem.id">
                <td>
                  <router-link
                    class="po-feedback-case"
                    :to="{ name: 'caseReview', params: { caseId: caseItem.id } }"
                  >
                    <strong>{{ caseItem.id }}</strong>
                    <span>{{ caseItem.accession }}</span>
                  </router-link>
                </td>
                <td>
                  <div class="po-decision-cell">
                    <strong>{{ t('po.decision.' + caseItem.workflow.decision.type) }}</strong>
                    <span>{{ caseItem.workflow.decision.actor }}</span>
                  </div>
                </td>
                <td>
                  <span class="po-review-path">
                    <el-icon aria-hidden="true">
                      <Connection v-if="caseItem.workflow.reviewTriggers.length" />
                      <Right v-else />
                    </el-icon>
                    {{ t(caseItem.workflow.reviewTriggers.length ? 'po.feedback.conditional' : 'po.feedback.direct') }}
                  </span>
                </td>
                <td>
                  <span class="po-version">{{ caseItem.feedback.version }}</span>
                </td>
                <td>
                  <span :class="['po-feedback-status', caseItem.feedback.status]">
                    <el-icon aria-hidden="true">
                      <CircleCheck v-if="caseItem.feedback.status === 'validated'" />
                      <Clock v-else />
                    </el-icon>
                    {{ t('po.feedback.' + caseItem.feedback.status) }}
                  </span>
                </td>
                <td>
                  <time :datetime="caseItem.workflow.lastUpdatedAt">
                    {{ formatDateTime(caseItem.workflow.lastUpdatedAt, locale) }}
                  </time>
                </td>
                <td class="po-feedback-actions">
                  <el-button
                    v-if="canValidate && caseItem.feedback.status === 'queued'"
                    type="primary"
                    plain
                    :icon="DocumentChecked"
                    @click="validate(caseItem)"
                  >
                    {{ t('po.feedback.validate') }}
                  </el-button>
                  <el-tooltip v-else :content="t('po.feedback.openCase')" placement="left">
                    <el-button
                      :aria-label="t('po.feedback.openCase')"
                      :icon="ArrowRight"
                      @click="router.push({ name: 'caseReview', params: { caseId: caseItem.id } })"
                    />
                  </el-tooltip>
                </td>
              </tr>
            </tbody>
          </table>

          <div class="po-feedback-mobile">
            <article v-for="caseItem in filteredRecords" :key="caseItem.id">
              <div class="po-feedback-mobile-head">
                <div>
                  <strong>{{ caseItem.id }}</strong>
                  <span>{{ caseItem.accession }}</span>
                </div>
                <span :class="['po-feedback-status', caseItem.feedback.status]">
                  {{ t('po.feedback.' + caseItem.feedback.status) }}
                </span>
              </div>
              <dl>
                <div>
                  <dt>{{ t('po.feedback.tableDecision') }}</dt>
                  <dd>{{ t('po.decision.' + caseItem.workflow.decision.type) }}</dd>
                </div>
                <div>
                  <dt>{{ t('po.feedback.tableVersion') }}</dt>
                  <dd>{{ caseItem.feedback.version }}</dd>
                </div>
              </dl>
              <router-link :to="{ name: 'caseReview', params: { caseId: caseItem.id } }">
                {{ t('po.feedback.openCase') }}
                <el-icon aria-hidden="true"><ArrowRight /></el-icon>
              </router-link>
            </article>
          </div>
        </div>

        <div v-else class="po-feedback-empty">
          <el-icon aria-hidden="true"><DataAnalysis /></el-icon>
          <p>{{ t('po.feedback.empty') }}</p>
        </div>
      </section>
    </section>
  </ProductShell>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import {
  ArrowRight,
  CircleCheck,
  Clock,
  Connection,
  DataAnalysis,
  DocumentChecked,
  InfoFilled,
  Right,
} from '@element-plus/icons-vue'
import ProductShell from '@/components/ProductShell.vue'
import { useCaseStore } from '@/stores/caseStore'
import { useProductStore } from '@/stores/productStore'
import { formatDateTime } from '@/utils/formatters'

const { t, locale } = useI18n()
const router = useRouter()
const caseStore = useCaseStore()
const productStore = useProductStore()
const statusFilter = ref('all')

const canValidate = computed(() => productStore.effectiveRole === 'AI_DATA_ADMIN')
const filteredRecords = computed(() => caseStore.feedbackCases.filter((caseItem) => (
  statusFilter.value === 'all' || caseItem.feedback.status === statusFilter.value
)))

onMounted(() => caseStore.loadCases())

async function validate(caseItem) {
  if (!await caseStore.validateFeedback(caseItem.id, productStore.identity)) return
  ElMessage.success(t('po.feedback.validatedMessage'))
}
</script>

<style scoped>
.po-feedback-page {
  display: grid;
  gap: 20px;
}

.po-page-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
}

.po-heading-kicker {
  display: flex;
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
}

.po-page-heading p {
  margin: 7px 0 0;
  color: var(--po-ink-muted);
  font-size: 14px;
}

.po-record-count {
  color: var(--po-ink-muted);
  font-size: 11px;
  font-weight: 600;
}

.po-provenance-note {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 9px 11px;
  border: 1px solid oklch(0.89 0.04 80);
  border-radius: var(--po-radius-sm);
  background: var(--po-attention-soft);
  color: var(--po-attention);
  font-size: 10px;
  font-weight: 600;
}

.po-provenance-note .el-icon {
  flex: 0 0 auto;
  margin-top: 1px;
}

.po-feedback-tool {
  overflow: hidden;
  border: 1px solid var(--po-hairline);
  border-radius: var(--po-radius-md);
  background: var(--po-paper);
}

.po-feedback-tool > header {
  display: flex;
  justify-content: flex-end;
  min-height: 58px;
  padding: 11px 14px;
  border-bottom: 1px solid var(--po-hairline);
}

.po-feedback-filter {
  width: 190px;
}

.po-feedback-table-wrap {
  overflow-x: auto;
}

table {
  width: 100%;
  min-width: 980px;
  border-collapse: collapse;
  table-layout: fixed;
}

th {
  height: 38px;
  padding: 0 12px;
  border-bottom: 1px solid var(--po-hairline);
  background: var(--po-surface-subtle);
  color: var(--po-ink-muted);
  font-size: 10px;
  font-weight: 650;
  text-align: left;
}

th:nth-child(1) { width: 155px; }
th:nth-child(2) { width: 150px; }
th:nth-child(3) { width: 180px; }
th:nth-child(4) { width: 74px; }
th:nth-child(5) { width: 125px; }
th:nth-child(6) { width: 140px; }
th:nth-child(7) { width: 156px; }

td {
  height: 62px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--po-hairline);
  color: var(--po-ink);
  font-size: 11px;
}

tbody tr:last-child td {
  border-bottom: 0;
}

.po-feedback-case,
.po-decision-cell {
  display: grid;
  line-height: 1.3;
}

.po-feedback-case {
  color: var(--po-ink);
  text-decoration: none;
}

.po-feedback-case:hover strong {
  color: var(--po-harbor);
  text-decoration: underline;
}

.po-feedback-case:focus-visible {
  outline: 3px solid var(--po-harbor-soft);
  outline-offset: 2px;
}

.po-feedback-case strong {
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 11px;
}

.po-feedback-case span,
.po-decision-cell span {
  margin-top: 3px;
  color: var(--po-ink-muted);
  font-size: 9px;
}

.po-decision-cell strong {
  font-weight: 650;
}

.po-review-path {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--po-ink-muted);
  font-size: 10px;
}

.po-review-path .el-icon {
  color: var(--po-eosin);
}

.po-version {
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 10px;
  font-weight: 600;
}

.po-feedback-status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 24px;
  padding: 0 7px;
  border: 1px solid;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 650;
  white-space: nowrap;
}

.po-feedback-status.queued {
  border-color: oklch(0.87 0.065 80);
  background: var(--po-attention-soft);
  color: var(--po-attention);
}

.po-feedback-status.validated {
  border-color: oklch(0.88 0.05 155);
  background: var(--po-verified-soft);
  color: var(--po-verified);
}

td time {
  color: var(--po-ink-muted);
  font-size: 10px;
}

.po-feedback-actions {
  text-align: right;
}

.po-feedback-actions .el-button {
  font-size: 10px;
}

.po-feedback-mobile {
  display: none;
}

.po-feedback-empty {
  display: grid;
  place-items: center;
  gap: 9px;
  min-height: 260px;
  color: var(--po-ink-muted);
}

.po-feedback-empty .el-icon {
  font-size: 26px;
}

.po-feedback-empty p {
  margin: 0;
  font-size: 12px;
}

@media (max-width: 760px) {
  .po-page-heading {
    align-items: flex-start;
  }

  .po-page-heading h1 {
    font-size: 24px;
  }

  table {
    display: none;
  }

  .po-feedback-mobile {
    display: grid;
  }

  .po-feedback-mobile article {
    display: grid;
    gap: 12px;
    padding: 14px;
  }

  .po-feedback-mobile article + article {
    border-top: 1px solid var(--po-hairline);
  }

  .po-feedback-mobile-head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
  }

  .po-feedback-mobile-head > div {
    display: grid;
  }

  .po-feedback-mobile-head strong {
    font-family: "SFMono-Regular", Consolas, monospace;
    font-size: 11px;
  }

  .po-feedback-mobile-head > div span {
    margin-top: 3px;
    color: var(--po-ink-muted);
    font-size: 9px;
  }

  .po-feedback-mobile dl {
    display: grid;
    grid-template-columns: 1fr 1fr;
    margin: 0;
  }

  .po-feedback-mobile dl > div {
    display: grid;
    gap: 2px;
  }

  .po-feedback-mobile dt {
    color: var(--po-ink-muted);
    font-size: 9px;
  }

  .po-feedback-mobile dd {
    margin: 0;
    color: var(--po-ink);
    font-size: 10px;
    font-weight: 600;
  }

  .po-feedback-mobile a {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding-top: 10px;
    border-top: 1px solid var(--po-hairline);
    color: var(--po-harbor);
    font-size: 10px;
    font-weight: 650;
    text-decoration: none;
  }
}
</style>
