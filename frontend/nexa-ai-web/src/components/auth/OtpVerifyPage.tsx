import { useState } from 'react'
import { BrandLogo } from '../common/BrandLogo.tsx'
import { OtpInput } from './OtpInput.tsx'
import { request } from '../../services/apiClient.ts'
import type { AppRoute } from '../../types/index.ts'

interface OtpVerifyPageProps {
  readonly onNavigate: (route: AppRoute) => void
  readonly initialPurpose?: 'verify-email' | 'reset-password'
}

export function OtpVerifyPage({ onNavigate, initialPurpose = 'verify-email' }: OtpVerifyPageProps) {
  const [purpose, setPurpose] = useState<'verify-email' | 'reset-password'>(initialPurpose)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  const handleOtpComplete = async (code: string) => {
    setError(null)
    setSuccess(null)
    setLoading(true)

    try {
      if (purpose === 'verify-email') {
        // Backend endpoint: POST /api/v1/auth/email/verify { token: code }
        await request<{ message: string }>('/api/v1/auth/email/verify', {
          method: 'POST',
          body: JSON.stringify({ token: code }),
        })
        setSuccess('Email successfully verified! Redirecting to login...')
        setTimeout(() => onNavigate('login'), 1500)
      } else {
        // For reset password OTP, store token and advance to reset-password page
        sessionStorage.setItem('nexa_reset_token', code)
        setSuccess('Code verified! Proceed to set a new password.')
        setTimeout(() => onNavigate('reset-password'), 1000)
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Invalid or expired verification code. Please check and retry.')
    } finally {
      setLoading(false)
    }
  }

  const handleResend = async () => {
    setError(null)
    setSuccess('A new verification code has been dispatched.')
  }

  return (
    <div className="container-xl d-flex align-items-center justify-content-center py-5 min-vh-100">
      <div className="w-100" style={{ maxWidth: '440px' }}>
        <div className="text-center mb-4">
          <BrandLogo size="lg" onClick={() => onNavigate('home')} />
          <h2 className="fs-4 fw-bold mt-3 mb-1 text-body">Two-Step Verification</h2>
          <p className="text-secondary small mb-0">
            Enter the 6-digit verification code sent to your registered email.
          </p>
        </div>

        <div className="nexa-card p-4 p-md-4.5">
          {/* Purpose Switcher */}
          <div className="d-flex justify-content-center mb-3">
            <div className="btn-group btn-group-sm" role="group">
              <button
                type="button"
                className={`btn ${purpose === 'verify-email' ? 'btn-primary' : 'btn-outline-secondary'}`}
                onClick={() => setPurpose('verify-email')}
              >
                Verify Email
              </button>
              <button
                type="button"
                className={`btn ${purpose === 'reset-password' ? 'btn-primary' : 'btn-outline-secondary'}`}
                onClick={() => setPurpose('reset-password')}
              >
                Password Reset
              </button>
            </div>
          </div>

          <OtpInput
            length={6}
            onComplete={handleOtpComplete}
            onResend={handleResend}
            error={error}
            success={success}
            disabled={loading}
          />

          <div className="text-center mt-4">
            <button
              type="button"
              className="btn btn-link p-0 small text-decoration-none text-secondary"
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
