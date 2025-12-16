import apiClient from './client'
import type { Permission, GrantPermissionRequest } from '../types'

export const permissionsApi = {
  getUserPermissions: (username: string, host: string) =>
    apiClient.get<Permission[]>(`/permissions/${encodeURIComponent(username)}@${encodeURIComponent(host)}`),

  grant: (data: GrantPermissionRequest) =>
    apiClient.post('/permissions/grant', data),

  revoke: (data: GrantPermissionRequest) =>
    apiClient.post('/permissions/revoke', data),
}
