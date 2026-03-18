import { defineStore } from 'pinia'
import { ref } from 'vue'
import { workspaceApi } from '@/api/workspace'

export const useWorkspaceStore = defineStore('workspace', () => {
  const workspaces = ref([])
  const loading = ref(false)

  async function fetchAll() {
    loading.value = true
    try {
      const res = await workspaceApi.list()
      workspaces.value = res.data ?? []
    } finally {
      loading.value = false
    }
  }

  async function create(payload) {
    const res = await workspaceApi.create(payload)
    workspaces.value.unshift(res.data)
    return res.data
  }

  async function update(id, payload) {
    const res = await workspaceApi.update(id, payload)
    const idx = workspaces.value.findIndex((w) => w.id === id)
    if (idx !== -1) workspaces.value[idx] = res.data
    return res.data
  }

  async function remove(id) {
    await workspaceApi.remove(id)
    workspaces.value = workspaces.value.filter((w) => w.id !== id)
  }

  return { workspaces, loading, fetchAll, create, update, remove }
})
