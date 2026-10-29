import { useState, type FormEvent } from 'react'
import { BrandLogo } from '../common/BrandLogo.tsx'
import { authService } from '../../services/authService.ts'
import { ErrorBanner } from '../common/ErrorBanner.tsx'
import type { AppRoute } from '../../types/index.ts'

interface RegisterPageProps {
  readonly onNavigate: (route: AppRoute) => void
}

export function RegisterPage({ onNavigate }: RegisterPageProps) {
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState(false)

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)

    if (password !== confirmPassword) {
      setError('Passwords do not match. Please re-enter your password.')
      return
    }

    if (password.length < 8) {
      setError('Password must be at least 8 characters long.')
      return
    }

    setLoading(true)
    try {
      await authService.register(email.trim(), password, name.trim())
      setSuccess(true)
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Registration failed. Please check inputs.')
    } finally {
      setLoading(false)
    }
  }

  if (success) {
    return (
      <div className="container-xl d-flex align-items-center justify-content-center py-5 min-vh-100">
        <div className="w-100 text-center" style={{ maxWidth: '440px' }}>
          <BrandLogo size="lg" onClick={() => onNavigate('home')} />
          <div className="nexa-card p-4 mt-4">
            <h3 className="fs-5 fw-bold mb-2 text-body">Verify your email</h3>
            <p className="text-secondary small mb-4">
              A verification token has been issued for <strong>{email}</strong>. Please enter the OTP code or follow the link to activate your account.
            </p>
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
        </div>
      </div>
    )
  }

  return (
    <div className="container-xl d-flex align-items-center justify-content-center py-5 min-vh-100">
      <div className="w-100" style={{ maxWidth: '420px' }}>
        <div className="text-center mb-4">
          <BrandLogo size="lg" onClick={() => onNavigate('home')} />
          <h2 className="fs-4 fw-bold mt-3 mb-1 text-body">Create account</h2>
          <p className="text-secondary small mb-0">Start chatting with multi-model AI and document RAG.</p>
        </div>

        <div className="nexa-card p-4 p-md-4.5">
          <ErrorBanner message={error} onDismiss={() => setError(null)} />

          <form onSubmit={handleSubmit} className="d-flex flex-column gap-3">
            <div>
              <label className="form-label small fw-semibold text-body" htmlFor="register-name">
                Name
              </label>
              <input
                id="register-name"
                type="text"
                className="nexa-input"
                placeholder="Ada Lovelace"
                required
                value={name}
                onChange={(e) => setName(e.target.value)}
                disabled={loading}
              />
            </div>

            <div>
              <label className="form-label small fw-semibold text-body" htmlFor="register-email">
                Email
              </label>
              <input
                id="register-email"
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
              <label className="form-label small fw-semibold text-body" htmlFor="register-password">
                Password
              </label>
              <input
                id="register-password"
                type="password"
                className="nexa-input"
                placeholder="At least 8 characters"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                disabled={loading}
              />
            </div>

            <div>
              <label className="form-label small fw-semibold text-body" htmlFor="register-confirm-password">
                Confirm Password
              </label>
              <input
                id="register-confirm-password"
                type="password"
                className="nexa-input"
                placeholder="Confirm your password"
                required
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                disabled={loading}
              />
            </div>

            <button
              type="submit"
              className="nexa-btn-primary w-100 py-2.5 mt-1"
              disabled={loading}
            >
              {loading ? 'Creating account...' : 'Create Account'}
            </button>
          </form>

          <div className="text-center mt-4 text-secondary small">
            Already have an account?{' '}
            <button
              type="button"
              className="btn btn-link p-0 small fw-semibold text-decoration-none"
              style={{ color: 'var(--brand-primary)' }}
              onClick={() => onNavigate('login')}
            >
              Login
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
