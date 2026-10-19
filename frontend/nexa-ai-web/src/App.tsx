import { useState } from 'react'
import { ThemeProvider } from './context/ThemeContext.tsx'
import { AuthProvider, useAuth } from './context/AuthContext.tsx'
import { Header } from './components/layout/Header.tsx'
import { LoginPage } from './components/auth/LoginPage.tsx'
import { RegisterPage } from './components/auth/RegisterPage.tsx'
import { ForgotPasswordPage } from './components/auth/ForgotPasswordPage.tsx'
import { ChatLayout } from './components/chat/ChatLayout.tsx'
import type { AppRoute } from './types/index.ts'

function AppContent() {
  const { isAuthenticated } = useAuth()
  const [route, setRoute] = useState<AppRoute>(() => (isAuthenticated ? 'chat' : 'login'))

  const handleNavigate = (nextRoute: AppRoute) => {
    setRoute(nextRoute)
  }

  return (
    <div className="d-flex flex-column vh-100 bg-body text-body">
      <Header currentRoute={route} onNavigate={handleNavigate} />
      <div className="d-flex flex-grow-1 overflow-hidden">
        {route === 'login' && <LoginPage onNavigate={handleNavigate} />}
        {route === 'register' && <RegisterPage onNavigate={handleNavigate} />}
        {route === 'forgot-password' && <ForgotPasswordPage onNavigate={handleNavigate} />}
        {route === 'chat' && <ChatLayout />}
      </div>
    </div>
  )
}

export default function App() {
  return (
    <ThemeProvider>
      <AuthProvider>
        <AppContent />
      </AuthProvider>
    </ThemeProvider>
  )
}