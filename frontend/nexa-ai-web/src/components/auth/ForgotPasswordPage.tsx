import { useState, type FormEvent } from 'react'
import { authService } from '../../services/authService.ts'
import { ErrorBanner } from '../common/ErrorBanner.tsx'
import type { AppRoute } from '../../types/index.ts'

interface ForgotPasswordPageProps {
  readonly onNavigate: (route: AppRoute) => void
}

export function ForgotPasswordPage({ onNavigate }: ForgotPasswordPageProps) {
  const [email, setEmail] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [sent, setSent] = useState(false)

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setLoading(true)

    try {
      await authService.forgotPassword(email.trim())
      setSent(true)
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Request failed. Please try again later.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="container py-5">
      <div className="row justify-content-center">
        <div className="col-12 col-md-6 col-lg-5 col-xl-4">
          <div className="card shadow-sm border p-4">
            <h2 className="fs-4 fw-bold mb-1">Reset password</h2>
            <p className="text-secondary small mb-4">
              Enter your email address and we'll send you instructions to reset your password.
            </p>

            <ErrorBanner message={error} onDismiss={() => setError(null)} />

            {sent ? (
              <div className="text-center">
                <div className="alert alert-success small mb-3">
                  If an account exists for {email}, a reset link has been dispatched.
                </div>
                <button
                  type="button"
                  className="btn btn-outline-primary w-100"
                  onClick={() => onNavigate('login')}
                >
                  Return to Sign in
                </button>
              </div>
            ) : (
              <form onSubmit={handleSubmit}>
                <div className="mb-3">
                  <label className="form-label small fw-semibold" htmlFor="forgot-email">
                    Email address
                  </label>
                  <input
                    id="forgot-email"
                    type="email"
                    className="form-control"
                    placeholder="name@example.com"
                    required
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    disabled={loading}
                  />
                </div>

                <button
                  type="submit"
                  className="btn btn-primary w-100 py-2 d-flex align-items-center justify-content-center gap-2 mb-3"
                  disabled={loading}
                >
                  {loading && <div className="spinner-border spinner-border-sm" role="status" />}
                  <span>{loading ? 'Sending link...' : 'Send reset instructions'}</span>
                </button>

                <div className="text-center small">
                  <button
                    type="button"
                    className="btn btn-link p-0 small text-decoration-none"
                    onClick={() => onNavigate('login')}
                  >
                    Back to Sign in
                  </button>
                </div>
              </form>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
