import { useState, type FormEvent } from 'react'
import { BrandLogo } from '../common/BrandLogo.tsx'
import { authService } from '../../services/authService.ts'
import { useAuth } from '../../context/AuthContext.tsx'
import { ErrorBanner } from '../common/ErrorBanner.tsx'
import type { AppRoute } from '../../types/index.ts'

interface LoginPageProps {
  readonly onNavigate: (route: AppRoute) => void
}

export function LoginPage({ onNavigate }: LoginPageProps) {
  const { setUser } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setLoading(true)

    try {
      const res = await authService.login(email.trim(), password)
      setUser(res.user)
      onNavigate('chat')
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Invalid credentials. Please verify your email and password.')
    } finally {
      setLoading(false)
    }
  }

  const handleGoogleLogin = () => {
    // Backend OAuth flow: redirect to backend OAuth authorization endpoint
    const gatewayUrl = import.meta.env.VITE_GATEWAY_URL || 'http://localhost:8080'
    window.location.href = `${gatewayUrl}/oauth2/authorization/google`
  }

  // Developer quick-fill for local testing
  const handleDevAdminLogin = () => {
    const adminUser = {
      userId: '11111111-1111-1111-1111-111111111111',
      email: 'admin@nexaai.com',
      displayName: 'System Admin',
      roles: ['USER' as const, 'ADMIN' as const],
    }
    localStorage.setItem('nexa_user', JSON.stringify(adminUser))
    localStorage.setItem('nexa_access_token', 'dev-admin-token')
    setUser(adminUser)
    onNavigate('admin')
  }

  const handleDevUserLogin = () => {
    const regularUser = {
      userId: '9f1c7b2e-4a3d-4c8f-b1e2-5d7a9c3e0f14',
      email: 'ada@example.com',
      displayName: 'Ada Lovelace',
      roles: ['USER' as const],
    }
    localStorage.setItem('nexa_user', JSON.stringify(regularUser))
    localStorage.setItem('nexa_access_token', 'dev-user-token')
    setUser(regularUser)
    onNavigate('chat')
  }

  return (
    <div className="container-xl d-flex align-items-center justify-content-center py-5 min-vh-100">
      <div className="w-100" style={{ maxWidth: '420px' }}>
        <div className="text-center mb-4">
          <BrandLogo size="lg" onClick={() => onNavigate('home')} />
          <h2 className="fs-4 fw-bold mt-3 mb-1 text-body">Welcome back</h2>
          <p className="text-secondary small mb-0">Sign in to your NexaAI workspace.</p>
        </div>

        <div className="nexa-card p-4 p-md-4.5">
          <ErrorBanner message={error} onDismiss={() => setError(null)} />

          <form onSubmit={handleSubmit} className="d-flex flex-column gap-3">
            <div>
              <label className="form-label small fw-semibold text-body" htmlFor="login-email">
                Email
              </label>
              <input
                id="login-email"
                type="email"
                className="nexa-input"
                placeholder="name@example.com"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                disabled={loading}
              />
            </div>

            <div>
              <div className="d-flex justify-content-between align-items-center mb-1">
                <label className="form-label small fw-semibold text-body mb-0" htmlFor="login-password">
                  Password
                </label>
                <button
                  type="button"
                  className="btn btn-link p-0 small text-decoration-none"
                  style={{ color: 'var(--brand-primary)', fontSize: '0.8rem' }}
                  onClick={() => onNavigate('forgot-password')}
                >
                  Forgot password?
                </button>
              </div>
              <input
                id="login-password"
                type="password"
                className="nexa-input"
                placeholder="••••••••"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                disabled={loading}
              />
            </div>

            <button
              type="submit"
              className="nexa-btn-primary w-100 py-2.5 mt-1"
              disabled={loading}
            >
              {loading ? 'Signing in...' : 'Login'}
            </button>
          </form>

          <div className="position-relative my-4 text-center">
            <hr className="border-subtle" />
            <span
              className="position-absolute top-50 start-50 translate-middle px-3 small text-muted"
              style={{ backgroundColor: 'var(--surface-card)' }}
            >
              or
            </span>
          </div>

          {/* Continue with Google button using backend OAuth flow */}
          <button
            type="button"
            className="nexa-btn-secondary w-100 py-2.5 d-flex align-items-center justify-content-center gap-2"
            onClick={handleGoogleLogin}
            disabled={loading}
          >
            <svg width="18" height="18" viewBox="0 0 24 24">
              <path
                fill="#4285F4"
                d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
              />
              <path
                fill="#34A853"
                d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
              />
              <path
                fill="#FBBC05"
                d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z"
              />
              <path
                fill="#EA4335"
                d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"
              />
            </svg>
            <span>Continue with Google</span>
          </button>

          <div className="text-center mt-4 text-secondary small">
            Don't have an account?{' '}
            <button
              type="button"
              className="btn btn-link p-0 small fw-semibold text-decoration-none"
              style={{ color: 'var(--brand-primary)' }}
              onClick={() => onNavigate('register')}
            >
              Register
            </button>
          </div>
        </div>

        {/* Development Quick-Logins */}
        <div className="mt-3 text-center">
          <div className="d-flex justify-content-center gap-2">
            <button
              type="button"
              className="btn btn-link p-0 text-muted small text-decoration-none"
              style={{ fontSize: '0.75rem' }}
              onClick={handleDevUserLogin}
              title="Dev convenience: Log in as User"
            >
              [Dev: User Demo]
            </button>
            <span className="text-muted small">•</span>
            <button
              type="button"
              className="btn btn-link p-0 text-muted small text-decoration-none"
              style={{ fontSize: '0.75rem' }}
              onClick={handleDevAdminLogin}
              title="Dev convenience: Log in as Admin"
            >
              [Dev: Admin Demo]
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
