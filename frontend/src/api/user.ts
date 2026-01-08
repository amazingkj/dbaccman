import apiClient from './client'

export interface UserTable {
  schemaName: string
  tableName: string
  rowCount: number | null
  lastAnalyzed: string | null
  tablespaceName: string | null
}

export interface UserTablespace {
  name: string
  maxBytes: string
  usedBytes: number
}

export interface UserTablespaceInfo {
  quotas: UserTablespace[]
  defaultTablespace: string | null
  temporaryTablespace: string | null
}

export interface UserQueryResult {
  columns: string[]
  rows: (string | null)[][]
  rowCount: number
  executionTimeMs: number
}

export interface ColumnInfo {
  name: string
  type: string
  nullable: boolean
  key: string | null
  defaultValue: string | null
  extra: string | null
}

export interface IndexInfo {
  name: string
  type: string
  unique: boolean
  columns: string[]
}

export interface TablespaceTableInfo {
  name: string
  engine: string | null
  rows: number
  size: number
  createTime: string | null
}

export const userApi = {
  // Get tables owned by the current user
  getTables: () =>
    apiClient.get<UserTable[]>('/user/tables'),

  // Get columns for a specific table
  getTableColumns: (table: string) =>
    apiClient.get<ColumnInfo[]>(`/user/tables/${encodeURIComponent(table)}/columns`),

  // Get indexes for a specific table
  getTableIndexes: (table: string) =>
    apiClient.get<IndexInfo[]>(`/user/tables/${encodeURIComponent(table)}/indexes`),

  // Get table data
  getTableData: (table: string, limit: number = 100) =>
    apiClient.get<{ columns: string[]; rows: (string | null)[][]; rowCount: number }>(
      `/user/tables/${encodeURIComponent(table)}/data?limit=${limit}`
    ),

  // Get tablespace quotas
  getTablespaces: () =>
    apiClient.get<UserTablespaceInfo>('/user/tablespaces'),

  // Get tables in a specific tablespace
  getTablesInTablespace: (tablespaceName: string) =>
    apiClient.get<TablespaceTableInfo[]>(`/user/tablespaces/${encodeURIComponent(tablespaceName)}/tables`),

  // Execute query (restricted to own schema)
  executeQuery: (query: string, limit: number = 1000) =>
    apiClient.post<UserQueryResult>('/user/query', { query, limit }),
}
