import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

const TOKEN_KEY = 'wsi_token'
const USER_KEY  = 'wsi_user'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) || '')
  const user  = ref(JSON.parse(localStorage.getItem(USER_KEY) || 'null'))

  const isLoggedIn   = computed(() => !!token.value)
  const isReviewer   = computed(() => user.value?.role === 'REVIEWER')
  const isAnnotator  = computed(() => user.value?.role === 'ANNOTATOR')

  function setAuth(newToken, newUser) {
    token.value = newToken
    user.value  = newUser
    localStorage.setItem(TOKEN_KEY, newToken)
    localStorage.setItem(USER_KEY, JSON.stringify(newUser))
  }

  function logout() {
    token.value = ''
    user.value  = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }

  return { token, user, isLoggedIn, isReviewer, isAnnotator, setAuth, logout }
})
