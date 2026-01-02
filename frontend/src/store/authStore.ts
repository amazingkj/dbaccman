import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { User, LoginResponse } from '../types'

// Session timeout: 30 minutes (in milliseconds)
export const SESSION_TIMEOUT_MS = 30 * 60 * 1000
// Warning before timeout: 5 minutes
export const SESSION_WARNING_MS = 5 * 60 * 1000

interface AuthState {
  token: string | null
  user: User | null
  passwordExpiryDays: number | null
  isAuthenticated: boolean
  lastActivity: number
  setAuth: (response: LoginResponse) => void
  logout: () => void
  updateLastActivity: () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      user: null,
      passwordExpiryDays: null,
      isAuthenticated: false,
      lastActivity: Date.now(),
      setAuth: (response: LoginResponse) =>
        set({
          token: response.token,
          user: {
            username: response.username,
            role: response.role,
            host: response.host,
            port: response.port,
            dbType: response.dbType,
          },
          passwordExpiryDays: response.passwordExpiryDays,
          isAuthenticated: true,
          lastActivity: Date.now(),
        }),
      logout: () =>
        set({
          token: null,
          user: null,
          passwordExpiryDays: null,
          isAuthenticated: false,
          lastActivity: Date.now(),
        }),
      updateLastActivity: () =>
        set({
          lastActivity: Date.now(),
        }),
    }),
    {
      name: 'auth-storage',
    }
  )
)