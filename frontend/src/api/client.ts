import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios'
import { message } from 'antd'
import { useAuthStore } from '../store/authStore'

const apiClient = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json',
    'Cache-Control': 'no-cache, no-store, must-revalidate',
    'Pragma': 'no-cache',
    'Expires': '0',
  },
})

// Track if session expired - prevent multiple redirects
let sessionExpiredHandled = false

// Request interceptor - Add JWT token
apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = useAuthStore.getState().token
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
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
    // Refresh session timeout on successful API calls
    const { isAuthenticated, updateLastActivity } = useAuthStore.getState()
    if (isAuthenticated) {
      updateLastActivity()
    }
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
