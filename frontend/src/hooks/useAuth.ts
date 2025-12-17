import { useNavigate } from 'react-router-dom'
import { message } from 'antd'
import { AxiosError } from 'axios'
import { useAuthStore } from '../store/authStore'
import { useConnectionStore } from '../store/connectionStore'
import { authApi } from '../api/auth'
import type { LoginRequest, ApiError } from '../types'
import { DATABASE_TYPES } from '../types'

export function useAuth() {
  const navigate = useNavigate()
  const { token, user, passwordExpiryDays, isAuthenticated, setAuth, logout: clearAuth } = useAuthStore()
  const { addRecentConnection } = useConnectionStore()

  const login = async (credentials: LoginRequest) => {
    try {
      const response = await authApi.login(credentials)
      const data = response.data

      setAuth(data)

      // Save to recent connections
      addRecentConnection({
        host: credentials.host,
        port: credentials.port || data.port,
        username: credentials.username,
        dbType: credentials.dbType,
        database: credentials.database,
      })

      const dbTypeLabel = DATABASE_TYPES.find(t => t.value === data.dbType)?.label || data.dbType
      message.success(`Connected to ${data.host}:${data.port} (${dbTypeLabel}) as ${data.username}`)
      navigate('/dashboard')
    } catch (error) {
      if (error instanceof AxiosError) {
        const errorMsg = (error.response?.data as ApiError)?.error || 'Connection failed. Please check your credentials.'
        message.error(errorMsg)
      } else {
        message.error('Connection failed. Please check your credentials.')
      }
      throw error
    }
  }

  const logout = async () => {
    try {
      await authApi.logout()
    } catch {
      // Ignore logout errors
    }
    clearAuth()
    message.info('Disconnected')
    navigate('/login')
  }

  return {
    token,
    user,
    passwordExpiryDays,
    isAuthenticated,
    login,
    logout,
  }
}