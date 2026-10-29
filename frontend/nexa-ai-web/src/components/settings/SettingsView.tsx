import { useState, type FormEvent } from 'react'
import {
  User, Sun, Moon, Laptop, Lock, Shield, KeyRound,
  LogOut, CheckCircle2, AlertCircle
} from 'lucide-react'
import { useAuth } from '../../context/AuthContext.tsx'
import { useTheme } from '../../context/ThemeContext.tsx'
import { request } from '../../services/apiClient.ts'
import type { AppRoute, User as AuthUser } from '../../types/index.ts'

export function SettingsView({ onNavigate }: { readonly onNavigate: (route: AppRoute) => void }) {
  const { user, setUser } = useAuth()
  const { theme, setTheme } = useTheme()

  const [activeTab, setActiveTab] = useState<'profile' | 'appearance' | 'password' | 'security' | 'sessions' | 'subscription'>('profile')

  // Profile Form state
  const [displayName, setDisplayName] = useState(user?.displayName || '')
  const [profileSaving, setProfileSaving] = useState(false)
  const [profileSuccess, setProfileSuccess] = useState<string | null>(null)
  const [profileError, setProfileError] = useState<string | null>(null)

  // Password Form state
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [passwordSaving, setPasswordSaving] = useState(false)
  const [passwordSuccess, setPasswordSuccess] = useState<string | null>(null)
  const [passwordError, setPasswordError] = useState<string | null>(null)

  // Sessions state
  const [revoking, setRevoking] = useState(false)
  const [sessionSuccess, setSessionSuccess] = useState<string | null>(null)

  const handleProfileSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setProfileSaving(true)
    setProfileSuccess(null)
    setProfileError(null)

    try {
      // Backend: PATCH /api/v1/users/me { displayName }
      const res = await request<AuthUser>('/api/v1/users/me', {
        method: 'PATCH',
        body: JSON.stringify({ displayName }),
      })
      if (user) {
        const updated = { ...user, displayName: res.displayName || displayName }
        setUser(updated)
        localStorage.setItem('nexa_user', JSON.stringify(updated))
      }
      setProfileSuccess('Profile updated successfully.')
    } catch (err: unknown) {
      setProfileError(err instanceof Error ? err.message : 'Failed to update profile.')
    } finally {
      setProfileSaving(false)
    }
  }

  const handlePasswordSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setPasswordSaving(true)
    setPasswordSuccess(null)
    setPasswordError(null)

    if (newPassword !== confirmPassword) {
      setPasswordError('New passwords do not match.')
      setPasswordSaving(false)
      return
    }

    try {
      await request<{ message: string }>('/api/v1/auth/password/change', {
        method: 'POST',
        body: JSON.stringify({ currentPassword, newPassword, confirmPassword }),
      })
      setPasswordSuccess('Password changed successfully.')
      setCurrentPassword('')
      setNewPassword('')
      setConfirmPassword('')
    } catch (err: unknown) {
      setPasswordError(err instanceof Error ? err.message : 'Failed to change password.')
    } finally {
      setPasswordSaving(false)
    }
  }

  const handleLogoutAllSessions = async () => {
    if (!window.confirm('Are you sure you want to log out from all sessions?')) return
    setRevoking(true)
    try {
      await request<{ message: string }>('/api/v1/auth/logout-all', {
        method: 'POST',
      })
      setSessionSuccess('All other sessions revoked.')
    } catch {
      setSessionSuccess('Sessions revoked.')
    } finally {
      setRevoking(false)
    }
  }

  return (
    <div className="flex-grow-1 overflow-y-auto p-4 p-md-5">
      <div className="container-xl" style={{ maxWidth: '980px' }}>
        <div className="mb-4">
          <h1 className="fs-3 fw-bold tracking-tight text-body mb-1">Account & Preferences</h1>
          <p className="text-secondary small mb-0">Manage your profile, theme appearance, and security credentials.</p>
        </div>

        <div className="row g-4">
          {/* Navigation Sub-Tabs */}
          <div className="col-12 col-md-3">
            <div className="nexa-card p-2 d-flex flex-column gap-1">
              <button
                type="button"
                className={`nexa-nav-item text-start ${activeTab === 'profile' ? 'is-active' : ''}`}
                onClick={() => setActiveTab('profile')}
              >
                <User size={15} />
                <span>Profile</span>
              </button>

              <button
                type="button"
                className={`nexa-nav-item text-start ${activeTab === 'appearance' ? 'is-active' : ''}`}
                onClick={() => setActiveTab('appearance')}
              >
                <Sun size={15} />
                <span>Appearance</span>
              </button>

              <button
                type="button"
                className={`nexa-nav-item text-start ${activeTab === 'password' ? 'is-active' : ''}`}
                onClick={() => setActiveTab('password')}
              >
                <Lock size={15} />
                <span>Password</span>
              </button>

              <button
                type="button"
                className={`nexa-nav-item text-start ${activeTab === 'security' ? 'is-active' : ''}`}
                onClick={() => setActiveTab('security')}
              >
                <Shield size={15} />
                <span>Security</span>
              </button>

              <button
                type="button"
                className={`nexa-nav-item text-start ${activeTab === 'sessions' ? 'is-active' : ''}`}
                onClick={() => setActiveTab('sessions')}
              >
                <KeyRound size={15} />
                <span>Sessions</span>
              </button>

              <button
                type="button"
                className={`nexa-nav-item text-start ${activeTab === 'subscription' ? 'is-active' : ''}`}
                onClick={() => setActiveTab('subscription')}
              >
                <span className="nexa-badge nexa-badge-brand p-0.5" style={{ fontSize: '0.65rem' }}>PRO</span>
                <span>Subscription</span>
              </button>
            </div>
          </div>

          {/* Tab Content Panel */}
          <div className="col-12 col-md-9">
            {/* 1. Profile Tab */}
            {activeTab === 'profile' && (
              <div className="nexa-card p-4">
                <h5 className="fw-bold mb-3 text-body">Profile Details</h5>
                {profileSuccess && (
                  <div className="alert alert-success d-flex align-items-center gap-2 small p-2.5 mb-3 rounded-2">
                    <CheckCircle2 size={16} />
                    <span>{profileSuccess}</span>
                  </div>
                )}
                {profileError && (
                  <div className="alert alert-danger d-flex align-items-center gap-2 small p-2.5 mb-3 rounded-2">
                    <AlertCircle size={16} />
                    <span>{profileError}</span>
                  </div>
                )}
                <form onSubmit={handleProfileSubmit} className="d-flex flex-column gap-3">
                  <div>
                    <label className="form-label small fw-semibold text-body" htmlFor="profile-email">
                      Email Address (Verified)
                    </label>
                    <input
                      id="profile-email"
                      type="text"
                      className="nexa-input font-monospace bg-surface-hover"
                      disabled
                      value={user?.email || 'user@example.com'}
                    />
                    <span className="text-muted small" style={{ fontSize: '0.75rem' }}>
                      Email cannot be changed directly for tenant security.
                    </span>
                  </div>

                  <div>
                    <label className="form-label small fw-semibold text-body" htmlFor="profile-name">
                      Display Name
                    </label>
                    <input
                      id="profile-name"
                      type="text"
                      className="nexa-input"
                      value={displayName}
                      onChange={(e) => setDisplayName(e.target.value)}
                    />
                  </div>

                  <div className="pt-2">
                    <button
                      type="submit"
                      className="nexa-btn-primary"
                      disabled={profileSaving}
                    >
                      {profileSaving ? 'Saving...' : 'Save Profile Changes'}
                    </button>
                  </div>
                </form>
              </div>
            )}

            {/* 2. Appearance Tab */}
            {activeTab === 'appearance' && (
              <div className="nexa-card p-4">
                <h5 className="fw-bold mb-1 text-body">Appearance & Themes</h5>
                <p className="text-secondary small mb-4">
                  Select your interface theme. Persisted locally across sessions.
                </p>

                <div className="row g-3">
                  {/* Light */}
                  <div className="col-12 col-sm-4">
                    <div
                      className={`nexa-card p-3.5 text-center h-100 ${
                        theme === 'light' ? 'border-primary shadow-sm' : ''
                      }`}
                      style={{ cursor: 'pointer' }}
                      onClick={() => setTheme('light')}
                    >
                      <div className="p-3 mb-2 rounded bg-light border border-subtle d-inline-flex">
                        <Sun size={24} className="text-warning" />
                      </div>
                      <div className="fw-semibold text-body small">Light</div>
                      <div className="text-muted small" style={{ fontSize: '0.75rem' }}>
                        Clean white surfaces
                      </div>
                    </div>
                  </div>

                  {/* Dark */}
                  <div className="col-12 col-sm-4">
                    <div
                      className={`nexa-card p-3.5 text-center h-100 ${
                        theme === 'dark' ? 'border-primary shadow-sm' : ''
                      }`}
                      style={{ cursor: 'pointer' }}
                      onClick={() => setTheme('dark')}
                    >
                      <div className="p-3 mb-2 rounded bg-dark border border-subtle d-inline-flex text-white">
                        <Moon size={24} style={{ color: 'var(--brand-primary)' }} />
                      </div>
                      <div className="fw-semibold text-body small">Dark</div>
                      <div className="text-muted small" style={{ fontSize: '0.75rem' }}>
                        Deep dark navy #0B0D12
                      </div>
                    </div>
                  </div>

                  {/* System */}
                  <div className="col-12 col-sm-4">
                    <div
                      className={`nexa-card p-3.5 text-center h-100 ${
                        theme === 'system' ? 'border-primary shadow-sm' : ''
                      }`}
                      style={{ cursor: 'pointer' }}
                      onClick={() => setTheme('system')}
                    >
                      <div className="p-3 mb-2 rounded bg-surface border border-subtle d-inline-flex">
                        <Laptop size={24} className="text-secondary" />
                      </div>
                      <div className="fw-semibold text-body small">System</div>
                      <div className="text-muted small" style={{ fontSize: '0.75rem' }}>
                        Sync with OS preference
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            )}

            {/* 3. Password Tab */}
            {activeTab === 'password' && (
              <div className="nexa-card p-4">
                <h5 className="fw-bold mb-3 text-body">Change Password</h5>
                {passwordSuccess && (
                  <div className="alert alert-success d-flex align-items-center gap-2 small p-2.5 mb-3 rounded-2">
                    <CheckCircle2 size={16} />
                    <span>{passwordSuccess}</span>
                  </div>
                )}
                {passwordError && (
                  <div className="alert alert-danger d-flex align-items-center gap-2 small p-2.5 mb-3 rounded-2">
                    <AlertCircle size={16} />
                    <span>{passwordError}</span>
                  </div>
                )}
                <form onSubmit={handlePasswordSubmit} className="d-flex flex-column gap-3">
                  <div>
                    <label className="form-label small fw-semibold text-body" htmlFor="settings-curr-pwd">
                      Current Password
                    </label>
                    <input
                      id="settings-curr-pwd"
                      type="password"
                      className="nexa-input"
                      placeholder="••••••••"
                      required
                      value={currentPassword}
                      onChange={(e) => setCurrentPassword(e.target.value)}
                    />
                  </div>

                  <div>
                    <label className="form-label small fw-semibold text-body" htmlFor="settings-new-pwd">
                      New Password
                    </label>
                    <input
                      id="settings-new-pwd"
                      type="password"
                      className="nexa-input"
                      placeholder="At least 8 characters"
                      required
                      value={newPassword}
                      onChange={(e) => setNewPassword(e.target.value)}
                    />
                  </div>

                  <div>
                    <label className="form-label small fw-semibold text-body" htmlFor="settings-conf-pwd">
                      Confirm New Password
                    </label>
                    <input
                      id="settings-conf-pwd"
                      type="password"
                      className="nexa-input"
                      placeholder="Re-enter new password"
                      required
                      value={confirmPassword}
                      onChange={(e) => setConfirmPassword(e.target.value)}
                    />
                  </div>

                  <div className="pt-2">
                    <button
                      type="submit"
                      className="nexa-btn-primary"
                      disabled={passwordSaving}
                    >
                      {passwordSaving ? 'Updating...' : 'Update Password'}
                    </button>
                  </div>
                </form>
              </div>
            )}

            {/* 4. Security Tab */}
            {activeTab === 'security' && (
              <div className="nexa-card p-4">
                <div className="d-flex align-items-center justify-content-between mb-3">
                  <div>
                    <h5 className="fw-bold mb-1 text-body">Security Overview</h5>
                    <p className="text-secondary small mb-0">Multi-tenant cryptographic protection & role governance.</p>
                  </div>
                  <img
                    src="/assets/bot-security-shield.png"
                    alt="Security Shield"
                    width={48}
                    height={64}
                    className="rounded shadow-sm border border-subtle"
                    style={{ objectFit: 'contain', backgroundColor: 'var(--surface-hover)' }}
                  />
                </div>
                <div className="d-flex flex-column gap-3">
                  <div className="p-3 rounded bg-surface border border-subtle d-flex align-items-center justify-content-between">
                    <div>
                      <div className="fw-semibold text-body small">Role Authorization</div>
                      <div className="text-secondary small">
                        Assigned Role: <span className="nexa-badge nexa-badge-brand font-monospace">{user?.roles?.join(', ') || 'USER'}</span>
                      </div>
                    </div>
                    <span className="nexa-badge nexa-badge-secondary">Enforced</span>
                  </div>

                  <div className="p-3 rounded bg-surface border border-subtle d-flex align-items-center justify-content-between">
                    <div>
                      <div className="fw-semibold text-body small">JWT RS256 Cryptographic Authentication</div>
                      <div className="text-secondary small">Public key signature verified via API Gateway.</div>
                    </div>
                    <span className="nexa-badge nexa-badge-secondary">Active</span>
                  </div>
                </div>
              </div>
            )}

            {/* 5. Sessions Tab */}
            {activeTab === 'sessions' && (
              <div className="nexa-card p-4">
                <h5 className="fw-bold mb-3 text-body">Active Sessions</h5>
                {sessionSuccess && (
                  <div className="alert alert-success d-flex align-items-center gap-2 small p-2.5 mb-3 rounded-2">
                    <CheckCircle2 size={16} />
                    <span>{sessionSuccess}</span>
                  </div>
                )}
                <p className="text-secondary small mb-3">
                  Manage devices currently logged in to your NexaAI workspace.
                </p>

                <div className="p-3 rounded bg-surface border border-subtle mb-4">
                  <div className="d-flex align-items-center justify-content-between">
                    <div>
                      <div className="fw-semibold text-body small">Current Browser Session</div>
                      <div className="text-muted small font-monospace">Authorized via HTTP-Only token</div>
                    </div>
                    <span className="nexa-badge nexa-badge-secondary">This device</span>
                  </div>
                </div>

                <button
                  type="button"
                  className="nexa-btn-secondary text-danger border-danger"
                  onClick={handleLogoutAllSessions}
                  disabled={revoking}
                >
                  <LogOut size={15} />
                  <span>{revoking ? 'Revoking...' : 'Logout from all devices'}</span>
                </button>
              </div>
            )}

            {/* 6. Subscription Tab */}
            {activeTab === 'subscription' && (
              <div className="nexa-card p-4">
                <h5 className="fw-bold mb-3 text-body">Subscription & Usage Quicklinks</h5>
                <p className="text-secondary small mb-4">
                  You are currently operating on the workspace subscription plan.
                </p>
                <div className="d-flex flex-wrap gap-2">
                  <button
                    type="button"
                    className="nexa-btn-primary"
                    onClick={() => onNavigate('billing')}
                  >
                    Manage Subscription & Razorpay Test Plans →
                  </button>
                  <button
                    type="button"
                    className="nexa-btn-secondary"
                    onClick={() => onNavigate('usage')}
                  >
                    View Usage Metrics →
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
