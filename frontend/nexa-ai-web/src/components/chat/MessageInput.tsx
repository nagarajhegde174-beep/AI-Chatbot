import { useState, type KeyboardEvent, type FormEvent } from 'react'
import { Send } from 'lucide-react'

interface MessageInputProps {
  readonly onSend: (content: string) => Promise<void>
  readonly disabled?: boolean
}

export function MessageInput({ onSend, disabled = false }: MessageInputProps) {
  const [content, setContent] = useState('')
  const [sending, setSending] = useState(false)

  const handleSend = async () => {
    if (!content.trim() || sending || disabled) return
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
    void handleSend()
  }

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      void handleSend()
    }
  }

  return (
    <form onSubmit={handleSubmit} className="p-3 bg-body border-top">
      <div className="input-group">
        <textarea
          className="form-control"
          placeholder="Ask NexaAI anything... (Press Enter to send, Shift+Enter for new line)"
          rows={2}
          value={content}
          disabled={disabled || sending}
          onChange={(e) => setContent(e.target.value)}
          onKeyDown={handleKeyDown}
        />
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
      </div>
      <div className="d-flex justify-content-between align-items-center mt-1 px-1">
        <span className="text-secondary small" style={{ fontSize: '0.72rem' }}>
          Phase 3 Chat Foundation (AI placeholder boundary active)
        </span>
        <span className="text-secondary small" style={{ fontSize: '0.72rem' }}>
          {content.length > 0 ? `${content.length} characters` : ''}
        </span>
      </div>
    </form>
  )
}
