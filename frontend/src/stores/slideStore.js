import { defineStore } from 'pinia'
import { ref } from 'vue'
import { slideApi } from '@/api/slide'

export const useSlideStore = defineStore('slide', () => {
  const slides = ref([])
  const loading = ref(false)
  const currentWorkspaceId = ref(null)

  async function fetchAll(workspaceId) {
    loading.value = true
    currentWorkspaceId.value = workspaceId
    try {
      const res = await slideApi.list(workspaceId)
      slides.value = res.data ?? []
    } finally {
      loading.value = false
    }
  }

  async function remove(id) {
    await slideApi.remove(id)
    slides.value = slides.value.filter((s) => s.id !== id)
  }

  async function move(id, targetWorkspaceId) {
    await slideApi.move(id, targetWorkspaceId)
    slides.value = slides.value.filter((s) => s.id !== id)
  }

  // After uploading, add the returned PENDING slides to list
  function addSlides(newSlides) {
    slides.value.unshift(...newSlides)
  }

  // Refresh a single slide's data (for status polling)
  function updateSlide(updated) {
    const idx = slides.value.findIndex((s) => s.id === updated.id)
    if (idx !== -1) slides.value[idx] = updated
  }

  return { slides, loading, currentWorkspaceId, fetchAll, remove, move, addSlides, updateSlide }
})
