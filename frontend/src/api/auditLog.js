import http from './index'

export const auditLogApi = {
  /**
   * Query audit logs with optional filters.
   * @param {object} params - slideId, userId, action, from, to, page, size
   */
  list(params = {}) {
    return http.get('/audit-logs', { params })
  }
}
