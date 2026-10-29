import { useState } from 'react'
import {
  AlertCircle, Check, Copy, Edit2, RotateCw,
  ThumbsDown, ThumbsUp, User as UserIcon
} from 'lucide-react'
import type { FeedbackType, Message } from '../../types/index.ts'
import { MarkdownRenderer } from './MarkdownRenderer.tsx'

interface MessageItemProps {
  readonly message: Message
  readonly onRegenerate?: (messageId: string) => Promise<void>
  readonly onRetry?: (messageId: string) => Promise<void>
  readonly onEdit?: (messageId: string, newContent: string) => Promise<void>
  readonly onFeedback?: (messageId: string, feedback: FeedbackType) => Promise<void>
}

export function MessageItem({
  message,
  onRegenerate,
  onRetry,
  onEdit,
  onFeedback,
}: MessageItemProps) {
  const isUser = message.role === 'USER'
  const [isEditing, setIsEditing] = useState(false)
  const [editContent, setEditContent] = useState(message.content)
  const [copied, setCopied] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  const handleCopy = async () => {
    await navigator.clipboard.writeText(message.content)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  const handleSaveEdit = async () => {
    if (!editContent.trim() || editContent.trim() === message.content) {
      setIsEditing(false)
      return
    }
    setSubmitting(true)
    try {
      if (onEdit) {
        await onEdit(message.id, editContent.trim())
      }
      setIsEditing(false)
    } finally {
      setSubmitting(false)
    }
  }

  const timeString = new Date(message.createdAt).toLocaleTimeString([], {
    hour: '2-digit',
    minute: '2-digit',
  })

  return (
    <div className={`d-flex gap-3 mb-4 ${isUser ? 'justify-content-end' : 'justify-content-start'}`}>
      {!isUser && (
        <div
          className="rounded-circle d-flex align-items-center justify-content-center flex-shrink-0 overflow-hidden shadow-sm"
          style={{
            width: '34px',
            height: '34px',
            border: '1.5px solid var(--border-subtle)',
            backgroundColor: 'var(--surface-card)',
          }}
        >
          <img
            src="/assets/nexa-avatar.png"
            alt="NexaAI Assistant"
            width={34}
            height={34}
            style={{ objectFit: 'cover' }}
          />
        </div>
      )}

      <div
        className={`nexa-card ${
          isUser
            ? 'bg-surface-hover border-subtle'
            : 'bg-surface border-subtle'
        }`}
        style={{ maxWidth: '82%', minWidth: '240px' }}
      >
        <div className="d-flex align-items-center justify-content-between py-2 px-3.5 border-bottom border-subtle">
          <div className="d-flex align-items-center gap-2">
            <span className="fw-semibold small text-body">{isUser ? 'You' : 'NexaAI'}</span>
            {!isUser && message.model && (
              <span className="nexa-badge nexa-badge-subtle font-monospace" style={{ fontSize: '0.68rem' }}>
                {message.model}
              </span>
            )}
            {message.isRegenerated && (
              <span className="nexa-badge nexa-badge-brand" style={{ fontSize: '0.68rem' }}>
                Regenerated
              </span>
            )}
          </div>
          <span className="text-muted small font-monospace" style={{ fontSize: '0.72rem' }}>
            {timeString}
          </span>
        </div>

        <div className="py-2.5 px-3.5">
          {isEditing ? (
            <div>
              <textarea
                className="nexa-input mb-2"
                rows={3}
                value={editContent}
                disabled={submitting}
                onChange={(e) => setEditContent(e.target.value)}
              />
              <div className="d-flex justify-content-end gap-2">
                <button
                  type="button"
                  className="nexa-btn-secondary py-1 px-2.5 small"
                  disabled={submitting}
                  onClick={() => {
                    setIsEditing(false)
                    setEditContent(message.content)
                  }}
                >
                  Cancel
                </button>
                <button
                  type="button"
                  className="nexa-btn-primary py-1 px-2.5 small"
                  disabled={submitting}
                  onClick={handleSaveEdit}
                >
                  {submitting ? 'Updating...' : 'Save & Resend'}
                </button>
              </div>
            </div>
          ) : isUser ? (
            <div className="text-break text-body" style={{ whiteSpace: 'pre-wrap', lineHeight: 1.6 }}>
              {message.content}
            </div>
          ) : (
            <div>
              {message.status === 'FAILED' && (
                <div className="text-danger d-flex align-items-center gap-2 mb-2 small">
                  <AlertCircle size={15} />
                  <span>Failed to generate response. Please retry.</span>
                </div>
              )}

              {/* AI Thinking: subtle breathing 3-dot indicator */}
              {(message.status === 'IN_PROGRESS' || message.status === 'PENDING') && !message.content && (
                <div className="nexa-thinking-indicator py-1 my-1">
                  <span className="dot" />
                  <span className="dot" />
                  <span className="dot" />
                  <span className="ms-1 small">Thinking...</span>
                </div>
              )}

              {/* Rendered Markdown Content */}
              {message.content ? (
                <div>
                  <MarkdownRenderer content={message.content} />
                  {/* Subtle Caret while streaming */}
                  {message.status === 'IN_PROGRESS' && (
                    <span className="nexa-streaming-caret" aria-hidden="true" />
                  )}
                </div>
              ) : null}
            </div>
          )}
        </div>

        {!isEditing && (
          <div className="d-flex align-items-center justify-content-between py-1.5 px-3.5 border-top border-subtle">
            <div className="d-flex align-items-center gap-2">
              <button
                type="button"
                className="btn btn-sm btn-ghost p-1 text-secondary"
                onClick={handleCopy}
                title="Copy message text"
              >
                {copied ? <Check size={13} className="text-success" /> : <Copy size={13} />}
              </button>

              {isUser && onEdit && (
                <button
                  type="button"
                  className="btn btn-sm btn-ghost p-1 text-secondary"
                  onClick={() => setIsEditing(true)}
                  title="Edit and resend"
                >
                  <Edit2 size={13} />
                </button>
              )}

              {!isUser && onFeedback && (
                <>
                  <button
                    type="button"
                    className={`btn btn-sm btn-ghost p-1 ${
                      message.feedback === 'THUMBS_UP' ? 'text-success' : 'text-secondary'
                    }`}
                    onClick={() => void onFeedback(message.id, 'THUMBS_UP')}
                    title="Helpful response"
                  >
                    <ThumbsUp size={13} />
                  </button>
                  <button
                    type="button"
                    className={`btn btn-sm btn-ghost p-1 ${
                      message.feedback === 'THUMBS_DOWN' ? 'text-danger' : 'text-secondary'
                    }`}
                    onClick={() => void onFeedback(message.id, 'THUMBS_DOWN')}
                    title="Not helpful response"
                  >
                    <ThumbsDown size={13} />
                  </button>
                </>
              )}

              {!isUser && Boolean(message.totalTokens) && (
                <span className="text-muted small font-monospace" style={{ fontSize: '0.68rem' }}>
                  {message.totalTokens} tokens
                </span>
              )}
            </div>

            <div className="d-flex align-items-center gap-2">
              {!isUser && (onRetry || onRegenerate) && (
                <button
                  type="button"
                  className="btn btn-sm btn-ghost p-1 text-secondary d-inline-flex align-items-center gap-1 small"
                  onClick={() => {
                    if (onRetry) {
                      void onRetry(message.id)
                    } else if (onRegenerate) {
                      void onRegenerate(message.id)
                    }
                  }}
                  title={message.status === 'FAILED' ? 'Retry generation' : 'Regenerate this response'}
                >
                  <RotateCw size={12} />
                  <span style={{ fontSize: '0.72rem' }}>
                    {message.status === 'FAILED' ? 'Retry' : 'Regenerate'}
                  </span>
                </button>
              )}
            </div>
          </div>
        )}
      </div>

      {isUser && (
        <div
          className="rounded-circle d-flex align-items-center justify-content-center flex-shrink-0"
          style={{
            width: '32px',
            height: '32px',
            backgroundColor: 'var(--surface-hover)',
            color: 'var(--text-secondary)',
          }}
        >
          <UserIcon size={16} />
        </div>
      )}
    </div>
  )
}
