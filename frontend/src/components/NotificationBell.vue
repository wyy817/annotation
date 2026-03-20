<template>
  <el-popover
    v-model:visible="popoverVisible"
    placement="bottom-end"
    :width="340"
    trigger="click"
    popper-class="notif-popover"
  >
    <!-- Trigger: bell button with badge -->
    <template #reference>
      <el-badge :value="unreadCount || ''" :hidden="unreadCount === 0" type="danger">
        <el-tooltip content="通知" placement="bottom">
          <el-button size="small" :icon="Bell" circle @click="onOpen" />
        </el-tooltip>
      </el-badge>
    </template>

    <!-- Popover content -->
    <div class="notif-header">
      <span class="notif-title">通知</span>
      <el-button
        v-if="notifications.length"
        link
        size="small"
        @click="notifStore.markAllRead()"
      >全部标为已读</el-button>
    </div>

    <el-scrollbar max-height="360px">
      <div v-if="!notifications.length" class="notif-empty">暂无通知</div>
      <div
        v-for="n in notifications"
        :key="n.id"
        class="notif-item"
        :class="{ unread: !n.read }"
        @click="handleItemClick(n)"
      >
        <el-icon class="notif-icon" :class="n.type.toLowerCase()">
          <component :is="typeIcon(n.type)" />
        </el-icon>
        <div class="notif-body">
          <p class="notif-msg">{{ n.message }}</p>
          <p class="notif-slide">{{ n.slideName }}</p>
          <p class="notif-time">{{ formatTime(n.timestamp) }}</p>
        </div>
        <div v-if="!n.read" class="unread-dot" />
      </div>
    </el-scrollbar>
  </el-popover>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { Bell, SuccessFilled, WarningFilled, InfoFilled } from '@element-plus/icons-vue'
import { useNotificationStore } from '@/stores/notificationStore'

const notifStore = useNotificationStore()
const router = useRouter()
const popoverVisible = ref(false)

const notifications = computed(() => notifStore.notifications)
const unreadCount = computed(() => notifStore.unreadCount)

function onOpen() {
  // Mark all read when the panel is opened
  if (popoverVisible.value) {
    notifStore.markAllRead()
  }
}

function typeIcon(type) {
  if (type === 'APPROVED') return SuccessFilled
  if (type === 'REJECTED') return WarningFilled
  return InfoFilled
}

function handleItemClick(n) {
  n.read = true
  popoverVisible.value = false
  if (n.slideId) {
    router.push({ name: 'slideViewer', params: { id: n.slideId } })
  }
}

function formatTime(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  if (isNaN(d.getTime())) return ts
  const pad = (v) => String(v).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
</script>

<style scoped>
.notif-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px 6px;
  border-bottom: 1px solid #ebeef5;
}
.notif-title {
  font-weight: 600;
  font-size: 14px;
}
.notif-empty {
  text-align: center;
  color: #909399;
  padding: 32px 0;
  font-size: 13px;
}
.notif-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 12px;
  cursor: pointer;
  border-bottom: 1px solid #f4f4f5;
  position: relative;
  transition: background 0.15s;
}
.notif-item:hover { background: #f5f7fa; }
.notif-item.unread { background: #fef9ec; }
.notif-item.unread:hover { background: #fdf3d0; }
.notif-icon {
  font-size: 18px;
  margin-top: 2px;
  flex-shrink: 0;
}
.notif-icon.review_pending { color: #409eff; }
.notif-icon.approved       { color: #67c23a; }
.notif-icon.rejected       { color: #e6a23c; }
.notif-body { flex: 1; min-width: 0; }
.notif-msg {
  font-size: 13px;
  color: #303133;
  line-height: 1.4;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.notif-slide {
  font-size: 12px;
  color: #606266;
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.notif-time {
  font-size: 11px;
  color: #909399;
  margin-top: 2px;
}
.unread-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #f56c6c;
  flex-shrink: 0;
  margin-top: 6px;
}
</style>
