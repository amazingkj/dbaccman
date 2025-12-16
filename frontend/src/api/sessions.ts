import apiClient from './client'
import type { SessionInfo } from '../types'

export const sessionsApi = {
  getActive: () =>
    apiClient.get<SessionInfo[]>('/sessions'),

  kill: (pid: number) =>
    apiClient.delete(`/sessions/${pid}`),
}
