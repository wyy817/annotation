import http from './index'

export const reviewApi = {
  /** List all PENDING_REVIEW slides */
  getPending: () => http.get('/reviews/pending'),

  /** List all APPROVED slides */
  getApproved: () => http.get('/reviews/approved'),

  /** Approve a slide */
  approve: (slideId) => http.patch(`/reviews/${slideId}/approve`),

  /** Reject a slide with a comment */
  reject: (slideId, comment) => http.patch(`/reviews/${slideId}/reject`, { comment }),

  /** List annotators with progress stats */
  getAnnotators: () => http.get('/users/annotators')
}
