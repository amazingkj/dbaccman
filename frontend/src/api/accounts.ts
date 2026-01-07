import apiClient from './client'
import type {
  Account,
  CreateAccountRequest,
  ExpiringAccount,
  SetTablespaceRequest,
  CloneAccountRequest,
  BatchCreateAccountRequest,
  BatchDeleteRequest,
  BatchUnlockRequest,
  BatchOperationResult,
  PaginatedAccountsResponse,
} from '../types'

export const accountsApi = {
  list: () =>
    apiClient.get<Account[]>('/accounts'),

  listPaginated: (page: number, pageSize: number, sortBy?: string, sortOrder?: 'asc' | 'desc', filter?: string) => {
    let url = `/accounts?page=${page}&pageSize=${pageSize}`
    if (sortBy) {
      url += `&sortBy=${sortBy}&sortOrder=${sortOrder || 'asc'}`
    }
    if (filter) {
      url += `&filter=${filter}`
    }
    return apiClient.get<PaginatedAccountsResponse>(url)
  },

  create: (data: CreateAccountRequest) =>
    apiClient.post<Account>('/accounts', data),

  changePassword: (username: string, host: string, password: string) =>
    apiClient.put(`/accounts/${encodeURIComponent(username)}@${encodeURIComponent(host)}/password`, { password }),

  delete: (username: string, host: string) =>
    apiClient.delete(`/accounts/${encodeURIComponent(username)}@${encodeURIComponent(host)}`),

  getExpiring: (days: number = 30) =>
    apiClient.get<ExpiringAccount[]>(`/accounts/expiring?days=${days}`),

  unlock: (username: string, host: string) =>
    apiClient.post(`/accounts/${encodeURIComponent(username)}@${encodeURIComponent(host)}/unlock`),

  setTablespace: (data: SetTablespaceRequest) =>
    apiClient.post('/accounts/tablespace', data),

  // Clone account
  clone: (data: CloneAccountRequest) =>
    apiClient.post<Account>('/accounts/clone', data),

  // Batch operations
  batchCreate: (data: BatchCreateAccountRequest) =>
    apiClient.post<BatchOperationResult>('/accounts/batch/create', data),

  batchDelete: (data: BatchDeleteRequest) =>
    apiClient.post<BatchOperationResult>('/accounts/batch/delete', data),

  batchUnlock: (data: BatchUnlockRequest) =>
    apiClient.post<BatchOperationResult>('/accounts/batch/unlock', data),

  // Export
  exportCsv: (includePermissions: boolean = false) =>
    apiClient.get(`/accounts/export?format=csv&includePermissions=${includePermissions}`, {
      responseType: 'blob',
    }),
}
