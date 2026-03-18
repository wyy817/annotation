import http from './index'

export const workspaceApi = {
  list() {
    return http.get('/workspaces')
  },

  get(id) {
    return http.get(`/workspaces/${id}`)
  },

  create(payload) {
    // payload: { name, description }
    return http.post('/workspaces', payload)
  },

  update(id, payload) {
    // payload: { name, description }
    return http.put(`/workspaces/${id}`, payload)
  },

  remove(id) {
    return http.delete(`/workspaces/${id}`)
  }
}
