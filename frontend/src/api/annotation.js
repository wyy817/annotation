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
  },

  // ── Version history ──────────────────────────────────────────────────────
  listVersions(slideId) {
    return http.get(`/slides/${slideId}/versions`)
  },
  getVersionAnnotations(slideId, versionId) {
    return http.get(`/slides/${slideId}/versions/${versionId}/annotations`)
  },

  // ── Import ────────────────────────────────────────────────────────────────
  /**
   * Import annotations from a file.
   * @param {number}  slideId
   * @param {File}    file      - annotation file (COCO/CUSTOM_JSON: .json; YOLO: .txt)
   * @param {string}  format    - 'COCO' | 'CUSTOM_JSON' | 'YOLO'
   * @param {string}  mode      - 'REPLACE' | 'MERGE'
   * @param {File}    [classFile] - (YOLO only) classes.txt
   */
  importAnnotations(slideId, file, format = 'COCO', mode = 'REPLACE', classFile = null) {
    const form = new FormData()
    form.append('file', file)
    form.append('format', format)
    form.append('mode', mode)
    if (classFile) form.append('classFile', classFile)
    return http.post(`/slides/${slideId}/annotations/import`, form, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  }
}
