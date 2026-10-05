/**
 * The message composer.
 *
 * <p>A textarea that grows with its content and sends on Enter, because a chat surface that needs
 * a visible Send button for every message is a chat surface people stop using.
 *
 * <p>Enter sends, Shift+Enter inserts a newline. That convention is worth its own comment because
 * it is invisible to a new user: someone who presses Enter expecting a newline gets a sent message
 * instead. The hint below the composer is what makes it discoverable, and the textarea is two
 * rows so the affordance is visible.
 */

import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { SendHorizonal } from 'lucide-react'

export function Composer({
  disabled,
  sending,
  editing,
  onSend,
  onCancelEdit,
}: {
  readonly disabled: boolean
  readonly sending: boolean
  /** The message being edited, or null. Changes the header and the submit label. */
  readonly editing: { readonly id: string; readonly content: string } | null
  readonly onSend: (content: string) => void
  readonly onCancelEdit: () => void
}): React.JSX.Element {
  // Initialised once from the edit target. The parent remounts this component with a `key` that
  // changes when an edit begins or ends, so the prefill and the reset after submitting are both
  // handled by remounting rather than by two effects trying to synchronise state after the fact.
  const [text, setText] = useState(editing?.content ?? '')
  const textareaRef = useRef<HTMLTextAreaElement | null>(null)

  // Focused on mount so editing starts with the caret in the text. Mount-time rather than
  // effect-driven, because a remount is exactly the moment this matters.
  useEffect(() => {
    textareaRef.current?.focus()
  }, [])
  // Grow with the content up to a cap, then scroll. Without a cap the composer grows until it
  // pushes the whole thread off screen.
  useEffect(() => {
    const element = textareaRef.current
    if (element === null) {
      return
    }
    element.style.height = 'auto'
    element.style.height = `${Math.min(element.scrollHeight, 160)}px`
  }, [text])

  const trimmed = text.trim()
  const canSend = trimmed !== '' && !sending && !disabled

  const submit = (): void => {
    if (!canSend) {
      return
    }
    onSend(trimmed)
    setText('')
  }

  const onKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>): void => {
    if (event.key === 'Enter' && !event.shiftKey) {
      // preventDefault, because the default action of Enter in a textarea is a newline, which
      // would both send and insert a line break.
      event.preventDefault()
      submit()
    }
  }

  return (
    <form
      className="nexa-composer"
      onSubmit={(event) => {
        event.preventDefault()
        submit()
      }}
    >
      {editing !== null ? (
        <div className="nexa-composer-editing" role="status">
          <span>Editing your message</span>
          <button type="button" className="btn btn-sm btn-ghost" onClick={onCancelEdit}>
            Cancel
          </button>
        </div>
      ) : null}

      <div className="nexa-composer-row">
        <label htmlFor="composer-input" className="visually-hidden">
          {editing !== null ? 'Edit your message' : 'Message'}
        </label>
        <textarea
          id="composer-input"
          ref={textareaRef}
          className="nexa-composer-input"
          rows={2}
          value={text}
          placeholder={disabled ? 'Sign in to start a conversation' : 'Send a message…'}
          disabled={disabled || sending}
          onChange={(event) => setText(event.target.value)}
          onKeyDown={onKeyDown}
        />

        <button
          type="submit"
          className="btn btn-primary nexa-composer-send"
          disabled={!canSend}
          aria-label={editing !== null ? 'Resend edited message' : 'Send message'}
        >
          {sending ? (
            <span className="spinner-border spinner-border-sm" aria-hidden="true" />
          ) : (
            <SendHorizonal size={18} aria-hidden="true" />
          )}
        </button>
      </div>

      <p className="nexa-composer-hint mb-0">
        <kbd>Enter</kbd> to send, <kbd>Shift</kbd>+<kbd>Enter</kbd> for a new line.
      </p>
    </form>
  )
}
