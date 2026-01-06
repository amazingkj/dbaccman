import apiClient from './client'

export interface ColumnMetadata {
  name: string
  type: string
  isAutoIncrement: boolean
  isNullable: boolean
  isPrimaryKey: boolean
}

export interface QueryResult {
  columns: string[]
  columnMetadata?: ColumnMetadata[]
  rows: (string | null)[][]
  rowCount: number
  executionTimeMs: number
  affectedRows?: number
  isSelectQuery: boolean
}

export interface QueryError {
  error: string
  executionTimeMs?: number
}

export interface AuditLogEntry {
  timestamp: string
  action: string
  user: string | null
  ipAddress: string | null
  message: string
  target?: string
  details?: string
}

export interface SchemaInfo {
  currentSchema: string
  availableSchemas: string[]
}

export const queryApi = {
  execute: (query: string, account?: string, limit?: number) =>
    apiClient.post<QueryResult>('/query/execute', { query, account, limit }),

  getAuditLogs: (params?: { limit?: number; action?: string; user?: string }) =>
    apiClient.get<AuditLogEntry[]>('/query/audit-logs', { params }),

  getSchemas: () =>
    apiClient.get<SchemaInfo>('/query/schemas'),

  switchSchema: (schema: string) =>
    apiClient.post<{ success: boolean; schema: string }>('/query/switch-schema', { schema }),
}