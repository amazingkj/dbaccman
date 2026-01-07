import apiClient from './client'
import type { DashboardStats } from '../types'
import { apiCache, CACHE_TTL, CACHE_KEYS } from '../utils/cache'

export const dashboardApi = {
  getStats: async () => {
    const cached = apiCache.get<DashboardStats>(CACHE_KEYS.DASHBOARD_STATS)
    if (cached) return { data: cached }
    const response = await apiClient.get<DashboardStats>('/dashboard/stats')
    apiCache.set(CACHE_KEYS.DASHBOARD_STATS, response.data, CACHE_TTL.SHORT)
    return response
  },

  // Cache invalidation
  invalidateStats: () => apiCache.invalidate(CACHE_KEYS.DASHBOARD_STATS),
}
