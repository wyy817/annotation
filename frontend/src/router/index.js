import { createRouter, createWebHistory } from 'vue-router'
import WorkspaceView from '@/views/WorkspaceView.vue'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true }
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/RegisterView.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    name: 'workspaces',
    component: WorkspaceView
  },
  {
    path: '/workspace/:id',
    name: 'fileBrowser',
    component: () => import('@/views/FileBrowserView.vue')
  },
  {
    path: '/slide/:id/view',
    name: 'slideViewer',
    component: () => import('@/views/SlideViewerView.vue')
  },
  {
    path: '/review',
    name: 'reviewDashboard',
    component: () => import('@/views/ReviewDashboardView.vue')
  },
  {
    path: '/admin',
    name: 'admin',
    component: () => import('@/views/AdminView.vue'),
    meta: { requiresReviewer: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const token = localStorage.getItem('wsi_token')
  if (!to.meta.public && !token) {
    return { name: 'login' }
  }
  if (to.meta.requiresReviewer) {
    const user = JSON.parse(localStorage.getItem('wsi_user') || 'null')
    if (user?.role !== 'REVIEWER') {
      return { name: 'workspaces' }
    }
  }
  // Reviewers always start at review dashboard
  if (to.name === 'workspaces') {
    const user = JSON.parse(localStorage.getItem('wsi_user') || 'null')
    if (user?.role === 'REVIEWER') {
      return { name: 'reviewDashboard' }
    }
  }
})

export default router
