import { useState, useEffect } from 'react'
import { BrandLogo } from '../common/BrandLogo.tsx'
import { CheckCircle2, AlertCircle, ArrowRight, Loader2 } from 'lucide-react'
import { request } from '../../services/apiClient.ts'
import type { AppRoute } from '../../types/index.ts'

export function VerifyEmailPage({ onNavigate }: { readonly onNavigate: (route: AppRoute) => void }) {
  const [tokenInput, setTokenInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [status, setStatus] = useState<'idle' | 'success' | 'error'>('idle')
  const [message, setMessage] = useState<string | null>(null)

  useEffect(() => {
    // Check if token was supplied in query params
    const params = new URLSearchParams(window.location.search)
    const token = params.get('token')
    if (token) {
      setTokenInput(token)
      void verifyToken(token)
    }
  }, [])

  const verifyToken = async (tok: string) => {
    setLoading(true)
    setStatus('idle')
    setMessage(null)

    try {
      await request<{ message: string }>('/api/v1/auth/email/verify', {
        method: 'POST',
        body: JSON.stringify({ token: tok.trim() }),
      })
      setStatus('success')
      setMessage('Your email address has been successfully verified! You may now sign in.')
    } catch (err: unknown) {
      setStatus('error')
      setMessage(err instanceof Error ? err.message : 'Invalid or expired verification link.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="container-xl d-flex align-items-center justify-content-center py-5 min-vh-100">
      <div className="w-100 text-center" style={{ maxWidth: '440px' }}>
        <BrandLogo size="lg" onClick={() => onNavigate('home')} />
        <h2 className="fs-4 fw-bold mt-3 mb-1 text-body">Email Verification</h2>
        <p className="text-secondary small mb-4">Activate your NexaAI account.</p>

        <div className="nexa-card p-4 p-md-4.5 text-start">
          {status === 'success' ? (
            <div className="text-center py-2">
              <div className="mb-3 text-success">
                <CheckCircle2 size={44} className="mx-auto" />
              </div>
              <h5 className="fw-bold mb-2">Verified!</h5>
              <p className="text-secondary small mb-4">{message}</p>
              <button
                type="button"
                className="nexa-btn-primary w-100 py-2.5"
                onClick={() => onNavigate('login')}
              >
                <span>Continue to Login</span>
                <ArrowRight size={15} />
              </button>
            </div>
          ) : (
            <div>
              {status === 'error' && (
                <div className="alert alert-danger d-flex align-items-center gap-2 small mb-3">
                  <AlertCircle size={16} className="flex-shrink-0" />
                  <span>{message}</span>
                </div>
              )}

              <p className="text-secondary small mb-3">
                If you have an email verification token or received an activation link, enter your token below:
              </p>

              <div className="mb-3">
                <label className="form-label small fw-semibold text-body" htmlFor="verify-token-input">
                  Verification Token / Code
                </label>
                <input
                  id="verify-token-input"
                  type="text"
                  className="nexa-input font-monospace"
                  placeholder="e.g. 8f4e2b1a-9c7d..."
                  value={tokenInput}
                  onChange={(e) => setTokenInput(e.target.value)}
                  disabled={loading}
                />
              </div>

              <button
                type="button"
                className="nexa-btn-primary w-100 py-2.5 d-flex align-items-center justify-content-center gap-2"
                onClick={() => verifyToken(tokenInput)}
                disabled={loading || !tokenInput.trim()}
              >
                {loading && <Loader2 size={16} className="spin" />}
                <span>{loading ? 'Verifying...' : 'Verify Email'}</span>
              </button>

              <div className="text-center mt-3">
                <button
                  type="button"
                  className="btn btn-link p-0 small text-decoration-none text-secondary"
                  onClick={() => onNavigate('otp-verify')}
                >
                  Enter 6-digit OTP code instead
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
