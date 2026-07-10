import apiClient from './client'
import type { ProvisionRequest, ProvisionPlan, ProvisionResult } from '../types'
import { apiCache, CACHE_KEYS } from '../utils/cache'

export const provisioningApi = {
  preview: (data: ProvisionRequest) =>
    apiClient.post<ProvisionPlan>('/provisioning/preview', data),

  execute: async (data: ProvisionRequest) => {
    const response = await apiClient.post<ProvisionResult>('/provisioning/execute', data)
    // New account and tablespaces were (partially) created - drop stale caches
    apiCache.invalidate(CACHE_KEYS.TABLESPACES)
    apiCache.invalidatePrefix('accounts:')
    return response
  },
}
