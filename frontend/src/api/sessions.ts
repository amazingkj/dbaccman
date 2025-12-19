import apiClient from './client'
import type { SessionInfo } from '../types'

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
}
