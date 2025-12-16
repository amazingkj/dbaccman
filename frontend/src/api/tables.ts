import apiClient from './client'
import type { DatabaseInfo, TableInfo, IndexInfo, CreateIndexRequest } from '../types'

export const tablesApi = {
  getDatabases: () =>
    apiClient.get<DatabaseInfo[]>('/databases'),

  getTables: (database: string) =>
    apiClient.get<TableInfo[]>(`/tables/${encodeURIComponent(database)}`),

  getIndexes: (database: string, table: string) =>
    apiClient.get<IndexInfo[]>(`/tables/${encodeURIComponent(database)}/${encodeURIComponent(table)}/indexes`),

  createIndex: (data: CreateIndexRequest) =>
    apiClient.post('/indexes', data),

  dropIndex: (database: string, table: string, indexName: string) =>
    apiClient.delete(`/indexes/${encodeURIComponent(database)}/${encodeURIComponent(table)}/${encodeURIComponent(indexName)}`),
}
