import { useState, type FormEvent } from 'react'
import { BrandLogo } from '../common/BrandLogo.tsx'
import { CheckCircle2, ArrowRight } from 'lucide-react'
import { ErrorBanner } from '../common/ErrorBanner.tsx'
import { request } from '../../services/apiClient.ts'
import type { AppRoute } from '../../types/index.ts'

export function ResetPasswordPage({ onNavigate }: { readonly onNavigate: (route: AppRoute) => void }) {
  const [token, setToken] = useState(() => sessionStorage.getItem('nexa_reset_token') || '')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState(false)

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)

    if (newPassword !== confirmPassword) {
      setError('Passwords do not match.')
      return
    }

    if (newPassword.length < 8) {
      setError('New password must be at least 8 characters long.')
      return
    }

    if (!token.trim()) {
      setError('A valid reset token is required.')
      return
    }

    setLoading(true)
    try {
      await request<{ message: string }>('/api/v1/auth/password/reset', {
        method: 'POST',
        body: JSON.stringify({ token: token.trim(), newPassword }),
      })
      sessionStorage.removeItem('nexa_reset_token')
      setSuccess(true)
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to reset password. Please verify token and retry.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="container-xl d-flex align-items-center justify-content-center py-5 min-vh-100">
      <div className="w-100" style={{ maxWidth: '420px' }}>
        <div className="text-center mb-4">
          <BrandLogo size="lg" onClick={() => onNavigate('home')} />
          <h2 className="fs-4 fw-bold mt-3 mb-1 text-body">Create New Password</h2>
          <p className="text-secondary small mb-0">Set a secure password for your NexaAI account.</p>
        </div>

        <div className="nexa-card p-4 p-md-4.5">
          <ErrorBanner message={error} onDismiss={() => setError(null)} />

          {success ? (
            <div className="text-center py-2">
              <CheckCircle2 size={40} className="text-success mx-auto mb-3" />
              <h5 className="fw-bold mb-2">Password Reset Complete</h5>
              <p className="text-secondary small mb-4">
                Your password has been securely updated. You can now sign in with your new credentials.
              </p>
              <button
                type="button"
                className="nexa-btn-primary w-100 py-2.5"
                onClick={() => onNavigate('login')}
              >
                <span>Proceed to Login</span>
                <ArrowRight size={15} />
              </button>
            </div>
          ) : (
            <form onSubmit={handleSubmit} className="d-flex flex-column gap-3">
              <div>
                <label className="form-label small fw-semibold text-body" htmlFor="reset-token">
                  Reset Token / Code
                </label>
                <input
                  id="reset-token"
                  type="text"
                  className="nexa-input font-monospace"
                  placeholder="Enter token from email"
                  required
                  value={token}
                  onChange={(e) => setToken(e.target.value)}
                  disabled={loading}
                />
              </div>

              <div>
                <label className="form-label small fw-semibold text-body" htmlFor="reset-new-password">
                  New Password
                </label>
                <input
                  id="reset-new-password"
                  type="password"
                  className="nexa-input"
                  placeholder="At least 8 characters"
                  required
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  disabled={loading}
                />
              </div>

              <div>
                <label className="form-label small fw-semibold text-body" htmlFor="reset-confirm-password">
                  Confirm New Password
                </label>
                <input
                  id="reset-confirm-password"
                  type="password"
                  className="nexa-input"
                  placeholder="Confirm password"
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
                {loading ? 'Resetting password...' : 'Update Password'}
              </button>

              <div className="text-center mt-3">
                <button
                  type="button"
                  className="btn btn-link p-0 small text-decoration-none text-secondary"
                  onClick={() => onNavigate('login')}
                >
                  Return to Login
                </button>
              </div>
            </form>
          )}
        </div>
      </div>
    </div>
  )
}
