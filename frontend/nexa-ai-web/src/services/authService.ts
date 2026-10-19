import { request } from './apiClient.ts'
import type { AuthTokens, User } from '../types/index.ts'

export const authService = {
  async login(email: string, password: string): Promise<AuthTokens> {
    const res = await request<AuthTokens>('/api/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    })
    if (res.accessToken) {
      localStorage.setItem('nexa_access_token', res.accessToken)
      localStorage.setItem('nexa_user', JSON.stringify(res.user))
    }
    return res
  },

  async register(email: string, password: string, displayName: string): Promise<User> {
    return request<User>('/api/v1/auth/register', {
      method: 'POST',
      body: JSON.stringify({ email, password, displayName }),
    })
  },

  async forgotPassword(email: string): Promise<{ message: string }> {
    return request<{ message: string }>('/api/v1/auth/password/forgot', {
      method: 'POST',
      body: JSON.stringify({ email }),
    })
  },

  logout(): void {
    localStorage.removeItem('nexa_access_token')
    localStorage.removeItem('nexa_user')
  },

  getStoredUser(): User | null {
    const json = localStorage.getItem('nexa_user')
    if (!json) return null
    try {
      return JSON.parse(json) as User
    } catch {
      return null
    }
  },

  isAuthenticated(): boolean {
    return Boolean(localStorage.getItem('nexa_access_token'))
  },
}
