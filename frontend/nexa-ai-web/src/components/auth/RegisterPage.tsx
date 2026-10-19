import { useState, type FormEvent } from 'react'
import { authService } from '../../services/authService.ts'
import { ErrorBanner } from '../common/ErrorBanner.tsx'
import type { AppRoute } from '../../types/index.ts'

interface RegisterPageProps {
  readonly onNavigate: (route: AppRoute) => void
}

export function RegisterPage({ onNavigate }: RegisterPageProps) {
  const [displayName, setDisplayName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState(false)

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setLoading(true)

    try {
      await authService.register(email.trim(), password, displayName.trim())
      setSuccess(true)
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Registration failed. Please check inputs.')
    } finally {
      setLoading(false)
    }
  }

  if (success) {
    return (
      <div className="container py-5">
        <div className="row justify-content-center">
          <div className="col-12 col-md-6 col-lg-5 col-xl-4">
            <div className="card shadow-sm border p-4 text-center">
              <h3 className="fs-5 fw-bold mb-2">Check your email</h3>
              <p className="text-secondary small mb-4">
                A verification link has been sent to <strong>{email}</strong>. Once verified, you can sign in to your account.
              </p>
              <button
                type="button"
                className="btn btn-primary"
                onClick={() => onNavigate('login')}
              >
                Proceed to Sign in
              </button>
            </div>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="container py-5">
      <div className="row justify-content-center">
        <div className="col-12 col-md-6 col-lg-5 col-xl-4">
          <div className="card shadow-sm border">
            <div className="card-body p-4">
              <h2 className="fs-4 fw-bold mb-1">Create account</h2>
              <p className="text-secondary small mb-4">
                Sign up to start chatting with NexaAI.
              </p>

              <ErrorBanner message={error} onDismiss={() => setError(null)} />

              <form onSubmit={handleSubmit}>
                <div className="mb-3">
                  <label className="form-label small fw-semibold" htmlFor="register-name">
                    Display name
                  </label>
                  <input
                    id="register-name"
                    type="text"
                    className="form-control"
                    placeholder="Ada Lovelace"
                    required
                    value={displayName}
                    onChange={(e) => setDisplayName(e.target.value)}
                    disabled={loading}
                  />
                </div>

                <div className="mb-3">
                  <label className="form-label small fw-semibold" htmlFor="register-email">
                    Email address
                  </label>
                  <input
                    id="register-email"
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
                  <label className="form-label small fw-semibold" htmlFor="register-password">
                    Password
                  </label>
                  <input
                    id="register-password"
                    type="password"
                    className="form-control"
                    placeholder="At least 8 characters"
                    required
                    minLength={8}
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
                  <span>{loading ? 'Creating account...' : 'Create account'}</span>
                </button>

                <div className="text-center small text-secondary">
                  Already have an account?{' '}
                  <button
                    type="button"
                    className="btn btn-link p-0 small text-decoration-none"
                    onClick={() => onNavigate('login')}
                  >
                    Sign in
                  </button>
                </div>
              </form>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
