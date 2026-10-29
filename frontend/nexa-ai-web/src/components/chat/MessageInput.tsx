import { useState, type KeyboardEvent, type FormEvent } from 'react'
import { ArrowUp, Square, FileText, Paperclip } from 'lucide-react'

interface MessageInputProps {
  readonly onSend: (content: string) => Promise<void>
  readonly disabled?: boolean
  readonly isStreaming?: boolean
  readonly onStop?: () => void
  readonly attachedDocCount?: number
  readonly onManageDocs?: () => void
}

export function MessageInput({
  onSend,
  disabled = false,
  isStreaming = false,
  onStop,
  attachedDocCount = 0,
  onManageDocs,
}: MessageInputProps) {
  const [content, setContent] = useState('')
  const [sending, setSending] = useState(false)

  const handleSend = async () => {
    if (!content.trim() || sending || disabled || isStreaming) return
    const text = content.trim()
    setContent('')
    setSending(true)
    try {
      await onSend(text)
    } finally {
      setSending(false)
    }
  }

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (isStreaming) {
      onStop?.()
    } else {
      void handleSend()
    }
  }

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      if (!isStreaming) {
        void handleSend()
      }
    }
  }

  return (
    <form onSubmit={handleSubmit} className="p-3 bg-elevated border-top border-subtle">
      <div className="mx-auto" style={{ maxWidth: '860px' }}>
        {/* Document Grounding Pill */}
        {attachedDocCount > 0 && (
          <div className="d-flex align-items-center gap-2 mb-2">
            <span
              className="nexa-badge nexa-badge-secondary font-monospace"
              style={{ cursor: onManageDocs ? 'pointer' : 'default', fontSize: '0.72rem' }}
              onClick={onManageDocs}
              title="Click to manage RAG grounded documents"
            >
              <FileText size={12} />
              <span>RAG: {attachedDocCount} document{attachedDocCount > 1 ? 's' : ''} active</span>
            </span>
          </div>
        )}

        <div className="position-relative nexa-card p-2 bg-surface">
          <textarea
            className="w-100 border-0 bg-transparent text-body p-1"
            style={{
              outline: 'none',
              resize: 'none',
              fontSize: '0.9375rem',
              lineHeight: 1.5,
              minHeight: '48px',
              maxHeight: '160px',
            }}
            placeholder={
              isStreaming
                ? 'Generating response... Click Stop to abort.'
                : 'Ask NexaAI anything... (Enter to send, Shift+Enter for new line)'
            }
            rows={2}
            value={content}
            disabled={disabled || sending || isStreaming}
            onChange={(e) => setContent(e.target.value)}
            onKeyDown={handleKeyDown}
          />

          <div className="d-flex align-items-center justify-content-between pt-1 border-top border-subtle mt-1">
            <div className="d-flex align-items-center gap-2">
              {onManageDocs && (
                <button
                  type="button"
                  className="btn btn-sm btn-ghost p-1 text-secondary"
                  onClick={onManageDocs}
                  title="Attach or inspect RAG documents"
                >
                  <Paperclip size={15} />
                  <span className="small d-none d-sm-inline" style={{ fontSize: '0.75rem' }}>Attach Docs</span>
                </button>
              )}
              <span className="text-muted small" style={{ fontSize: '0.72rem' }}>
                Spring AI SSE Streaming
              </span>
            </div>

            <div className="d-flex align-items-center gap-2">
              {isStreaming ? (
                <button
                  type="button"
                  className="nexa-btn-secondary py-1 px-3 text-danger border-danger small"
                  onClick={onStop}
                  title="Stop generation"
                >
                  <Square size={13} fill="currentColor" />
                  <span className="small fw-semibold">Stop</span>
                </button>
              ) : (
                <button
                  type="submit"
                  className="nexa-btn-primary p-1.5 rounded-circle"
                  style={{ width: '32px', height: '32px' }}
                  disabled={!content.trim() || disabled || sending}
                  title="Send message"
                >
                  {sending ? (
                    <div className="spinner-border spinner-border-sm" role="status" style={{ width: '14px', height: '14px' }} />
                  ) : (
                    <ArrowUp size={16} />
                  )}
                </button>
              )}
            </div>
          </div>
        </div>
      </div>
    </form>
  )
}
