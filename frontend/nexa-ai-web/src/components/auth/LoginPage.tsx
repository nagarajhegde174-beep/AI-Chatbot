import { useState, type FormEvent } from 'react'
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
      setError(err instanceof Error ? err.message : 'Login failed. Please check your credentials.')
    } finally {
      setLoading(false)
    }
  }

  // Quick dev bypass button so developer can test without full auth backend running if desired
  const handleDevDemoLogin = () => {
    const demoUser = {
      userId: '9f1c7b2e-4a3d-4c8f-b1e2-5d7a9c3e0f14',
      email: 'ada@example.com',
      displayName: 'Ada Lovelace',
      roles: ['USER' as const],
    }
    localStorage.setItem('nexa_user', JSON.stringify(demoUser))
    localStorage.setItem('nexa_access_token', 'dev-demo-token')
    setUser(demoUser)
    onNavigate('chat')
  }

  return (
    <div className="container py-5">
      <div className="row justify-content-center">
        <div className="col-12 col-md-6 col-lg-5 col-xl-4">
          <div className="card shadow-sm border">
            <div className="card-body p-4">
              <h2 className="fs-4 fw-bold mb-1">Sign in</h2>
              <p className="text-secondary small mb-4">
                Enter your credentials to access your conversations.
              </p>

              <ErrorBanner message={error} onDismiss={() => setError(null)} />

              <form onSubmit={handleSubmit}>
                <div className="mb-3">
                  <label className="form-label small fw-semibold" htmlFor="login-email">
                    Email address
                  </label>
                  <input
                    id="login-email"
                    type="email"
                    className="form-control"
                    placeholder="name@example.com"
                    required
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    disabled={loading}
                  />
                </div>

                <div className="mb-3">
                  <div className="d-flex justify-content-between align-items-center">
                    <label className="form-label small fw-semibold" htmlFor="login-password">
                      Password
                    </label>
                    <button
                      type="button"
                      className="btn btn-link p-0 small text-decoration-none"
                      onClick={() => onNavigate('forgot-password')}
                    >
                      Forgot?
                    </button>
                  </div>
                  <input
                    id="login-password"
                    type="password"
                    className="form-control"
                    placeholder="••••••••"
                    required
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    disabled={loading}
                  />
                </div>

                <button
                  type="submit"
                  className="btn btn-primary w-100 py-2 d-flex align-items-center justify-content-center gap-2 mb-3"
                  disabled={loading}
                >
                  {loading && <div className="spinner-border spinner-border-sm" role="status" />}
                  <span>{loading ? 'Signing in...' : 'Sign in'}</span>
                </button>

                <div className="text-center small text-secondary">
                  Don't have an account?{' '}
                  <button
                    type="button"
                    className="btn btn-link p-0 small text-decoration-none"
                    onClick={() => onNavigate('register')}
                  >
                    Sign up
                  </button>
                </div>
              </form>

              <hr className="my-4" />

              <div className="d-grid">
                <button
                  type="button"
                  className="btn btn-sm btn-outline-secondary"
                  onClick={handleDevDemoLogin}
                  title="Bypass login for quick local testing"
                >
                  Demo Mode (Instant Access)
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
