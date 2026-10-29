interface LoadingSpinnerProps {
  readonly message?: string
  readonly withBot?: boolean
}

export function LoadingSpinner({ message = 'Loading...', withBot = false }: LoadingSpinnerProps) {
  if (withBot) {
    return (
      <div className="d-flex flex-column align-items-center justify-content-center p-4 text-secondary">
        <div className="position-relative mb-2">
          <img
            src="/assets/nexa-avatar.png"
            alt="NexaAI Bot"
            width={44}
            height={44}
            className="rounded-circle shadow-sm border border-subtle"
            style={{ objectFit: 'cover' }}
          />
          <div
            className="position-absolute bottom-0 end-0 spinner-border text-primary"
            style={{ width: '16px', height: '16px', borderWidth: '2px' }}
            role="status"
          >
            <span className="visually-hidden">Loading...</span>
          </div>
        </div>
        <span className="small text-muted">{message}</span>
      </div>
    )
  }

  return (
    <div className="d-flex align-items-center justify-content-center gap-2 p-4 text-secondary">
      <div className="spinner-border spinner-border-sm text-primary" role="status">
        <span className="visually-hidden">Loading...</span>
      </div>
      <span className="small">{message}</span>
    </div>
  )
}
