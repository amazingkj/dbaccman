import apiClient from './client'
import type { TablespaceInfo, CreateTablespaceRequest, TableLocationRequest, TableInfo } from '../types'
import { apiCache, CACHE_TTL, CACHE_KEYS } from '../utils/cache'

export const tablespacesApi = {
  list: async () => {
    const cached = apiCache.get<TablespaceInfo[]>(CACHE_KEYS.TABLESPACES)
    if (cached) return { data: cached }
    const response = await apiClient.get<TablespaceInfo[]>('/tablespaces')
    apiCache.set(CACHE_KEYS.TABLESPACES, response.data, CACHE_TTL.MEDIUM)
    return response
  },

  create: async (data: CreateTablespaceRequest) => {
    const response = await apiClient.post('/tablespaces', data)
    apiCache.invalidate(CACHE_KEYS.TABLESPACES)
    return response
  },

  delete: async (name: string) => {
    const response = await apiClient.delete(`/tablespaces/${encodeURIComponent(name)}`)
    apiCache.invalidate(CACHE_KEYS.TABLESPACES)
    apiCache.invalidate(CACHE_KEYS.TABLESPACE_TABLES(name))
    return response
  },

  getTables: async (name: string) => {
    const cacheKey = CACHE_KEYS.TABLESPACE_TABLES(name)
    const cached = apiCache.get<TableInfo[]>(cacheKey)
    if (cached) return { data: cached }
    const response = await apiClient.get<TableInfo[]>(`/tablespaces/${encodeURIComponent(name)}/tables`)
    apiCache.set(cacheKey, response.data, CACHE_TTL.MEDIUM)
    return response
  },

  moveTable: async (data: TableLocationRequest) => {
    const response = await apiClient.post('/tablespaces/move-table', data)
    // Invalidate both source and target tablespace caches
    apiCache.invalidatePrefix('tablespace:')
    return response
  },

  // Cache invalidation
  invalidateList: () => apiCache.invalidate(CACHE_KEYS.TABLESPACES),
  invalidateTables: (name: string) => apiCache.invalidate(CACHE_KEYS.TABLESPACE_TABLES(name)),
}
