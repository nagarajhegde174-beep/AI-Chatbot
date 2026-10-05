/**
 * The message thread and its bubbles.
 *
 * <p>Role is conveyed by alignment, background and an explicit label — not by colour alone. About
 * one in twelve men has some form of colour vision deficiency, so a UI where "the blue ones are
 * mine and the grey ones are the model's" is a UI some users cannot read at all.
 */

import { Check, Copy, Pencil, RefreshCw, ThumbsDown, ThumbsUp } from 'lucide-react'

import type { FeedbackRating, Message } from '../api/chat'

export function MessageThread({
  messages,
  onRegenerate,
  onRate,
  onEdit,
}: {
  readonly messages: readonly Message[]
  readonly onRegenerate: (message: Message) => void
  readonly onRate: (message: Message, rating: FeedbackRating) => void
  readonly onEdit: (message: Message) => void
}): React.JSX.Element {
  return (
    <div className="nexa-thread" role="log" aria-label="Conversation" aria-live="polite">
      {messages.map((message) => (
        <MessageBubble
          key={message.id}
          message={message}
          onRegenerate={onRegenerate}
          onRate={onRate}
          onEdit={onEdit}
        />
      ))}
    </div>
  )
}

function MessageBubble({
  message,
  onRegenerate,
  onRate,
  onEdit,
}: {
  readonly message: Message
  readonly onRegenerate: (message: Message) => void
  readonly onRate: (message: Message, rating: FeedbackRating) => void
  readonly onEdit: (message: Message) => void
}): React.JSX.Element {
  const isUser = message.role === 'USER'
  const label = isUser ? 'You' : message.role === 'ASSISTANT' ? 'Assistant' : 'System'

  return (
    <article className={`nexa-bubble ${isUser ? 'is-user' : 'is-assistant'}`}>
      <header className="nexa-bubble-header">
        <span className="nexa-bubble-author">{label}</span>
        {message.model !== null ? (
          <span className="badge text-bg-light border">{message.model}</span>
        ) : null}
        {message.edited ? (
          <span className="nexa-bubble-flag">
            <Pencil size={11} aria-hidden="true" /> edited
          </span>
        ) : null}
        {message.regenerated ? (
          <span className="nexa-bubble-flag">
            <RefreshCw size={11} aria-hidden="true" /> regenerated
          </span>
        ) : null}
      </header>

      <div className="nexa-bubble-body">
        {message.status === 'PENDING' ? (
          // The honest state: a reply is expected and has not arrived. Not an error, and not
          // empty content pretending to be an answer.
          <div className="nexa-pending" role="status">
            <span className="nexa-pending-dots" aria-hidden="true">
              <span />
              <span />
              <span />
            </span>
            <span className="visually-hidden">Waiting for a reply</span>
            <span className="nexa-pending-label">Waiting for a reply…</span>
          </div>
        ) : message.status === 'FAILED' ? (
          <div className="nexa-failed" role="alert">
            {message.failureReason ?? 'The reply could not be completed.'}
          </div>
        ) : message.content === '' ? (
          <p className="text-body-secondary fst-italic mb-0">No content</p>
        ) : (
          // whitespace-pre-wrap so a pasted code block or a list keeps its shape, which
          // pre-wrap does and a plain div does not.
          <p className="nexa-bubble-content mb-0">{message.content}</p>
        )}
      </div>

      <footer className="nexa-bubble-actions">
        <button
          type="button"
          className="btn btn-sm btn-ghost"
          aria-label="Copy message"
          onClick={() => {
            void navigator.clipboard?.writeText(message.content).catch(() => {
              // Clipboard access can be refused by permissions policy. Failing a copy must not
              // surface as an error the user has to dismiss; the text is still selectable.
            })
          }}
        >
          <Copy size={13} aria-hidden="true" />
        </button>

        {isUser ? (
          <button
            type="button"
            className="btn btn-sm btn-ghost"
            aria-label="Edit and resend"
            onClick={() => onEdit(message)}
          >
            <Pencil size={13} aria-hidden="true" />
          </button>
        ) : (
          <button
            type="button"
            className="btn btn-sm btn-ghost"
            aria-label="Regenerate this reply"
            onClick={() => onRegenerate(message)}
          >
            <RefreshCw size={13} aria-hidden="true" />
          </button>
        )}

        {!isUser && message.status === 'COMPLETE' ? (
          <>
            <button
              type="button"
              className={`btn btn-sm btn-ghost ${message.feedback?.rating === 'UP' ? 'is-active' : ''}`}
              aria-label="Good reply"
              aria-pressed={message.feedback?.rating === 'UP'}
              onClick={() => onRate(message, 'UP')}
            >
              <ThumbsUp size={13} aria-hidden="true" />
            </button>
            <button
              type="button"
              className={`btn btn-sm btn-ghost ${message.feedback?.rating === 'DOWN' ? 'is-active' : ''}`}
              aria-label="Bad reply"
              aria-pressed={message.feedback?.rating === 'DOWN'}
              onClick={() => onRate(message, 'DOWN')}
            >
              <ThumbsDown size={13} aria-hidden="true" />
            </button>
          </>
        ) : null}

        {message.status === 'COMPLETE' ? (
          <span className="nexa-bubble-status" title={message.createdAt}>
            <Check size={11} aria-hidden="true" /> Sent
          </span>
        ) : null}
      </footer>
    </article>
  )
}
