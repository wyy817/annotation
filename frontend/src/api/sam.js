import request from './index'

export const samApi = {
  /**
   * Run SAM/MobileSAM prediction on a slide region.
   * @param {number} slideId
   * @param {{ points?: number[][], point_labels?: number[], box?: number[], level?: number, patch_size?: number }} payload
   */
  predict(slideId, payload) {
    return request.post(`/slides/${slideId}/sam/predict`, payload)
  }
}
