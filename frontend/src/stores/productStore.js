import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { useUserStore } from '@/stores/userStore'
import {
  DEMO_IDENTITIES,
  IS_DEMO_MODE,
  PRODUCT_ROLES,
  getProductRole,
  normalizeProductRole,
} from '@/config/product'

const ROLE_STORAGE_KEY = 'pathorchestra_demo_role'

function readStoredRole() {
  const stored = localStorage.getItem(ROLE_STORAGE_KEY)
  return PRODUCT_ROLES.some((role) => role.id === stored) ? stored : 'ANNOTATOR'
}

export const useProductStore = defineStore('product', () => {
  const userStore = useUserStore()
  const demoRole = ref(readStoredRole())

  const effectiveRole = computed(() => (
    IS_DEMO_MODE
      ? demoRole.value
      : normalizeProductRole(userStore.user?.role)
  ))

  const roleDefinition = computed(() => getProductRole(effectiveRole.value))

  const identity = computed(() => {
    if (IS_DEMO_MODE) return DEMO_IDENTITIES[effectiveRole.value]

    const backendUser = userStore.user
    if (!backendUser) return DEMO_IDENTITIES.ANNOTATOR

    return {
      username: backendUser.username,
      displayName: backendUser.displayName || backendUser.username,
      role: effectiveRole.value,
    }
  })

  function setDemoRole(role) {
    if (!IS_DEMO_MODE) return
    const nextRole = normalizeProductRole(role)
    demoRole.value = nextRole
    localStorage.setItem(ROLE_STORAGE_KEY, nextRole)
  }

  return {
    demoRole,
    effectiveRole,
    roleDefinition,
    identity,
    isDemoMode: IS_DEMO_MODE,
    setDemoRole,
  }
})
