import http from './index'

export const annotationApi = {
  // ── Labels ──────────────────────────────────────────────────────────────
  listLabels(slideId) {
    return http.get(`/slides/${slideId}/labels`)
  },
  createLabel(slideId, data) {
    return http.post(`/slides/${slideId}/labels`, data)
  },
  updateLabel(id, data) {
    return http.put(`/labels/${id}`, data)
  },
  deleteLabel(id) {
    return http.delete(`/labels/${id}`)
  },

  // ── Annotations ─────────────────────────────────────────────────────────
  list(slideId) {
    return http.get(`/slides/${slideId}/annotations`)
  },
  /**
   * Batch-replace all annotations for a slide.
   * coordinates must already be JSON.stringified before calling this.
   */
  saveBatch(slideId, annotations) {
    return http.post(`/slides/${slideId}/annotations/batch`, annotations)
  },
  markComplete(slideId) {
    return http.patch(`/slides/${slideId}/annotations/complete`)
  }
}
