import { createRouter, createWebHistory } from 'vue-router'
import WorkspaceView from '@/views/WorkspaceView.vue'

const routes = [
  {
    path: '/',
    name: 'workspaces',
    component: WorkspaceView
  },
  {
    path: '/workspace/:id',
    name: 'fileBrowser',
    // Placeholder — implemented in Phase 6
    component: () => import('@/views/FileBrowserView.vue')
  },
  {
    path: '/slide/:id/view',
    name: 'slideViewer',
    // Placeholder — implemented in Phase 7
    component: () => import('@/views/SlideViewerView.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
