import { useState, useEffect } from 'react'
import { ThemeProvider } from './context/ThemeContext.tsx'
import { AuthProvider, useAuth } from './context/AuthContext.tsx'
import { Header } from './components/layout/Header.tsx'
import { UserSidebar } from './components/layout/UserSidebar.tsx'
import { PublicNavbar } from './components/public/PublicNavbar.tsx'
import { PublicFooter } from './components/public/PublicFooter.tsx'
import { LandingPage } from './components/public/LandingPage.tsx'
import { PricingPage } from './components/public/PricingPage.tsx'
import { FaqPage } from './components/public/FaqPage.tsx'
import { LoginPage } from './components/auth/LoginPage.tsx'
import { RegisterPage } from './components/auth/RegisterPage.tsx'
import { VerifyEmailPage } from './components/auth/VerifyEmailPage.tsx'
import { ForgotPasswordPage } from './components/auth/ForgotPasswordPage.tsx'
import { OtpVerifyPage } from './components/auth/OtpVerifyPage.tsx'
import { ResetPasswordPage } from './components/auth/ResetPasswordPage.tsx'
import { ChangePasswordPage } from './components/auth/ChangePasswordPage.tsx'
import { UserDashboard } from './components/dashboard/UserDashboard.tsx'
import { ChatLayout } from './components/chat/ChatLayout.tsx'
import { DocumentsView } from './components/documents/DocumentsView.tsx'
import { UserUsageDashboard } from './components/usage/UserUsageDashboard.tsx'
import { PricingBillingView } from './components/billing/PricingBillingView.tsx'
import { SettingsView } from './components/settings/SettingsView.tsx'
import { AdminPortal } from './components/admin/AdminPortal.tsx'
import type { AppRoute } from './types/index.ts'

function pathToRoute(path: string): AppRoute {
  const clean = path.replace(/^\/+|\/+$/g, '')
  if (!clean || clean === '') return 'home'
  if (clean === 'pricing') return 'pricing'
  if (clean === 'faq') return 'faq'
  if (clean === 'login') return 'login'
  if (clean === 'register') return 'register'
  if (clean === 'verify-email') return 'verify-email'
  if (clean === 'forgot-password') return 'forgot-password'
  if (clean === 'otp-verify') return 'otp-verify'
  if (clean === 'reset-password') return 'reset-password'
  if (clean === 'change-password') return 'change-password'
  if (clean === 'app') return 'app'
  if (clean === 'chat') return 'chat'
  if (clean === 'documents') return 'documents'
  if (clean === 'usage') return 'usage'
  if (clean === 'billing') return 'billing'
  if (clean === 'settings') return 'settings'
  if (clean === 'admin') return 'admin'
  return 'home'
}

function routeToPath(route: AppRoute): string {
  if (route === 'home') return '/'
  return `/${route}`
}

function AppContent() {
  const { isAuthenticated, user } = useAuth()
  const [route, setRoute] = useState<AppRoute>(() => {
    const initial = pathToRoute(window.location.pathname)
    if (initial === 'home' && isAuthenticated) return 'chat'
    return initial
  })
  const [sidebarOpen, setSidebarOpen] = useState(false)

  // Sync browser back/forward buttons
  useEffect(() => {
    const onPopState = () => {
      setRoute(pathToRoute(window.location.pathname))
    }
    window.addEventListener('popstate', onPopState)
    return () => window.removeEventListener('popstate', onPopState)
  }, [])

  const handleNavigate = (nextRoute: AppRoute) => {
    // Restrict admin route strictly to users with ADMIN role
    if (nextRoute === 'admin' && !user?.roles?.includes('ADMIN')) {
      nextRoute = 'chat'
    }

    setRoute(nextRoute)
    setSidebarOpen(false)

    const nextPath = routeToPath(nextRoute)
    if (window.location.pathname !== nextPath) {
      window.history.pushState(null, '', nextPath)
    }
    window.scrollTo(0, 0)
  }

  const isPublicPage = route === 'home' || route === 'pricing' || route === 'faq'
  const isAuthPage =
    route === 'login' ||
    route === 'register' ||
    route === 'verify-email' ||
    route === 'forgot-password' ||
    route === 'otp-verify' ||
    route === 'reset-password' ||
    route === 'change-password'

  // 1. PUBLIC MARKETING PAGES
  if (isPublicPage) {
    return (
      <div className="d-flex flex-column min-vh-100 bg-base text-body">
        <PublicNavbar
          currentRoute={route}
          onNavigate={handleNavigate}
          isAuthenticated={isAuthenticated}
        />
        <main className="flex-grow-1">
          {route === 'home' && (
            <LandingPage
              onNavigate={handleNavigate}
              isAuthenticated={isAuthenticated}
            />
          )}
          {route === 'pricing' && (
            <PricingPage
              onNavigate={handleNavigate}
              isAuthenticated={isAuthenticated}
            />
          )}
          {route === 'faq' && <FaqPage onNavigate={handleNavigate} />}
        </main>
        <PublicFooter onNavigate={handleNavigate} />
      </div>
    )
  }

  // 2. AUTHENTICATION SCREENS
  if (isAuthPage) {
    return (
      <div className="d-flex flex-column min-vh-100 bg-base text-body">
        {route === 'login' && <LoginPage onNavigate={handleNavigate} />}
        {route === 'register' && <RegisterPage onNavigate={handleNavigate} />}
        {route === 'verify-email' && <VerifyEmailPage onNavigate={handleNavigate} />}
        {route === 'forgot-password' && <ForgotPasswordPage onNavigate={handleNavigate} />}
        {route === 'otp-verify' && <OtpVerifyPage onNavigate={handleNavigate} />}
        {route === 'reset-password' && <ResetPasswordPage onNavigate={handleNavigate} />}
        {route === 'change-password' && <ChangePasswordPage onNavigate={handleNavigate} />}
      </div>
    )
  }

  // 3. ADMIN APPLICATION (strictly ROLE_ADMIN)
  if (route === 'admin') {
    if (!user?.roles?.includes('ADMIN')) {
      handleNavigate('chat')
      return null
    }
    return (
      <div className="d-flex flex-column vh-100 bg-base text-body">
        <Header currentRoute={route} onNavigate={handleNavigate} />
        <div className="d-flex flex-grow-1 overflow-hidden">
          <AdminPortal />
        </div>
      </div>
    )
  }

  // 4. USER APPLICATION SHELL (Header + UserSidebar + Main view)
  return (
    <div className="d-flex flex-column vh-100 bg-base text-body">
      <Header currentRoute={route} onNavigate={handleNavigate} />
      <div className="d-flex flex-grow-1 overflow-hidden position-relative">
        <UserSidebar
          currentRoute={route}
          onNavigate={handleNavigate}
          onNewChat={() => handleNavigate('chat')}
          isOpen={sidebarOpen}
          onClose={() => setSidebarOpen(false)}
        />
        <main className="d-flex flex-grow-1 overflow-hidden position-relative">
          {route === 'app' && <UserDashboard onNavigate={handleNavigate} />}
          {route === 'chat' && <ChatLayout />}
          {route === 'documents' && <DocumentsView />}
          {route === 'usage' && <UserUsageDashboard />}
          {route === 'billing' && <PricingBillingView onNavigate={handleNavigate} />}
          {route === 'settings' && <SettingsView onNavigate={handleNavigate} />}
        </main>
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
