import { createRouter, createWebHashHistory, createWebHistory } from 'vue-router'
import { IS_DEMO_MODE, normalizeProductRole } from '@/config/product'

const productRoutes = [
  {
    path: '/',
    name: 'home',
    redirect: { name: 'cases' }
  },
  {
    path: '/cases',
    name: 'cases',
    component: () => import('@/views/CaseDashboardView.vue'),
    meta: { titleKey: 'po.queue.title' }
  },
  {
    path: '/cases/:caseId',
    name: 'caseReview',
    component: () => import('@/views/CaseReviewView.vue'),
    meta: { titleKey: 'po.viewer.title' }
  },
  {
    path: '/feedback',
    name: 'feedbackPool',
    component: () => import('@/views/FeedbackPoolView.vue'),
    meta: { titleKey: 'po.feedback.title' }
  }
]

const localOnlyRoutes = [
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
    path: '/workspaces',
    name: 'workspaces',
    component: () => import('@/views/WorkspaceView.vue')
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
    meta: { allowedRoles: ['REVIEWER', 'SYSTEM_ADMIN'] }
  }
]

const routes = [
  ...productRoutes,
  ...(IS_DEMO_MODE ? [] : localOnlyRoutes),
  {
    path: '/:pathMatch(.*)*',
    redirect: { name: 'cases' }
  }
]

const router = createRouter({
  history: IS_DEMO_MODE ? createWebHashHistory() : createWebHistory(),
  routes
})

router.beforeEach((to) => {
  if (IS_DEMO_MODE) {
    return true
  }

  const token = localStorage.getItem('wsi_token')
  if (!to.meta.public && !token) {
    return { name: 'login' }
  }

  if (to.meta.allowedRoles) {
    const user = JSON.parse(localStorage.getItem('wsi_user') || 'null')
    const role = normalizeProductRole(user?.role)
    if (!to.meta.allowedRoles.includes(role)) {
      return { name: 'cases' }
    }
  }

  return true
})

export default router
