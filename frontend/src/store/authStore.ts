import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { User, LoginResponse } from '../types'
import { apiCache } from '../utils/cache'

// Session timeout: 30 minutes (in milliseconds)
export const SESSION_TIMEOUT_MS = 30 * 60 * 1000
// Warning before timeout: 5 minutes
export const SESSION_WARNING_MS = 5 * 60 * 1000

interface AuthState {
  // Note: token is no longer stored here - authentication uses httpOnly cookies
  // This field is kept for backward compatibility but will always be null
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
      token: null,  // Deprecated: authentication now uses httpOnly cookies
      user: null,
      passwordExpiryDays: null,
      isAuthenticated: false,
      lastActivity: Date.now(),
      setAuth: (response: LoginResponse) =>
        set({
          // Token is now sent as httpOnly cookie, not stored in state
          token: null,
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
      logout: () => {
        apiCache.clear() // Clear all cached API responses
        set({
          token: null,
          user: null,
          passwordExpiryDays: null,
          isAuthenticated: false,
          lastActivity: Date.now(),
        })
      },
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