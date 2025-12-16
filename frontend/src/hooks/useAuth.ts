import { useNavigate } from 'react-router-dom'
import { message } from 'antd'
import { useAuthStore } from '../store/authStore'
import { useConnectionStore } from '../store/connectionStore'
import { authApi } from '../api/auth'
import type { LoginRequest } from '../types'

export function useAuth() {
  const navigate = useNavigate()
  const { token, user, passwordExpiryDays, isAuthenticated, setAuth, logout: clearAuth } = useAuthStore()
  const { addRecentConnection } = useConnectionStore()

  const login = async (credentials: LoginRequest) => {
    try {
      const response = await authApi.login(credentials)
      const data = response.data

      localStorage.setItem('token', data.token)
      setAuth(data)

      // Save to recent connections
      addRecentConnection({
        host: credentials.host,
        port: credentials.port,
        username: credentials.username,
      })

      message.success(`Connected to ${data.host}:${data.port} as ${data.username}`)
      navigate('/dashboard')
    } catch (error: unknown) {
      const err = error as { response?: { data?: { error?: string } } }
      const errorMsg = err.response?.data?.error || 'Connection failed. Please check your credentials.'
      message.error(errorMsg)
      throw error
    }
  }

  const logout = async () => {
    try {
      await authApi.logout()
    } catch {
      // Ignore logout errors
    }
    localStorage.removeItem('token')
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
