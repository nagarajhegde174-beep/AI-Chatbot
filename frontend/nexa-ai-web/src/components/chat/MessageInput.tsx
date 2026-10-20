import { useState, type KeyboardEvent, type FormEvent } from 'react'
import { Send, Square } from 'lucide-react'

interface MessageInputProps {
  readonly onSend: (content: string) => Promise<void>
  readonly disabled?: boolean
  readonly isStreaming?: boolean
  readonly onStop?: () => void
}

export function MessageInput({
  onSend,
  disabled = false,
  isStreaming = false,
  onStop,
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
    <form onSubmit={handleSubmit} className="p-3 bg-body border-top">
      <div className="input-group">
        <textarea
          className="form-control"
          placeholder={
            isStreaming
              ? 'Generating response... Click Stop to cancel'
              : 'Ask NexaAI anything... (Press Enter to send, Shift+Enter for new line)'
          }
          rows={2}
          value={content}
          disabled={disabled || sending || isStreaming}
          onChange={(e) => setContent(e.target.value)}
          onKeyDown={handleKeyDown}
        />
        {isStreaming ? (
          <button
            type="button"
            className="btn btn-outline-danger d-flex align-items-center justify-content-center px-4 gap-2"
            onClick={onStop}
            title="Stop generation"
          >
            <Square size={16} fill="currentColor" />
            <span className="small fw-semibold">Stop</span>
          </button>
        ) : (
          <button
            type="submit"
            className="btn btn-primary d-flex align-items-center justify-content-center px-4"
            disabled={!content.trim() || disabled || sending}
            title="Send message"
          >
            {sending ? (
              <div className="spinner-border spinner-border-sm" role="status" />
            ) : (
              <Send size={18} />
            )}
          </button>
        )}
      </div>
      <div className="d-flex justify-content-between align-items-center mt-1 px-1">
        <span className="text-secondary small" style={{ fontSize: '0.72rem' }}>
          Spring AI Model Router • Progressive SSE Streaming
        </span>
        <span className="text-secondary small" style={{ fontSize: '0.72rem' }}>
          {content.length > 0 ? `${content.length} characters` : ''}
        </span>
      </div>
    </form>
  )
}
