import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { User, LoginResponse, DatabaseType } from '../types'

interface AuthState {
  token: string | null
  user: User | null
  passwordExpiryDays: number | null
  isAuthenticated: boolean
  setAuth: (response: LoginResponse) => void
  logout: () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      user: null,
      passwordExpiryDays: null,
      isAuthenticated: false,
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
        }),
      logout: () =>
        set({
          token: null,
          user: null,
          passwordExpiryDays: null,
          isAuthenticated: false,
        }),
    }),
    {
      name: 'auth-storage',
    }
  )
)