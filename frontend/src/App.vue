<template>
  <router-view />
</template>

<script setup>
import { watch, onMounted } from 'vue'
import { ElNotification } from 'element-plus'
import { useUserStore } from '@/stores/userStore'
import { useNotificationStore } from '@/stores/notificationStore'
import { useWebSocket } from '@/composables/useWebSocket'

const userStore = useUserStore()
const notifStore = useNotificationStore()
const { connect, disconnect } = useWebSocket()

const TYPE_CONFIG = {
  REVIEW_PENDING: { elType: 'info',    title: '待审核通知' },
  APPROVED:       { elType: 'success', title: '审核通过'   },
  REJECTED:       { elType: 'warning', title: '审核拒绝'   },
}

function handleNotification(payload) {
  notifStore.addNotification(payload)
  const { elType, title } = TYPE_CONFIG[payload.type] ?? { elType: 'info', title: '通知' }
  ElNotification({
    title: `${title}：${payload.slideName}`,
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
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  background-color: #f5f7fa;
  color: #303133;
}
</style>
