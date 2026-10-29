import { useState, type FormEvent } from 'react'
import { BrandLogo } from '../common/BrandLogo.tsx'
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
    <div className="container-xl d-flex align-items-center justify-content-center py-5 min-vh-100">
      <div className="w-100" style={{ maxWidth: '420px' }}>
        <div className="text-center mb-4">
          <BrandLogo size="lg" onClick={() => onNavigate('home')} />
          <h2 className="fs-4 fw-bold mt-3 mb-1 text-body">Forgot password</h2>
          <p className="text-secondary small mb-0">Enter your email to receive recovery instructions.</p>
        </div>

        <div className="nexa-card p-4 p-md-4.5">
          <ErrorBanner message={error} onDismiss={() => setError(null)} />

          {sent ? (
            <div className="text-center">
              <div className="alert alert-success small mb-3">
                If an account exists for <strong>{email}</strong>, password reset instructions and a verification code have been sent.
              </div>
              <div className="d-flex flex-column gap-2">
                <button
                  type="button"
                  className="nexa-btn-primary w-100 py-2.5"
                  onClick={() => onNavigate('otp-verify')}
                >
                  Enter Verification OTP
                </button>
                <button
                  type="button"
                  className="nexa-btn-secondary w-100 py-2.5"
                  onClick={() => onNavigate('login')}
                >
                  Back to Login
                </button>
              </div>
            </div>
          ) : (
            <form onSubmit={handleSubmit} className="d-flex flex-column gap-3">
              <div>
                <label className="form-label small fw-semibold text-body" htmlFor="forgot-email">
                  Email address
                </label>
                <input
                  id="forgot-email"
                  type="email"
                  className="nexa-input"
                  placeholder="name@example.com"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  disabled={loading}
                />
              </div>

              <button
                type="submit"
                className="nexa-btn-primary w-100 py-2.5 mt-1"
                disabled={loading}
              >
                {loading ? 'Sending instructions...' : 'Send reset instructions'}
              </button>

              <div className="text-center mt-3">
                <button
                  type="button"
                  className="btn btn-link p-0 small text-decoration-none"
                  style={{ color: 'var(--brand-primary)' }}
                  onClick={() => onNavigate('login')}
                >
                  Remember your password? Login
                </button>
              </div>
            </form>
          )}
        </div>
      </div>
    </div>
  )
}
