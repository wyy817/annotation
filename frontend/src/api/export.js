import http from './index'

export const exportApi = {
  /** Trigger export for a slide. Returns ExportTask (status: PENDING). */
  startExport(slideId, { format = 'COCO', patchSize = 256, maskType = 'SEMANTIC', maskScale = 1 } = {}) {
    const params = new URLSearchParams({ format, patchSize, maskType, maskScale })
    return http.post(`/slides/${slideId}/export?${params}`)
  },

  /** Poll export task status. */
  getTask(taskId) {
    return http.get(`/export-tasks/${taskId}`)
  },

  /** Start batch export for multiple slides. Returns one combined ExportTask. */
  startBatchExport(slideIds, { format = 'COCO', patchSize = 256, maskType = 'SEMANTIC', maskScale = 1 } = {}) {
    return http.post('/export/batch', { slideIds, format, patchSize, maskType, maskScale })
  },

  /** Download ZIP as blob via axios (carries JWT token). Returns Promise<Blob>. */
  downloadFile(taskId) {
    return http.get(`/export-tasks/${taskId}/download`, { responseType: 'blob' })
  }
}
