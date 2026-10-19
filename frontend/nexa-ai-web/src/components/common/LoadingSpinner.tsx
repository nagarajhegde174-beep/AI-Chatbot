interface LoadingSpinnerProps {
  readonly message?: string
}

export function LoadingSpinner({ message = 'Loading...' }: LoadingSpinnerProps) {
  return (
    <div className="d-flex align-items-center justify-content-center gap-2 p-4 text-secondary">
      <div className="spinner-border spinner-border-sm" role="status">
        <span className="visually-hidden">Loading...</span>
      </div>
      <span className="small">{message}</span>
    </div>
  )
}
