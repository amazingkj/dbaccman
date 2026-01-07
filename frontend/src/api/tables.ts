import apiClient from './client'
import type { DatabaseInfo, TableInfo, IndexInfo } from '../types'
import { apiCache, CACHE_TTL, CACHE_KEYS } from '../utils/cache'

export interface TableDataResult {
  columns: string[]
  rows: (string | null)[][]
  rowCount: number
}

export interface ColumnInfo {
  name: string
  type: string
  nullable: boolean
  key: string | null
  defaultValue: string | null
  extra: string | null
}

export const tablesApi = {
  getDatabases: async () => {
    const cached = apiCache.get<DatabaseInfo[]>(CACHE_KEYS.DATABASES)
    if (cached) return { data: cached }
    const response = await apiClient.get<DatabaseInfo[]>('/databases')
    apiCache.set(CACHE_KEYS.DATABASES, response.data, CACHE_TTL.MEDIUM)
    return response
  },

  getTables: async (database: string) => {
    const cacheKey = CACHE_KEYS.TABLES(database)
    const cached = apiCache.get<TableInfo[]>(cacheKey)
    if (cached) return { data: cached }
    const response = await apiClient.get<TableInfo[]>(`/tables/${encodeURIComponent(database)}`)
    apiCache.set(cacheKey, response.data, CACHE_TTL.MEDIUM)
    return response
  },

  getColumns: async (database: string, table: string) => {
    const cacheKey = CACHE_KEYS.COLUMNS(database, table)
    const cached = apiCache.get<ColumnInfo[]>(cacheKey)
    if (cached) return { data: cached }
    const response = await apiClient.get<ColumnInfo[]>(`/tables/${encodeURIComponent(database)}/${encodeURIComponent(table)}/columns`)
    apiCache.set(cacheKey, response.data, CACHE_TTL.LONG)
    return response
  },

  getIndexes: async (database: string, table: string) => {
    const cacheKey = CACHE_KEYS.INDEXES(database, table)
    const cached = apiCache.get<IndexInfo[]>(cacheKey)
    if (cached) return { data: cached }
    const response = await apiClient.get<IndexInfo[]>(`/tables/${encodeURIComponent(database)}/${encodeURIComponent(table)}/indexes`)
    apiCache.set(cacheKey, response.data, CACHE_TTL.LONG)
    return response
  },

  getTableData: (database: string, table: string, limit: number = 100) =>
    apiClient.get<TableDataResult>(`/tables/${encodeURIComponent(database)}/${encodeURIComponent(table)}/data?limit=${limit}`),

  gatherStats: (database: string, table?: string) =>
    apiClient.post<{ success: boolean }>(
      `/tables/${encodeURIComponent(database)}/gather-stats${table ? `?table=${encodeURIComponent(table)}` : ''}`
    ),

  // Cache invalidation methods
  invalidateDatabases: () => apiCache.invalidate(CACHE_KEYS.DATABASES),
  invalidateTables: (database: string) => apiCache.invalidate(CACHE_KEYS.TABLES(database)),
  invalidateSchema: (database: string, table: string) => {
    apiCache.invalidate(CACHE_KEYS.COLUMNS(database, table))
    apiCache.invalidate(CACHE_KEYS.INDEXES(database, table))
  },
}
