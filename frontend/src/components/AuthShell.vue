<template>
  <div class="po-auth-shell">
    <header class="po-auth-topbar">
      <router-link class="po-auth-brand" :to="{ name: 'login' }" aria-label="PARVISIGHT">
        <span class="po-auth-brand-mark" aria-hidden="true">
          <span></span>
          <span></span>
          <span></span>
        </span>
        <span class="po-auth-brand-copy">
          <strong>{{ t('po.brandName') }}</strong>
          <small>{{ t('po.brandTagline') }}</small>
        </span>
      </router-link>

      <div class="po-auth-utilities">
        <span class="po-auth-mode">
          <el-icon aria-hidden="true"><Lock /></el-icon>
          {{ t('po.mode.local') }}
        </span>
        <ProductLanguageSwitch />
      </div>
    </header>

    <main class="po-auth-main">
      <section class="po-auth-panel" :aria-labelledby="headingId">
        <div class="po-auth-heading">
          <span class="po-auth-eyebrow">{{ t('po.auth.secureAccess') }}</span>
          <h1 :id="headingId">{{ title }}</h1>
          <p>{{ subtitle }}</p>
        </div>
        <slot />
      </section>
    </main>

    <footer class="po-auth-footer">
      <span>{{ t('po.auth.localBoundary') }}</span>
    </footer>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Lock } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import ProductLanguageSwitch from '@/components/ProductLanguageSwitch.vue'

const props = defineProps({
  title: {
    type: String,
    required: true,
  },
  subtitle: {
    type: String,
    required: true,
  },
})

const { t } = useI18n()
const headingId = computed(() => `po-auth-${props.title.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`)
</script>

<style scoped>
.po-auth-shell {
  display: grid;
  grid-template-rows: auto 1fr auto;
  min-height: 100vh;
  background:
    linear-gradient(var(--po-hairline), var(--po-hairline)) 0 92px / 100% 1px no-repeat,
    var(--po-canvas);
}

.po-auth-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 64px;
  padding: 0 24px;
  border-bottom: 1px solid var(--po-hairline);
  background: var(--po-paper);
}

.po-auth-brand,
.po-auth-utilities,
.po-auth-mode {
  display: flex;
  align-items: center;
}

.po-auth-brand {
  gap: 10px;
  color: var(--po-ink);
  text-decoration: none;
}

.po-auth-brand:focus-visible {
  outline: 3px solid var(--po-harbor-soft);
  outline-offset: 3px;
}

.po-auth-brand-mark {
  display: grid;
  grid-template-columns: repeat(3, 4px);
  align-items: end;
  gap: 3px;
  width: 18px;
  height: 24px;
}

.po-auth-brand-mark span {
  width: 4px;
  border-radius: 1px;
  background: var(--po-harbor);
}

.po-auth-brand-mark span:nth-child(1) {
  height: 14px;
}

.po-auth-brand-mark span:nth-child(2) {
  height: 22px;
}

.po-auth-brand-mark span:nth-child(3) {
  height: 17px;
  background: var(--po-eosin);
}

.po-auth-brand-copy {
  display: grid;
  line-height: 1.15;
}

.po-auth-brand-copy strong {
  font-size: 16px;
  font-weight: 650;
}

.po-auth-brand-copy small {
  margin-top: 3px;
  color: var(--po-ink-muted);
  font-size: 10px;
  font-weight: 500;
}

.po-auth-utilities {
  gap: 10px;
}

.po-auth-mode {
  gap: 6px;
  color: var(--po-ink-muted);
  font-size: 11px;
  font-weight: 600;
}

.po-auth-main {
  display: grid;
  place-items: center;
  width: min(calc(100% - 32px), 440px);
  margin: 0 auto;
  padding: 52px 0;
}

.po-auth-panel {
  width: 100%;
  padding: 32px;
  border: 1px solid var(--po-hairline);
  border-radius: var(--po-radius-md);
  background: var(--po-paper);
}

.po-auth-panel::before {
  display: block;
  width: 44px;
  height: 3px;
  margin-bottom: 24px;
  border-radius: 1px;
  background: linear-gradient(90deg, var(--po-harbor) 0 70%, var(--po-eosin) 70% 100%);
  content: '';
}

.po-auth-heading {
  margin-bottom: 26px;
}

.po-auth-eyebrow {
  color: var(--po-harbor);
  font-size: 11px;
  font-weight: 700;
}

.po-auth-heading h1 {
  margin: 7px 0 6px;
  color: var(--po-ink);
  font-size: 26px;
  font-weight: 680;
  letter-spacing: 0;
  line-height: 1.18;
}

.po-auth-heading p {
  margin: 0;
  color: var(--po-ink-muted);
  font-size: 13px;
}

.po-auth-footer {
  display: flex;
  justify-content: center;
  min-height: 48px;
  padding: 0 20px 20px;
  color: var(--po-ink-muted);
  font-size: 11px;
  text-align: center;
}

@media (max-width: 560px) {
  .po-auth-topbar {
    padding: 10px 12px;
  }

  .po-auth-brand-copy small,
  .po-auth-mode {
    display: none;
  }

  .po-auth-main {
    align-items: start;
    padding: 28px 0;
  }

  .po-auth-panel {
    padding: 24px 20px;
  }
}
</style>
