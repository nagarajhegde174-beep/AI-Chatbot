/**
 * The memory strip: what this conversation remembers, and the controls to change it.
 *
 * ## Why this is visible at all
 *
 * A conversation that has quietly stopped remembering its own beginning is the failure mode of
 * every memory feature, and it is invisible from the inside: the assistant simply answers as
 * though it had never been told. Showing the coverage — and the fact that older turns have been
 * compressed — is what turns a silent degradation into something the user can see and act on.
 *
 * ## Why "Forget" and "Delete conversation" are separate
 *
 * They are separate requests with different consequences. Forgetting drops only the compressed
 * form and can be rebuilt from messages that still exist. Deleting removes the user's own words
 * permanently. Putting them next to each other with similar labels is how one becomes the other by
 * accident.
 */

import { useState } from 'react'
import { Brain, Loader2, RefreshCw, Trash2 } from 'lucide-react'

import * as memoryApi from '../api/memoryApi'
import { canClear, describeMemory, type MemoryStatus } from '../api/memory'
import { toApiError } from '../api/client'

export interface MemoryStripProps {
  readonly conversationId: string
  readonly status: MemoryStatus | null
  readonly onChanged: (status: MemoryStatus) => void
}

export function MemoryStrip({ conversationId, status, onChanged }: MemoryStripProps) {
  const [busy, setBusy] = useState<'refresh' | 'clear' | null>(null)
  const [error, setError] = useState<string | null>(null)

  const run = async (
    which: 'refresh' | 'clear',
    action: () => Promise<{ memory: MemoryStatus }>,
  ): Promise<void> => {
    setBusy(which)
    setError(null)
    try {
      const result = await action()
      onChanged(result.memory)
    } catch (cause) {
      setError(toApiError(cause).displayMessage)
    } finally {
      setBusy(null)
    }
  }

  const summary = describeMemory(status)

  // Nothing is remembered and compression is off: there is nothing to explain, so the strip is
  // not rendered rather than rendered empty.
  if (status !== null && !status.hasSummary && !status.summarisationEnabled) {
    return null
  }

  return (
    <div className="memory-strip">
      <div className="memory-strip__info">
        <Brain size={14} aria-hidden />
        {summary === null ? (
          <span className="memory-strip__idle">
            No memory yet — context is limited to the {status?.contextTokenBudget ?? 0} most recent
            tokens of this conversation.
          </span>
        ) : (
          <span className="memory-strip__summary">{summary}.</span>
        )}
      </div>

      <div className="memory-strip__actions">
        <button
          type="button"
          className="btn btn-sm btn-ghost"
          disabled={busy !== null}
          onClick={() => void run('refresh', () => memoryApi.refreshMemory(conversationId))}
          title="Compress older messages now, instead of waiting until the conversation outgrows its budget."
        >
          {busy === 'refresh' ? (
            <Loader2 size={13} className="spin" aria-hidden />
          ) : (
            <RefreshCw size={13} aria-hidden />
          )}
          Compress
        </button>

        {/* Offered only when there is something to forget. A button that clears nothing tells
            the user their conversation is already clear, which it may not be. */}
        {canClear(status) ? (
          <button
            type="button"
            className="btn btn-sm btn-ghost"
            disabled={busy !== null}
            onClick={() => void run('clear', () => memoryApi.clearMemory(conversationId))}
            title="Forgets what was compressed about this conversation. Your messages are kept."
          >
            {busy === 'clear' ? (
              <Loader2 size={13} className="spin" aria-hidden />
            ) : (
              <Trash2 size={13} aria-hidden />
            )}
            Forget
          </button>
        ) : null}
      </div>

      {error === null ? null : (
        <p className="memory-strip__error" role="alert">
          {error}
        </p>
      )}
    </div>
  )
}