import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { RecentConnection, DatabaseType } from '../types'

const MAX_RECENT_CONNECTIONS = 10

interface ConnectionState {
  recentConnections: RecentConnection[]
  addRecentConnection: (connection: Omit<RecentConnection, 'lastUsed'>) => void
  removeRecentConnection: (host: string, port: number, username: string) => void
  clearRecentConnections: () => void
}

export const useConnectionStore = create<ConnectionState>()(
  persist(
    (set, get) => ({
      recentConnections: [],

      addRecentConnection: (connection) => {
        const { recentConnections } = get()

        // Remove existing connection with same host/port/username
        const filtered = recentConnections.filter(
          (c) =>
            !(c.host === connection.host &&
              c.port === connection.port &&
              c.username === connection.username)
        )

        // Add new connection at the beginning
        const newConnection: RecentConnection = {
          ...connection,
          dbType: connection.dbType || 'MYSQL',
          lastUsed: new Date().toISOString(),
        }

        // Keep only the most recent connections
        const updated = [newConnection, ...filtered].slice(0, MAX_RECENT_CONNECTIONS)

        set({ recentConnections: updated })
      },

      removeRecentConnection: (host, port, username) => {
        const { recentConnections } = get()
        const filtered = recentConnections.filter(
          (c) =>
            !(c.host === host && c.port === port && c.username === username)
        )
        set({ recentConnections: filtered })
      },

      clearRecentConnections: () => {
        set({ recentConnections: [] })
      },
    }),
    {
      name: 'connection-storage',
    }
  )
)