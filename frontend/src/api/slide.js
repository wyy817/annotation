import http from './index'

export const slideApi = {
  list(workspaceId) {
    return http.get(`/workspaces/${workspaceId}/slides`)
  },

  get(id) {
    return http.get(`/slides/${id}`)
  },

  upload(workspaceId, files, onProgress) {
    const form = new FormData()
    files.forEach((f) => form.append('files', f))
    return http.post(`/workspaces/${workspaceId}/slides/upload`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: onProgress,
      timeout: 0 // no timeout for large uploads
    })
  },

  remove(id) {
    return http.delete(`/slides/${id}`)
  },

  move(id, workspaceId) {
    return http.patch(`/slides/${id}/move`, { workspaceId })
  }
}
