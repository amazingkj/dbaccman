import apiClient from './client'
import type {
  Role,
  UserRole,
  GrantRoleRequest,
  RevokeRoleRequest,
  PdbInfo,
} from '../types'

export const rolesApi = {
  // Get all available roles
  list: () =>
    apiClient.get<Role[]>('/roles'),

  // Get common/recommended roles
  getCommon: () =>
    apiClient.get<string[]>('/roles/common'),

  // Get roles for a specific user
  getUserRoles: (username: string) =>
    apiClient.get<UserRole[]>(`/roles/user/${encodeURIComponent(username)}`),

  // Grant roles to a user
  grant: (data: GrantRoleRequest) =>
    apiClient.post('/roles/grant', data),

  // Revoke roles from a user
  revoke: (data: RevokeRoleRequest) =>
    apiClient.post('/roles/revoke', data),
}

export const pdbApi = {
  // Get list of PDBs
  list: () =>
    apiClient.get<PdbInfo[]>('/pdb'),

  // Get current container
  getCurrent: () =>
    apiClient.get<{ container: string }>('/pdb/current'),

  // Switch to a PDB
  switch: (pdbName: string) =>
    apiClient.post(`/pdb/switch/${encodeURIComponent(pdbName)}`),
}
