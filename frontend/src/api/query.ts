import apiClient from './client'

export interface QueryResult {
  columns: string[]
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

export const queryApi = {
  execute: (query: string, account?: string) =>
    apiClient.post<QueryResult>('/query/execute', { query, account }),

  getAuditLogs: (params?: { limit?: number; action?: string; user?: string }) =>
    apiClient.get<AuditLogEntry[]>('/query/audit-logs', { params }),
}