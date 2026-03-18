import http from './index'

export const exportApi = {
  /** Trigger export for a slide. Returns ExportTask (status: PENDING). */
  startExport(slideId) {
    return http.post(`/slides/${slideId}/export`)
  },

  /** Poll export task status. */
  getTask(taskId) {
    return http.get(`/export-tasks/${taskId}`)
  },

  /** Direct download URL (bypasses axios — binary stream). */
  downloadUrl(taskId) {
    return `/api/export-tasks/${taskId}/download`
  }
}
