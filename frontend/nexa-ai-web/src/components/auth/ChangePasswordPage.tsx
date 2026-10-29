import { useState, type FormEvent } from 'react'
import { BrandLogo } from '../common/BrandLogo.tsx'
import { CheckCircle2, ArrowLeft } from 'lucide-react'
import { ErrorBanner } from '../common/ErrorBanner.tsx'
import { request } from '../../services/apiClient.ts'
import type { AppRoute } from '../../types/index.ts'

export function ChangePasswordPage({ onNavigate }: { readonly onNavigate: (route: AppRoute) => void }) {
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState(false)

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)

    if (newPassword !== confirmPassword) {
      setError('New passwords do not match.')
      return
    }

    if (newPassword.length < 8) {
      setError('New password must be at least 8 characters.')
      return
    }

    setLoading(true)
    try {
      await request<{ message: string }>('/api/v1/auth/password/change', {
        method: 'POST',
        body: JSON.stringify({ currentPassword, newPassword, confirmPassword }),
      })
      setSuccess(true)
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to change password. Please verify current password.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="container-xl d-flex align-items-center justify-content-center py-5 min-vh-100">
      <div className="w-100" style={{ maxWidth: '420px' }}>
        <div className="text-center mb-4">
          <BrandLogo size="lg" onClick={() => onNavigate('app')} />
          <h2 className="fs-4 fw-bold mt-3 mb-1 text-body">Change Password</h2>
          <p className="text-secondary small mb-0">Update your account security credentials.</p>
        </div>

        <div className="nexa-card p-4 p-md-4.5">
          <ErrorBanner message={error} onDismiss={() => setError(null)} />

          {success ? (
            <div className="text-center py-2">
              <CheckCircle2 size={40} className="text-success mx-auto mb-3" />
              <h5 className="fw-bold mb-2">Password Updated</h5>
              <p className="text-secondary small mb-4">
                Your password has been changed successfully.
              </p>
              <button
                type="button"
                className="nexa-btn-primary w-100 py-2.5"
                onClick={() => onNavigate('settings')}
              >
                <span>Back to Settings</span>
              </button>
            </div>
          ) : (
            <form onSubmit={handleSubmit} className="d-flex flex-column gap-3">
              <div>
                <label className="form-label small fw-semibold text-body" htmlFor="current-password">
                  Current Password
                </label>
                <input
                  id="current-password"
                  type="password"
                  className="nexa-input"
                  placeholder="••••••••"
                  required
                  value={currentPassword}
                  onChange={(e) => setCurrentPassword(e.target.value)}
                  disabled={loading}
                />
              </div>

              <div>
                <label className="form-label small fw-semibold text-body" htmlFor="new-password">
                  New Password
                </label>
                <input
                  id="new-password"
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
                <label className="form-label small fw-semibold text-body" htmlFor="confirm-new-password">
                  Confirm New Password
                </label>
                <input
                  id="confirm-new-password"
                  type="password"
                  className="nexa-input"
                  placeholder="Re-enter new password"
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
                {loading ? 'Updating...' : 'Change Password'}
              </button>

              <div className="text-center mt-3">
                <button
                  type="button"
                  className="btn btn-link p-0 small text-decoration-none text-secondary d-inline-flex align-items-center gap-1"
                  onClick={() => onNavigate('settings')}
                >
                  <ArrowLeft size={14} />
                  <span>Back to Settings</span>
                </button>
              </div>
            </form>
          )}
        </div>
      </div>
    </div>
  )
}
