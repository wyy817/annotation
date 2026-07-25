<template>
  <div :class="['po-shell', { 'po-shell-demo': productStore.isDemoMode }]">
    <a class="po-skip-link" href="#po-main">{{ t('po.common.skipToContent') }}</a>

    <header class="po-topbar">
      <div class="po-topbar-primary">
        <router-link class="po-brand" :to="{ name: 'cases' }" aria-label="PARVISIGHT">
          <span class="po-brand-mark" aria-hidden="true">
            <span></span>
            <span></span>
            <span></span>
          </span>
          <span class="po-brand-copy">
            <strong>{{ t('po.brandName') }}</strong>
            <small>{{ t('po.brandTagline') }}</small>
          </span>
        </router-link>

        <nav class="po-nav" :aria-label="t('po.common.primaryNavigation')">
          <router-link
            v-for="item in navigationItems"
            :key="item.name"
            class="po-nav-link"
            :to="{ name: item.name }"
          >
            <el-icon aria-hidden="true"><component :is="item.icon" /></el-icon>
            <span>{{ t(item.labelKey) }}</span>
          </router-link>
        </nav>
      </div>

      <div class="po-utilities">
        <span class="po-mode-label">
          <span class="po-mode-dot" aria-hidden="true"></span>
          {{ t(productStore.isDemoMode ? 'po.mode.demo' : 'po.mode.local') }}
        </span>

        <label v-if="productStore.isDemoMode" class="po-role-field">
          <span class="po-sr-only">{{ t('po.roles.switchLabel') }}</span>
          <el-select
            :model-value="productStore.effectiveRole"
            :aria-label="t('po.roles.switchLabel')"
            class="po-role-select"
            @change="productStore.setDemoRole"
          >
            <el-option
              v-for="role in productRoles"
              :key="role.id"
              :label="t(role.labelKey)"
              :value="role.id"
            />
          </el-select>
        </label>

        <ProductLanguageSwitch />
        <NotificationBell v-if="!productStore.isDemoMode && userStore.isLoggedIn" />

        <el-dropdown trigger="click">
          <button type="button" class="po-user-trigger">
            <span class="po-user-avatar" aria-hidden="true">{{ initials }}</span>
            <span class="po-user-copy">
              <strong>{{ productStore.identity.displayName }}</strong>
              <small>{{ t(productStore.roleDefinition.labelKey) }}</small>
            </span>
            <el-icon aria-hidden="true"><ArrowDown /></el-icon>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-if="!productStore.isDemoMode" @click="handleLogout">
                <el-icon><SwitchButton /></el-icon>
                {{ t('auth.logout') }}
              </el-dropdown-item>
              <el-dropdown-item v-else disabled>
                {{ t('po.mode.demoData') }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <div v-if="productStore.isDemoMode" class="po-demo-disclosure" role="note">
      <el-icon aria-hidden="true"><InfoFilled /></el-icon>
      <span>{{ t('po.mode.demoNotice') }}</span>
    </div>

    <main id="po-main" :class="['po-main', { 'po-main-wide': wide }]">
      <slot />
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  ArrowDown,
  InfoFilled,
  SwitchButton,
} from '@element-plus/icons-vue'
import { PRODUCT_ROLES } from '@/config/product'
import { useProductStore } from '@/stores/productStore'
import { useUserStore } from '@/stores/userStore'
import NotificationBell from '@/components/NotificationBell.vue'
import ProductLanguageSwitch from '@/components/ProductLanguageSwitch.vue'

defineProps({
  wide: {
    type: Boolean,
    default: false,
  },
})

const { t } = useI18n()
const router = useRouter()
const productStore = useProductStore()
const userStore = useUserStore()
const productRoles = PRODUCT_ROLES

const navigationItems = computed(() => {
  const items = [
    { name: 'cases', labelKey: 'po.nav.cases', icon: 'Files' },
    { name: 'feedbackPool', labelKey: 'po.nav.feedback', icon: 'DataAnalysis' },
  ]

  if (!productStore.isDemoMode) {
    items.push({ name: 'workspaces', labelKey: 'po.nav.slideLibrary', icon: 'Collection' })
    if (productStore.effectiveRole === 'SYSTEM_ADMIN') {
      items.push({ name: 'admin', labelKey: 'po.nav.administration', icon: 'Setting' })
    }
  }

  return items
})

const initials = computed(() => {
  const value = productStore.identity.displayName || productStore.identity.username || 'PO'
  return value
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0])
    .join('')
    .toUpperCase()
})

function handleLogout() {
  userStore.logout()
  router.replace({ name: 'login' })
}
</script>

<style scoped>
.po-shell {
  width: 100%;
  min-height: 100vh;
  overflow-x: clip;
  background: var(--po-canvas);
}

.po-skip-link {
  position: fixed;
  top: 8px;
  left: 8px;
  z-index: 1000;
  padding: 8px 12px;
  border-radius: var(--po-radius-sm);
  background: var(--po-harbor);
  color: var(--po-paper);
  font-weight: 600;
  text-decoration: none;
  transform: translateY(-160%);
}

.po-skip-link:focus {
  transform: translateY(0);
}

.po-topbar {
  position: sticky;
  top: 0;
  z-index: 40;
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 64px;
  padding: 0 24px;
  border-bottom: 1px solid var(--po-hairline);
  background: color-mix(in oklch, var(--po-paper) 96%, transparent);
  backdrop-filter: blur(10px);
}

.po-topbar-primary,
.po-utilities,
.po-nav,
.po-brand,
.po-user-trigger {
  display: flex;
  align-items: center;
}

.po-topbar-primary {
  min-width: 0;
  gap: 28px;
}

.po-brand {
  flex: 0 0 auto;
  gap: 10px;
  color: var(--po-ink);
  text-decoration: none;
}

.po-brand:focus-visible,
.po-nav-link:focus-visible,
.po-user-trigger:focus-visible {
  outline: 3px solid var(--po-harbor-soft);
  outline-offset: 2px;
}

.po-brand-mark {
  display: grid;
  grid-template-columns: repeat(3, 4px);
  align-items: end;
  gap: 3px;
  width: 18px;
  height: 24px;
}

.po-brand-mark span {
  display: block;
  width: 4px;
  border-radius: 1px;
  background: var(--po-harbor);
}

.po-brand-mark span:nth-child(1) {
  height: 14px;
}

.po-brand-mark span:nth-child(2) {
  height: 22px;
}

.po-brand-mark span:nth-child(3) {
  height: 17px;
  background: var(--po-eosin);
}

.po-brand-copy {
  display: grid;
  line-height: 1.15;
}

.po-brand-copy strong {
  font-size: 16px;
  font-weight: 650;
}

.po-brand-copy small {
  margin-top: 3px;
  color: var(--po-ink-muted);
  font-size: 10px;
  font-weight: 500;
}

.po-nav {
  gap: 3px;
}

.po-nav-link {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  height: 38px;
  padding: 0 10px;
  border-radius: var(--po-radius-sm);
  color: var(--po-ink-muted);
  font-size: 12px;
  font-weight: 600;
  text-decoration: none;
  white-space: nowrap;
  transition:
    color var(--po-motion-fast) var(--po-ease-state),
    background-color var(--po-motion-fast) var(--po-ease-state);
}

.po-nav-link:hover {
  background: var(--po-surface-subtle);
  color: var(--po-ink);
}

.po-nav-link.router-link-active {
  background: var(--po-harbor-soft);
  color: var(--po-harbor);
}

.po-utilities {
  flex: 0 0 auto;
  gap: 8px;
}

.po-mode-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--po-ink-muted);
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}

.po-mode-dot {
  width: 7px;
  height: 7px;
  border: 2px solid var(--po-eosin);
  border-radius: 2px;
  background: var(--po-eosin-soft);
}

.po-role-select {
  width: 184px;
}

.po-role-select :deep(.el-select__wrapper) {
  font-size: 12px;
  font-weight: 600;
}

.po-user-trigger {
  gap: 8px;
  min-height: 40px;
  padding: 3px 6px 3px 4px;
  border: 0;
  border-radius: var(--po-radius-sm);
  background: transparent;
  color: var(--po-ink);
  cursor: pointer;
  transition: background-color var(--po-motion-fast) var(--po-ease-state);
}

.po-user-trigger:hover {
  background: var(--po-surface-subtle);
}

.po-user-avatar {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border: 1px solid oklch(0.86 0.04 230);
  border-radius: var(--po-radius-sm);
  background: var(--po-harbor-soft);
  color: var(--po-harbor);
  font-size: 11px;
  font-weight: 700;
}

.po-user-copy {
  display: grid;
  min-width: 0;
  text-align: left;
  line-height: 1.2;
}

.po-user-copy strong {
  max-width: 132px;
  overflow: hidden;
  font-size: 12px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.po-user-copy small {
  margin-top: 2px;
  color: var(--po-ink-muted);
  font-size: 10px;
  white-space: nowrap;
}

.po-demo-disclosure {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-height: 32px;
  padding: 6px 24px;
  border-bottom: 1px solid oklch(0.89 0.04 80);
  background: var(--po-attention-soft);
  color: var(--po-attention);
  font-size: 11px;
  font-weight: 600;
  text-align: center;
  overflow-wrap: anywhere;
}

.po-main {
  width: min(calc(100% - 48px), 1320px);
  margin: 0 auto;
  padding: 28px 0 48px;
}

.po-main-wide {
  width: min(calc(100% - 32px), var(--po-content-max));
  padding-top: 18px;
}

@media (max-width: 1180px) {
  .po-topbar {
    padding: 0 16px;
  }

  .po-topbar-primary {
    gap: 16px;
  }

  .po-brand-copy small,
  .po-user-copy,
  .po-mode-label {
    display: none;
  }

  .po-role-select {
    width: 160px;
  }
}

@media (max-width: 820px) {
  .po-topbar {
    position: static;
    flex-wrap: wrap;
    gap: 8px;
    padding: 10px 12px;
  }

  .po-topbar-primary {
    width: 100%;
    justify-content: space-between;
    gap: 12px;
  }

  .po-nav {
    max-width: calc(100vw - 190px);
    overflow-x: auto;
    scrollbar-width: none;
  }

  .po-nav::-webkit-scrollbar {
    display: none;
  }

  .po-utilities {
    width: 100%;
    justify-content: flex-end;
  }

  .po-main,
  .po-main-wide {
    width: min(calc(100% - 24px), 1320px);
    padding-top: 20px;
  }
}

@media (max-width: 560px) {
  .po-topbar-primary {
    display: grid;
    grid-template-columns: auto minmax(0, 1fr);
  }

  .po-nav {
    justify-content: flex-end;
    max-width: none;
    min-width: 0;
  }

  .po-nav-link {
    padding: 0 8px;
  }

  .po-nav-link:nth-child(n + 2) {
    display: none;
  }

  .po-nav-link .el-icon {
    display: none;
  }

  .po-shell-demo .po-utilities {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 6px;
  }

  .po-shell-demo .po-user-trigger {
    display: none;
  }

  .po-role-field {
    flex: 1 1 auto;
    min-width: 0;
  }

  .po-role-select {
    width: 100%;
  }

  .po-user-trigger {
    padding-right: 2px;
  }
}
</style>
