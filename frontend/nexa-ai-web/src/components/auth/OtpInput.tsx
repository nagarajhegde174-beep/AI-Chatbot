import { useState, useRef, useEffect, type ClipboardEvent, type KeyboardEvent, type ChangeEvent } from 'react'
import { RotateCw, CheckCircle2, AlertCircle } from 'lucide-react'

interface OtpInputProps {
  readonly length?: number
  readonly onComplete: (otp: string) => void
  readonly onResend?: () => Promise<void>
  readonly error?: string | null
  readonly success?: string | null
  readonly disabled?: boolean
}

export function OtpInput({
  length = 6,
  onComplete,
  onResend,
  error,
  success,
  disabled = false,
}: OtpInputProps) {
  const [digits, setDigits] = useState<string[]>(() => Array(length).fill(''))
  const [resendCooldown, setResendCooldown] = useState<number>(60)
  const [isResending, setIsResending] = useState<boolean>(false)
  const inputRefs = useRef<(HTMLInputElement | null)[]>([])

  // Auto-focus first input on mount
  useEffect(() => {
    inputRefs.current[0]?.focus()
  }, [])

  // Resend countdown timer
  useEffect(() => {
    if (resendCooldown <= 0) return
    const timer = setInterval(() => {
      setResendCooldown((prev) => Math.max(0, prev - 1))
    }, 1000)
    return () => clearInterval(timer)
  }, [resendCooldown])

  const handleChange = (index: number, e: ChangeEvent<HTMLInputElement>) => {
    const val = e.target.value.replace(/\D/g, '')
    if (!val) {
      const nextDigits = [...digits]
      nextDigits[index] = ''
      setDigits(nextDigits)
      return
    }

    const char = val[val.length - 1] || ''
    const nextDigits = [...digits]
    nextDigits[index] = char
    setDigits(nextDigits)

    if (char && index < length - 1) {
      inputRefs.current[index + 1]?.focus()
    }

    const fullCode = nextDigits.join('')
    if (fullCode.length === length && !nextDigits.includes('')) {
      onComplete(fullCode)
    }
  }

  const handleKeyDown = (index: number, e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Backspace') {
      if (!digits[index] && index > 0) {
        inputRefs.current[index - 1]?.focus()
      }
    } else if (e.key === 'ArrowLeft' && index > 0) {
      inputRefs.current[index - 1]?.focus()
    } else if (e.key === 'ArrowRight' && index < length - 1) {
      inputRefs.current[index + 1]?.focus()
    }
  }

  const handlePaste = (e: ClipboardEvent<HTMLInputElement>) => {
    e.preventDefault()
    const pasted = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, length)
    if (!pasted) return

    const nextDigits = [...digits]
    for (let i = 0; i < length; i++) {
      nextDigits[i] = pasted[i] || ''
    }
    setDigits(nextDigits)

    const focusIndex = Math.min(pasted.length, length - 1)
    inputRefs.current[focusIndex]?.focus()

    if (pasted.length === length) {
      onComplete(pasted)
    }
  }

  const handleResendClick = async () => {
    if (resendCooldown > 0 || !onResend || isResending) return
    setIsResending(true)
    try {
      await onResend()
      setResendCooldown(60)
    } finally {
      setIsResending(false)
    }
  }

  return (
    <div className="d-flex flex-column align-items-center">
      <div className="nexa-otp-container" role="group" aria-label="One-Time Password Input">
        {digits.map((digit, index) => {
          const isFilled = Boolean(digit)
          const stateClass = error ? 'is-error' : success ? 'is-success' : isFilled ? 'is-filled' : ''
          return (
            <input
              key={index}
              ref={(el) => {
                inputRefs.current[index] = el
              }}
              type="text"
              inputMode="numeric"
              autoComplete="one-time-code"
              maxLength={1}
              value={digit}
              onChange={(e) => handleChange(index, e)}
              onKeyDown={(e) => handleKeyDown(index, e)}
              onPaste={handlePaste}
              disabled={disabled}
              className={`nexa-otp-box ${stateClass}`}
              aria-label={`Digit ${index + 1} of ${length}`}
            />
          )
        })}
      </div>

      {error && (
        <div className="d-flex align-items-center gap-1.5 text-danger small mb-3">
          <AlertCircle size={14} />
          <span>{error}</span>
        </div>
      )}

      {success && (
        <div className="d-flex align-items-center gap-1.5 text-success small mb-3">
          <CheckCircle2 size={14} />
          <span>{success}</span>
        </div>
      )}

      {onResend && (
        <div className="text-center mt-2">
          {resendCooldown > 0 ? (
            <span className="text-secondary small">
              Resend code in <strong className="text-body">{resendCooldown}s</strong>
            </span>
          ) : (
            <button
              type="button"
              className="btn btn-link p-0 text-decoration-none small d-inline-flex align-items-center gap-1"
              onClick={handleResendClick}
              disabled={isResending || disabled}
            >
              <RotateCw size={13} className={isResending ? 'spin' : ''} />
              <span>Resend verification code</span>
            </button>
          )}
        </div>
      )}
    </div>
  )
}
