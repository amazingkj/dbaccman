import apiClient from './client'
import type { LoginRequest, LoginResponse, User, PasswordExpiryInfo } from '../types'

export const authApi = {
  login: (data: LoginRequest) =>
    apiClient.post<LoginResponse>('/auth/login', data),

  logout: () =>
    apiClient.post('/auth/logout'),

  me: () =>
    apiClient.get<User>('/auth/me'),

  getPasswordExpiry: () =>
    apiClient.get<PasswordExpiryInfo>('/auth/password-expiry'),
}
