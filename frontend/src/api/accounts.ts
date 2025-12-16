import apiClient from './client'
import type { Account, CreateAccountRequest, ExpiringAccount } from '../types'

export const accountsApi = {
  list: () =>
    apiClient.get<Account[]>('/accounts'),

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
}
