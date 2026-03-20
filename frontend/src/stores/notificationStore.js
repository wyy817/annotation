import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

const MAX_NOTIFICATIONS = 50

export const useNotificationStore = defineStore('notification', () => {
  const notifications = ref([])

  const unreadCount = computed(
    () => notifications.value.filter((n) => !n.read).length
  )

  function addNotification(payload) {
    notifications.value.unshift({
      id: Date.now() + Math.random(),
      type: payload.type,
      slideId: payload.slideId,
      slideName: payload.slideName,
      message: payload.message,
      timestamp: payload.timestamp,
      read: false,
    })
    // Keep list bounded
    if (notifications.value.length > MAX_NOTIFICATIONS) {
      notifications.value = notifications.value.slice(0, MAX_NOTIFICATIONS)
    }
  }

  function markAllRead() {
    notifications.value.forEach((n) => (n.read = true))
  }

  function clearAll() {
    notifications.value = []
  }

  return { notifications, unreadCount, addNotification, markAllRead, clearAll }
})
