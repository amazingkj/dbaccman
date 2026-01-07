import apiClient from './client'
import type {
  Role,
  UserRole,
  GrantRoleRequest,
  RevokeRoleRequest,
  PdbInfo,
} from '../types'
import { apiCache, CACHE_TTL, CACHE_KEYS } from '../utils/cache'

export const rolesApi = {
  // Get all available roles
  list: async () => {
    const cached = apiCache.get<Role[]>(CACHE_KEYS.ROLES)
    if (cached) return { data: cached }
    const response = await apiClient.get<Role[]>('/roles')
    apiCache.set(CACHE_KEYS.ROLES, response.data, CACHE_TTL.MEDIUM)
    return response
  },

  // Get common/recommended roles
  getCommon: async () => {
    const cached = apiCache.get<string[]>(CACHE_KEYS.COMMON_ROLES)
    if (cached) return { data: cached }
    const response = await apiClient.get<string[]>('/roles/common')
    apiCache.set(CACHE_KEYS.COMMON_ROLES, response.data, CACHE_TTL.LONG)
    return response
  },

  // Get roles for a specific user
  getUserRoles: (username: string) =>
    apiClient.get<UserRole[]>(`/roles/user/${encodeURIComponent(username)}`),

  // Grant roles to a user
  grant: (data: GrantRoleRequest) =>
    apiClient.post('/roles/grant', data),

  // Revoke roles from a user
  revoke: (data: RevokeRoleRequest) =>
    apiClient.post('/roles/revoke', data),

  // Cache invalidation
  invalidateList: () => apiCache.invalidate(CACHE_KEYS.ROLES),
}

export const pdbApi = {
  // Get list of PDBs
  list: async () => {
    const cached = apiCache.get<PdbInfo[]>(CACHE_KEYS.PDBS)
    if (cached) return { data: cached }
    const response = await apiClient.get<PdbInfo[]>('/pdb')
    apiCache.set(CACHE_KEYS.PDBS, response.data, CACHE_TTL.LONG)
    return response
  },

  // Get current container
  getCurrent: () =>
    apiClient.get<{ container: string }>('/pdb/current'),

  // Switch to a PDB
  switch: (pdbName: string) =>
    apiClient.post(`/pdb/switch/${encodeURIComponent(pdbName)}`),

  // Cache invalidation
  invalidateList: () => apiCache.invalidate(CACHE_KEYS.PDBS),
}
