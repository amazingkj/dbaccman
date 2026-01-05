import apiClient from './client'
import type { DatabaseInfo, TableInfo, IndexInfo } from '../types'

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
  getDatabases: () =>
    apiClient.get<DatabaseInfo[]>('/databases'),

  getTables: (database: string) =>
    apiClient.get<TableInfo[]>(`/tables/${encodeURIComponent(database)}`),

  getColumns: (database: string, table: string) =>
    apiClient.get<ColumnInfo[]>(`/tables/${encodeURIComponent(database)}/${encodeURIComponent(table)}/columns`),

  getIndexes: (database: string, table: string) =>
    apiClient.get<IndexInfo[]>(`/tables/${encodeURIComponent(database)}/${encodeURIComponent(table)}/indexes`),

  getTableData: (database: string, table: string, limit: number = 100) =>
    apiClient.get<TableDataResult>(`/tables/${encodeURIComponent(database)}/${encodeURIComponent(table)}/data?limit=${limit}`),

  gatherStats: (database: string, table?: string) =>
    apiClient.post<{ success: boolean }>(
      `/tables/${encodeURIComponent(database)}/gather-stats${table ? `?table=${encodeURIComponent(table)}` : ''}`
    ),
}
