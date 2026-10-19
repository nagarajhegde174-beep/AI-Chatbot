import { AlertCircle } from 'lucide-react'

interface ErrorBannerProps {
  readonly message: string | null
  readonly onDismiss?: () => void
  readonly onRetry?: () => void
}

export function ErrorBanner({ message, onDismiss, onRetry }: ErrorBannerProps) {
  if (!message) return null

  return (
    <div className="alert alert-danger d-flex align-items-center justify-content-between py-2 px-3 mb-3" role="alert">
      <div className="d-flex align-items-center gap-2">
        <AlertCircle size={18} className="flex-shrink-0" />
        <span className="small">{message}</span>
        {onRetry && (
          <button
            type="button"
            className="btn btn-sm btn-outline-danger ms-2 py-0 px-2"
            onClick={onRetry}
          >
            Retry
          </button>
        )}
      </div>
      {onDismiss && (
        <button
          type="button"
          className="btn-close"
          aria-label="Close"
          onClick={onDismiss}
        />
      )}
    </div>
  )
}
