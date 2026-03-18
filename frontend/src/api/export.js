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

  /** Start batch export for multiple slides. Returns one combined ExportTask. */
  startBatchExport(slideIds) {
    return http.post('/export/batch', { slideIds })
  },

  /** Download ZIP as blob via axios (carries JWT token). Returns Promise<Blob>. */
  downloadFile(taskId) {
    return http.get(`/export-tasks/${taskId}/download`, { responseType: 'blob' })
  }
}
