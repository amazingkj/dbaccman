import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios'
import { message } from 'antd'
import { useAuthStore } from '../store/authStore'

// Debounced activity update - only update once per 30 seconds
let lastActivityUpdate = 0
const ACTIVITY_UPDATE_INTERVAL = 30 * 1000 // 30 seconds

function debouncedUpdateActivity(): void {
  const now = Date.now()
  if (now - lastActivityUpdate >= ACTIVITY_UPDATE_INTERVAL) {
    lastActivityUpdate = now
    const { isAuthenticated, updateLastActivity } = useAuthStore.getState()
    if (isAuthenticated) {
      updateLastActivity()
    }
  }
}

const apiClient = axios.create({
  baseURL: '/api',
  withCredentials: true,  // Send cookies with requests
  headers: {
    'Content-Type': 'application/json',
    'Cache-Control': 'no-cache, no-store, must-revalidate',
    'Pragma': 'no-cache',
    'Expires': '0',
  },
})

// Track if session expired - prevent multiple redirects
let sessionExpiredHandled = false

// CSRF token storage
let csrfToken: string | null = null

// Fetch CSRF token from server
export async function initCsrfToken(): Promise<void> {
  try {
    const response = await axios.get('/api/auth/csrf-token', { withCredentials: true })
    csrfToken = response.data.csrfToken
  } catch (error) {
    console.error('Failed to fetch CSRF token:', error)
  }
}

// Refresh CSRF token
export function refreshCsrfToken(): Promise<void> {
  return initCsrfToken()
}

// Get current CSRF token
export function getCsrfToken(): string | null {
  return csrfToken
}

// Request interceptor - Add CSRF token for mutating requests
apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // Add CSRF token for state-changing requests
    const method = config.method?.toLowerCase()
    if (['post', 'put', 'delete', 'patch'].includes(method || '')) {
      if (csrfToken && config.headers) {
        config.headers['X-CSRF-Token'] = csrfToken
      }
    }
    return config
  },
  (error: AxiosError) => {
    return Promise.reject(error)
  }
)

// Response interceptor - Handle auth errors and refresh session
apiClient.interceptors.response.use(
  (response) => {
    // Refresh session timeout on successful API calls (debounced)
    debouncedUpdateActivity()
    return response
  },
  (error: AxiosError) => {
    // Only redirect on 401 if NOT on login page (to allow login error display)
    if (error.response?.status === 401) {
      const isLoginPage = window.location.pathname === '/login'
      const isLoginRequest = error.config?.url?.includes('/auth/login')

      if (!isLoginPage && !isLoginRequest && !sessionExpiredHandled) {
        sessionExpiredHandled = true
        // Clear auth and redirect immediately
        localStorage.removeItem('auth-storage')
        message.warning('Session expired. Please log in again.')
        window.location.href = '/login'
      }
    }
    return Promise.reject(error)
  }
)

// Reset session expired flag (called on successful login)
export const resetSessionExpiredFlag = () => {
  sessionExpiredHandled = false
}

export default apiClient
