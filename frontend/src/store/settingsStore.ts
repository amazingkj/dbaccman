import { create } from 'zustand'
import { persist } from 'zustand/middleware'

// Default values
const DEFAULT_SESSION_TIMEOUT_MINUTES = 30
const DEFAULT_WARNING_MINUTES = 5

interface SettingsState {
  // Session timeout in minutes
  sessionTimeoutMinutes: number
  // Warning before timeout in minutes
  sessionWarningMinutes: number
  // Whether to auto-logout on timeout
  autoLogoutOnTimeout: boolean
  // Theme preference
  theme: 'light' | 'dark' | 'system'

  // Actions
  setSessionTimeout: (minutes: number) => void
  setSessionWarning: (minutes: number) => void
  setAutoLogoutOnTimeout: (enabled: boolean) => void
  setTheme: (theme: 'light' | 'dark' | 'system') => void
  resetToDefaults: () => void
}

// Helper to get timeout in milliseconds
export const getSessionTimeoutMs = (minutes: number) => minutes * 60 * 1000
export const getSessionWarningMs = (minutes: number) => minutes * 60 * 1000

export const useSettingsStore = create<SettingsState>()(
  persist(
    (set) => ({
      sessionTimeoutMinutes: DEFAULT_SESSION_TIMEOUT_MINUTES,
      sessionWarningMinutes: DEFAULT_WARNING_MINUTES,
      autoLogoutOnTimeout: true,
      theme: 'light',

      setSessionTimeout: (minutes: number) =>
        set({ sessionTimeoutMinutes: Math.max(5, Math.min(480, minutes)) }), // 5 min to 8 hours

      setSessionWarning: (minutes: number) =>
        set({ sessionWarningMinutes: Math.max(1, Math.min(30, minutes)) }), // 1 to 30 min

      setAutoLogoutOnTimeout: (enabled: boolean) =>
        set({ autoLogoutOnTimeout: enabled }),

      setTheme: (theme: 'light' | 'dark' | 'system') =>
        set({ theme }),

      resetToDefaults: () =>
        set({
          sessionTimeoutMinutes: DEFAULT_SESSION_TIMEOUT_MINUTES,
          sessionWarningMinutes: DEFAULT_WARNING_MINUTES,
          autoLogoutOnTimeout: true,
          theme: 'light',
        }),
    }),
    {
      name: 'settings-storage',
    }
  )
)

// Export default values for reference
export { DEFAULT_SESSION_TIMEOUT_MINUTES, DEFAULT_WARNING_MINUTES }
