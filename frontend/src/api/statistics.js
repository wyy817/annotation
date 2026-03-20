import http from './index'

export const statisticsApi = {
  /** Get annotation statistics, optionally filtered by workspace */
  getAnnotationStats: (workspaceId) =>
    http.get('/statistics/annotations', { params: workspaceId ? { workspaceId } : {} })
}
