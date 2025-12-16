import apiClient from './client'
import type { TablespaceInfo, CreateTablespaceRequest, TableLocationRequest, TableInfo } from '../types'

export const tablespacesApi = {
  list: () =>
    apiClient.get<TablespaceInfo[]>('/tablespaces'),

  create: (data: CreateTablespaceRequest) =>
    apiClient.post('/tablespaces', data),

  delete: (name: string) =>
    apiClient.delete(`/tablespaces/${encodeURIComponent(name)}`),

  getTables: (name: string) =>
    apiClient.get<TableInfo[]>(`/tablespaces/${encodeURIComponent(name)}/tables`),

  moveTable: (data: TableLocationRequest) =>
    apiClient.post('/tablespaces/move-table', data),
}
