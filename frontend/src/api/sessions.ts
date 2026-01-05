import apiClient from './client'
import type { SessionInfo } from '../types'

interface BulkKillResult {
  success: number
  failed: number
  errors: string[]
  message: string
}

interface SessionTarget {
  pid: number
  serialNum?: number | null
}

export const sessionsApi = {
  getActive: () =>
    apiClient.get<SessionInfo[]>('/sessions'),

  kill: (pid: number, serialNum?: number | null) => {
    const params = serialNum ? `?serialNum=${serialNum}` : ''
    return apiClient.delete(`/sessions/${pid}${params}`)
  },

  killQuery: (pid: number, serialNum?: number | null) => {
    const params = serialNum ? `?serialNum=${serialNum}` : ''
    return apiClient.delete(`/sessions/${pid}/query${params}`)
  },

  bulkKill: (sessions: SessionTarget[]) =>
    apiClient.post<BulkKillResult>('/sessions/bulk-kill', { sessions }),
}
