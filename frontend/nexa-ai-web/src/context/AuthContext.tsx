import { createContext, useContext, useState, type ReactNode } from 'react'
import { authService } from '../services/authService.ts'
import type { User } from '../types/index.ts'

interface AuthContextValue {
  readonly user: User | null
  readonly isAuthenticated: boolean
  readonly setUser: (user: User | null) => void
  readonly logout: () => void
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

export function AuthProvider({ children }: { readonly children: ReactNode }) {
  const [user, setUser] = useState<User | null>(() => authService.getStoredUser())

  const logout = () => {
    authService.logout()
    setUser(null)
  }

  const value: AuthContextValue = {
    user,
    isAuthenticated: Boolean(user),
    setUser,
    logout,
  }

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
