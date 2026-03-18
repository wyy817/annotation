import http from './index'

export const authApi = {
  login: (username, password) =>
    http.post('/auth/login', { username, password }),

  me: () => http.get('/auth/me')
}
