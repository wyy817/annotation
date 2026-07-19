<template>
  <el-config-provider :locale="elLocale">
    <router-view />
  </el-config-provider>
</template>

<script setup>
import { computed, watch, onMounted } from 'vue'
import { ElNotification } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import enUs from 'element-plus/es/locale/lang/en'
import { useUserStore } from '@/stores/userStore'
import { useNotificationStore } from '@/stores/notificationStore'
import { useWebSocket } from '@/composables/useWebSocket'
import { useI18n } from 'vue-i18n'

const { locale } = useI18n()
const userStore = useUserStore()
const notifStore = useNotificationStore()
const { connect, disconnect } = useWebSocket()

// Map i18n locale to Element Plus locale pack
const elLocale = computed(() => locale.value === 'en-US' ? enUs : zhCn)

const TYPE_CONFIG = {
  REVIEW_PENDING: { elType: 'info',    titleKey: 'notification.reviewPending' },
  APPROVED:       { elType: 'success', titleKey: 'notification.approved'      },
  REJECTED:       { elType: 'warning', titleKey: 'notification.rejected'      },
}

const { t } = useI18n()

function handleNotification(payload) {
  notifStore.addNotification(payload)
  const { elType, titleKey } = TYPE_CONFIG[payload.type] ?? { elType: 'info', titleKey: 'notification.default' }
  ElNotification({
    title: t(titleKey) + ': ' + payload.slideName,
    message: payload.message,
    type: elType,
    duration: 6000,
    position: 'top-right',
  })
}

function buildSubscriptions(role) {
  if (role === 'REVIEWER') {
    return [{ destination: '/topic/review-notification', callback: handleNotification }]
  }
  if (role === 'ANNOTATOR') {
    return [{ destination: '/user/queue/notifications', callback: handleNotification }]
  }
  return []
}

function startWebSocket() {
  if (!userStore.isLoggedIn) return
  const subs = buildSubscriptions(userStore.user?.role)
  if (subs.length) connect(userStore.token, subs)
}

watch(
  () => userStore.isLoggedIn,
  (loggedIn) => {
    if (loggedIn) {
      startWebSocket()
    } else {
      disconnect()
      notifStore.clearAll()
    }
  }
)

onMounted(() => {
  if (userStore.isLoggedIn) startWebSocket()
})
</script>

<style>
* {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}

body {
  background-color: var(--po-canvas);
  color: var(--po-ink);
}
</style>
