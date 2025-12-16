import { useNavigate } from 'react-router-dom'
import { message } from 'antd'
import { useAuthStore } from '../store/authStore'
import { authApi } from '../api/auth'
import type { LoginRequest } from '../types'

export function useAuth() {
  const navigate = useNavigate()
  const { token, user, isAuthenticated, setAuth, logout: clearAuth } = useAuthStore()

  const login = async (credentials: LoginRequest) => {
    try {
      const response = await authApi.login(credentials)
      const { token, username, role } = response.data

      localStorage.setItem('token', token)
      setAuth(token, { username, role })

      message.success('Login successful')
      navigate('/dashboard')
    } catch (error) {
      message.error('Login failed. Please check your credentials.')
      throw error
    }
  }

  const logout = () => {
    localStorage.removeItem('token')
    clearAuth()
    message.info('Logged out successfully')
    navigate('/login')
  }

  return {
    token,
    user,
    isAuthenticated,
    login,
    logout,
  }
}
