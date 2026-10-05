/**
 * Loading, error and empty states.
 *
 * <p>These exist as one file because they are one decision: every async view in this application
 * has the same three possible outcomes, and each needs an answer that is specific rather than
 * generic.
 *
 * <p><strong>Why they are not inline `if (loading) return null`.</strong> A blank region is not a
 * loading state. It reads as a broken page, it collapses the layout so everything jumps when the
 * content arrives, and it gives a screen reader nothing to announce. Each state below occupies
 * space, announces itself, and explains what is happening.
 */

import { AlertTriangle, Loader2, MessageSquareDashed } from 'lucide-react'

import { ApiError } from '../api/client'

/**
 * A spinner with a label.
 *
 * <p>`role="status"` plus `aria-live` means the wait is announced. A spinner with no accessible
 * name is a silent pause, which is the worst possible experience for a screen-reader user.
 */
export function LoadingState({ label }: { readonly label: string }): React.JSX.Element {
  return (
    <div className="nexa-state nexa-state-loading" role="status" aria-live="polite">
      <Loader2 className="nexa-state-spinner" size={22} aria-hidden="true" />
      <p className="nexa-state-label mb-0">{label}</p>
    </div>
  )
}

/**
 * A skeleton for the message thread.
 *
 * <p>Used instead of a spinner once the conversation is known: a thread-shaped placeholder keeps
 * the layout stable, so the content does not push the composer down when it arrives.
 */
export function MessageSkeleton({ rows = 3 }: { readonly rows?: number }): React.JSX.Element {
  const safeRows = Math.max(1, Math.min(rows, 8))
  return (
    <div className="nexa-thread-skeleton" role="status" aria-live="polite">
      <span className="visually-hidden">Loading conversation</span>
      {Array.from({ length: safeRows }, (_, index) => (
        <div
          key={index}
          className={`nexa-skeleton-bubble ${index % 2 === 0 ? 'is-user' : 'is-assistant'}`}
          aria-hidden="true"
        />
      ))}
    </div>
  )
}

/**
 * An error, with the trace id when there is one.
 *
 * <p>The trace id is shown because it is the only thing that lets a user report the problem and a
 * support engineer find the matching log line. Showing a generic message with no reference makes
 * a bug report unactionable.
 *
 * <p>The underlying error message is deliberately NOT shown. Some backend errors carry internal
 * detail, and the user-facing text comes from the API's `message`, which is written for them.
 */
export function ErrorState({
  error,
  onRetry,
  title = 'Something went wrong',
}: {
  readonly error: unknown
  readonly onRetry?: () => void
  readonly title?: string
}): React.JSX.Element {
  const apiError = error instanceof ApiError ? error : null
  const message =
    apiError?.displayMessage ?? 'The request could not be completed. Please try again.'

  return (
    <div className="alert alert-danger nexa-state nexa-state-error" role="alert">
      <div className="d-flex align-items-start gap-2">
        <AlertTriangle size={20} className="flex-shrink-0 mt-1" aria-hidden="true" />
        <div className="flex-grow-1">
          <h2 className="h6 alert-heading mb-1">{title}</h2>
          <p className="mb-2">{message}</p>

          {apiError?.traceId !== null && apiError?.traceId !== undefined ? (
            <p className="small mb-2">
              Quote this reference when reporting the problem:{' '}
              <code className="user-select-all">{apiError.traceId}</code>
            </p>
          ) : null}

          {onRetry !== undefined ? (
            <button type="button" className="btn btn-sm btn-outline-danger" onClick={onRetry}>
              Try again
            </button>
          ) : null}
        </div>
      </div>
    </div>
  )
}

/**
 * Nothing here yet.
 *
 * <p>An empty state carries an action. "No conversations" on its own is a dead end; "No
 * conversations — start one" is a route onwards.
 */
export function EmptyState({
  title,
  description,
  action,
}: {
  readonly title: string
  readonly description: string
  readonly action?: React.ReactNode
}): React.JSX.Element {
  return (
    <div className="nexa-state nexa-state-empty text-center">
      <MessageSquareDashed size={28} className="nexa-state-icon" aria-hidden="true" />
      <h2 className="h6 mt-2 mb-1">{title}</h2>
      <p className="text-body-secondary nexa-measure mx-auto mb-3">{description}</p>
      {action}
    </div>
  )
}
