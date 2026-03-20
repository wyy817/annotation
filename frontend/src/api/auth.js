import http from './index'

export const authApi = {
  login: (username, password) =>
    http.post('/auth/login', { username, password }),

  register: (username, password, confirmPassword, role) =>
    http.post('/auth/register', { username, password, confirmPassword, role }),

  me: () => http.get('/auth/me')
}

export const adminApi = {
  listPending: () => http.get('/admin/users/pending'),
  listAll:     () => http.get('/admin/users'),
  approve:     (id) => http.put(`/admin/users/${id}/approve`),
  disable:     (id) => http.put(`/admin/users/${id}/disable`)
}
