import { useState } from 'react'
import { AlertCircle, Bot, Check, Copy, Edit2, RotateCw, ThumbsDown, ThumbsUp, User as UserIcon } from 'lucide-react'
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
          className="rounded-circle bg-primary-subtle text-primary d-flex align-items-center justify-content-center flex-shrink-0"
          style={{ width: '34px', height: '34px' }}
        >
          <Bot size={18} />
        </div>
      )}

      <div
        className={`card border shadow-none ${
          isUser ? 'bg-primary-subtle border-primary-subtle' : 'bg-body border'
        }`}
        style={{ maxWidth: '85%', minWidth: '220px' }}
      >
        <div className="card-header bg-transparent border-0 d-flex align-items-center justify-content-between py-1 px-3">
          <div className="d-flex align-items-center gap-2">
            <span className="fw-semibold small">{isUser ? 'You' : 'NexaAI Assistant'}</span>
            {!isUser && message.model && (
              <span className="badge text-bg-secondary-subtle border small text-secondary py-0 px-1" style={{ fontSize: '0.68rem' }}>
                {message.model}
              </span>
            )}
            {message.isRegenerated && (
              <span className="badge text-bg-info-subtle border small text-info py-0 px-1" style={{ fontSize: '0.68rem' }}>
                Regenerated
              </span>
            )}
          </div>
          <span className="text-secondary small" style={{ fontSize: '0.72rem' }}>
            {timeString}
          </span>
        </div>

        <div className="card-body py-2 px-3">
          {isEditing ? (
            <div>
              <textarea
                className="form-control mb-2"
                rows={3}
                value={editContent}
                disabled={submitting}
                onChange={(e) => setEditContent(e.target.value)}
              />
              <div className="d-flex justify-content-end gap-2">
                <button
                  type="button"
                  className="btn btn-sm btn-outline-secondary"
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
                  className="btn btn-sm btn-primary"
                  disabled={submitting}
                  onClick={handleSaveEdit}
                >
                  {submitting ? 'Updating...' : 'Save & Resend'}
                </button>
              </div>
            </div>
          ) : isUser ? (
            <div className="text-break" style={{ whiteSpace: 'pre-wrap' }}>
              {message.content}
            </div>
          ) : (
            <div>
              {message.status === 'FAILED' && (
                <div className="text-danger d-flex align-items-center gap-2 mb-2 small">
                  <AlertCircle size={16} />
                  <span>Failed to generate response</span>
                </div>
              )}
              {message.content ? (
                <MarkdownRenderer content={message.content} />
              ) : message.status === 'IN_PROGRESS' || message.status === 'PENDING' ? (
                <div className="text-secondary small d-flex align-items-center gap-2 py-1">
                  <div className="spinner-border spinner-border-sm text-primary" role="status" />
                  <span>Thinking...</span>
                </div>
              ) : null}
              {(message.status === 'IN_PROGRESS' || message.status === 'PENDING') && message.content && (
                <span className="spinner-grow spinner-grow-sm text-primary ms-1 align-baseline" role="status" style={{ width: '0.5rem', height: '0.5rem' }} />
              )}
            </div>
          )}
        </div>

        {!isEditing && (
          <div className="card-footer bg-transparent border-0 d-flex align-items-center justify-content-between py-1 px-3">
            <div className="d-flex align-items-center gap-2">
              <button
                type="button"
                className="btn btn-sm btn-link p-0 text-secondary text-decoration-none"
                onClick={handleCopy}
                title="Copy message"
              >
                {copied ? <Check size={14} className="text-success" /> : <Copy size={14} />}
              </button>

              {isUser && onEdit && (
                <button
                  type="button"
                  className="btn btn-sm btn-link p-0 text-secondary text-decoration-none"
                  onClick={() => setIsEditing(true)}
                  title="Edit and resend"
                >
                  <Edit2 size={14} />
                </button>
              )}

              {!isUser && onFeedback && (
                <>
                  <button
                    type="button"
                    className={`btn btn-sm btn-link p-0 text-decoration-none ${
                      message.feedback === 'THUMBS_UP' ? 'text-success' : 'text-secondary'
                    }`}
                    onClick={() => void onFeedback(message.id, 'THUMBS_UP')}
                    title="Helpful response"
                  >
                    <ThumbsUp size={14} />
                  </button>
                  <button
                    type="button"
                    className={`btn btn-sm btn-link p-0 text-decoration-none ${
                      message.feedback === 'THUMBS_DOWN' ? 'text-danger' : 'text-secondary'
                    }`}
                    onClick={() => void onFeedback(message.id, 'THUMBS_DOWN')}
                    title="Not helpful"
                  >
                    <ThumbsDown size={14} />
                  </button>
                </>
              )}

              {!isUser && Boolean(message.totalTokens) && (
                <span className="text-secondary opacity-75 small" style={{ fontSize: '0.68rem' }}>
                  {message.totalTokens} tokens
                </span>
              )}
            </div>

            <div className="d-flex align-items-center gap-2">
              {!isUser && (onRetry || onRegenerate) && (
                <button
                  type="button"
                  className="btn btn-sm btn-link p-0 text-secondary text-decoration-none d-flex align-items-center gap-1 small"
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
          className="rounded-circle bg-secondary-subtle text-secondary d-flex align-items-center justify-content-center flex-shrink-0"
          style={{ width: '34px', height: '34px' }}
        >
          <UserIcon size={18} />
        </div>
      )}
    </div>
  )
}
