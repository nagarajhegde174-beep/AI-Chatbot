/**
 * The chat page: sidebar plus the selected conversation.
 *
 * <p>All state lives here rather than in a store, because there is exactly one screen that needs
 * it. A reducer would be the right call the moment a second surface needed the same state, and
 * introducing one now would be structure justified by a hypothetical.
 *
 * ## How a reply arrives
 *
 * Sending opens a Server-Sent Events stream and the assistant's text is rendered as it arrives.
 * Three outcomes are distinguished, and the difference decides what the user sees next:
 *
 * - `done` — the generation completed. Text is rendered as Markdown.
 * - `stopped` — the user pressed Stop. The text so far is kept, which is the point of stopping
 *   rather than cancelling.
 * - `error` — the generation failed. The partial text is kept and a Retry button appears, offered
 *   only when the server said retrying could help. Offering "try again" for a rejected request
 *   trains people to press it for no reason.
 *
 * <p><strong>The stream is a second request, not a continuation of the send.</strong> The send
 * stores the message and the stream produces the reply. They are separate because the reply is
 * long, fails independently, and can be retried without re-sending the question.
 *
 * <p><strong>Nothing is fabricated.</strong> Before a token arrives there is no text, and the UI
 * says so rather than showing a placeholder shaped like an answer.
 */

import { useCallback, useEffect, useRef, useState } from 'react'
import { Square } from 'lucide-react'

import * as chatApi from '../api/chat'
import type { Conversation, FeedbackRating, Message } from '../api/chat'
import { toApiError } from '../api/client'
import { streamReply, type StreamResult } from '../api/stream'
import { AppShell } from '../components/AppShell'
import { Composer } from '../components/Composer'
import { ConversationList, type ConversationFilter } from '../components/ConversationList'
import { Markdown } from '../components/Markdown'
import { MessageThread } from '../components/MessageThread'
import { EmptyState, ErrorState, LoadingState, MessageSkeleton } from '../components/States'

/** A reply being produced right now. */
interface StreamingReply {
  readonly conversationId: string
  /** The text so far. Empty until the first token arrives, which is not the same as "done". */
  readonly text: string
  readonly stopped: boolean
  readonly errorMessage: string | null
  readonly retryable: boolean
  /** The prompt to re-send on retry. Held so a retry needs nothing from the DOM. */
  readonly prompt: string
}

export function ChatPage(): React.JSX.Element {
  const [conversations, setConversations] = useState<readonly Conversation[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [messages, setMessages] = useState<readonly Message[]>([])
  const [filter, setFilter] = useState<ConversationFilter>('active')
  const [search, setSearch] = useState('')

  const [listLoading, setListLoading] = useState(true)
  const [listError, setListError] = useState<unknown>(null)
  const [threadLoading, setThreadLoading] = useState(false)
  const [threadError, setThreadError] = useState<unknown>(null)
  const [actionError, setActionError] = useState<unknown>(null)
  const [sending, setSending] = useState(false)
  const [editing, setEditing] = useState<{ readonly id: string; readonly content: string } | null>(null)

  const [streaming, setStreaming] = useState<StreamingReply | null>(null)

  /**
   * The in-flight stream's abort controller.
   *
   * <p>A ref, not state: it is mutated, never rendered, and putting it in state would make every
   * `abort()` call a re-render. Held in a ref so the Stop button and the unmount cleanup can both
   * reach the same one.
   */
  const abortRef = useRef<AbortController | null>(null)

  /** Aborts any in-flight stream. Called on unmount and when the selection changes. */
  const stopStreaming = useCallback((): void => {
    abortRef.current?.abort()
    abortRef.current = null
  }, [])

  // An unmount mid-stream would otherwise leave a reader pulling into a component that no longer
  // exists, and the fetch alive until the server noticed.
  useEffect(() => stopStreaming, [stopStreaming])

  // -------------------------------------------------------------------
  // Loading the sidebar
  // -------------------------------------------------------------------

  const loadConversations = useCallback(
    async (query: string, which: ConversationFilter): Promise<void> => {
      setListLoading(true)
      setListError(null)
      try {
        // The search endpoint covers both title and message text; the listing endpoint takes a
        // title filter. Using search for both keeps one code path and one set of semantics.
        const page =
          query.trim() === ''
            ? which === 'active'
              ? await chatApi.listConversations()
              : await chatApi.listArchivedConversations()
            : await chatApi.searchConversations(query)

        setConversations(page.content)
      } catch (cause) {
        setListError(toApiError(cause))
      } finally {
        setListLoading(false)
      }
    },
    [],
  )

  useEffect(() => {
    void loadConversations(search, filter)
  }, [loadConversations, search, filter])

  // -------------------------------------------------------------------
  // Loading a thread
  // -------------------------------------------------------------------

  const loadMessages = useCallback(async (conversationId: string): Promise<void> => {
    setThreadLoading(true)
    setThreadError(null)
    try {
      setMessages(await chatApi.listMessages(conversationId))
    } catch (cause) {
      setThreadError(toApiError(cause))
      setMessages([])
    } finally {
      setThreadLoading(false)
    }
  }, [])

  useEffect(() => {
    if (selectedId === null) {
      setMessages([])
      return
    }
    void loadMessages(selectedId)
  }, [selectedId, loadMessages])

  // -------------------------------------------------------------------
  // Actions
  // -------------------------------------------------------------------

  const startConversation = useCallback(async (): Promise<void> => {
    setActionError(null)
    try {
      const created = await chatApi.createConversation()
      // Re-read the list rather than prepending optimistically: a derived title arrives with the
      // first message, so the optimistic entry would be stale within seconds.
      await loadConversations(search, filter)
      setSelectedId(created.id)
    } catch (cause) {
      setActionError(toApiError(cause))
    }
  }, [loadConversations, search, filter])

  // The shell's "New chat" button dispatches this rather than the sidebar knowing about it.
  useEffect(() => {
    const handler = (): void => {
      void startConversation()
    }
    window.addEventListener('nexa:new-conversation', handler)
    return () => {
      window.removeEventListener('nexa:new-conversation', handler)
    }
  }, [startConversation])

  /**
 * Opens a stream for a prompt and renders it as it arrives.
 *
 * <p>Separate from `send` so that Retry re-runs exactly this and nothing else: the message is
 * already stored, so retrying must not send it again and create a duplicate question.
 */
  const runStream = useCallback(
    async (conversationId: string, prompt: string): Promise<void> => {
      // Only one generation at a time. A second would interleave two answers into one bubble.
      stopStreaming()

      const controller = new AbortController()
      abortRef.current = controller

      setStreaming({
        conversationId,
        text: '',
        stopped: false,
        errorMessage: null,
        retryable: false,
        prompt,
      })

      try {
        const result: StreamResult = await streamReply(
          conversationId,
          prompt,
          controller,
          {
            onToken: (text) => {
              setStreaming((current) =>
                // Guarded on the conversation id: switching conversations mid-stream must not
                // paint the old answer into the new thread.
                current !== null && current.conversationId === conversationId
                  ? { ...current, text }
                  : current,
              )
            },
            onDone: () => {
              // The finished text is re-read from the server below rather than trusted from
              // here. What is stored is what the next page load will show; a locally-held copy
              // would be a second, divergent source of truth for the same message.
            },
          },
        )

        if (result.kind === 'error') {
          setStreaming((current) =>
            current === null
              ? current
              : {
                  ...current,
                  stopped: false,
                  errorMessage: result.message,
                  retryable: result.retryable,
                },
          )
        }

        await loadMessages(conversationId)
      } catch (cause) {
        setActionError(toApiError(cause))
      } finally {
        abortRef.current = null
      }
    },
    [stopStreaming, loadMessages],
  )

  const send = useCallback(
    async (content: string): Promise<void> => {
      if (selectedId === null) {
        return
      }
      setSending(true)
      setActionError(null)
      const conversationId = selectedId
      // A local flag, not `actionError`. State set in the catch has not propagated by the time
      // the code below runs, so reading it here would be a stale closure that starts a stream
      // for a message that was never saved.
      let stored = false
      try {
        if (editing !== null) {
          await chatApi.editMessage(editing.id, content)
        } else {
          await chatApi.sendMessage(conversationId, content)
        }
        setEditing(null)
        // Re-read rather than appending: an edit supersedes the original and a send adds two
        // rows, so the local list would be wrong in both cases.
        await loadMessages(conversationId)
        await loadConversations(search, filter)
        stored = true
      } catch (cause) {
        setActionError(toApiError(cause))
      } finally {
        setSending(false)
      }

      // The stream starts only after the message is stored, and only when there was no error:
      // generating a reply to a message that was never saved would leave the answer with no
      // question attached to it.
      if (stored) {
        await runStream(conversationId, content)
      }
    },
    [selectedId, editing, loadMessages, loadConversations, search, filter, runStream],
  )

  /**
   * Retries a failed generation.
   *
   * <p>Re-runs the stream only. The question is already stored, and sending it again would put
   * two identical questions in the thread with one answer between them.
   */
  const retryStream = useCallback((): void => {
    const current = streaming
    if (current === null) {
      return
    }
    void runStream(current.conversationId, current.prompt)
  }, [streaming, runStream])

  /** The user pressed Stop. Whatever arrived is kept — that is what stopping is for. */
  const stopStream = useCallback((): void => {
    setStreaming((current) => (current === null ? current : { ...current, stopped: true }))
    stopStreaming()
  }, [stopStreaming])

  const regenerate = useCallback(
    async (message: Message): Promise<void> => {
      if (selectedId === null) {
        return
      }
      setActionError(null)
      try {
        await chatApi.regenerate(message.id)
        await loadMessages(selectedId)
      } catch (cause) {
        setActionError(toApiError(cause))
      }
    },
    [selectedId, loadMessages],
  )

  const rate = useCallback(
    async (message: Message, rating: FeedbackRating): Promise<void> => {
      if (selectedId === null) {
        return
      }
      // Optimistic: a thumbs-up should feel instant, and the failure path re-reads the thread so
      // the state cannot silently disagree with the server.
      setMessages((current) =>
        current.map((m) =>
          m.id === message.id
            ? {
                ...m,
                feedback: {
                  id: m.feedback?.id ?? 'pending',
                  rating,
                  comment: null,
                  updatedAt: new Date().toISOString(),
                },
              }
            : m,
        ),
      )

      try {
        await chatApi.rateMessage(message.id, rating)
      } catch (cause) {
        setActionError(toApiError(cause))
        await loadMessages(selectedId)
      }
    },
    [selectedId, loadMessages],
  )

  const rename = useCallback(
    async (id: string, title: string): Promise<void> => {
      setActionError(null)
      try {
        await chatApi.renameConversation(id, title)
        await loadConversations(search, filter)
      } catch (cause) {
        setActionError(toApiError(cause))
      }
    },
    [loadConversations, search, filter],
  )

  const toggleArchive = useCallback(
    async (conversation: Conversation): Promise<void> => {
      setActionError(null)
      try {
        if (conversation.status === 'ARCHIVED') {
          await chatApi.restoreConversation(conversation.id)
        } else {
          await chatApi.archiveConversation(conversation.id)
        }
        await loadConversations(search, filter)
      } catch (cause) {
        setActionError(toApiError(cause))
      }
    },
    [loadConversations, search, filter],
  )

  const remove = useCallback(
    async (conversation: Conversation): Promise<void> => {
      setActionError(null)
      try {
        await chatApi.deleteConversation(conversation.id)
        if (selectedId === conversation.id) {
          setSelectedId(null)
        }
        await loadConversations(search, filter)
      } catch (cause) {
        setActionError(toApiError(cause))
      }
    },
    [loadConversations, search, filter, selectedId],
  )

  // -------------------------------------------------------------------
  // Render
  // -------------------------------------------------------------------

  const sidebar = listError !== null ? (
    <ErrorState error={listError} onRetry={() => void loadConversations(search, filter)} />
  ) : listLoading ? (
    <LoadingState label="Loading conversations" />
  ) : (
    <ConversationList
      conversations={conversations}
      selectedId={selectedId}
      filter={filter}
      onFilterChange={setFilter}
      onSelect={setSelectedId}
      onSearchChange={setSearch}
      onRename={(id, title) => void rename(id, title)}
      onArchiveToggle={(conversation) => void toggleArchive(conversation)}
      onDelete={(conversation) => void remove(conversation)}
    />
  )

  const visibleStream =
    streaming !== null && streaming.conversationId === selectedId ? streaming : null

  return (
    <AppShell sidebar={sidebar}>
      {actionError !== null ? (
        <div className="px-3 pt-3">
          <ErrorState
            error={actionError}
            title="That action did not complete"
            onRetry={() => setActionError(null)}
          />
        </div>
      ) : null}

      {selectedId === null ? (
        <div className="nexa-chat-empty">
          <EmptyState
            title="No conversation selected"
            description="Start a new chat, or pick one from the list to pick up where you left off."
            action={
              <button type="button" className="btn btn-primary" onClick={() => void startConversation()}>
                New chat
              </button>
            }
          />
        </div>
      ) : threadError !== null ? (
        <div className="p-3">
          <ErrorState
            error={threadError}
            title="Could not load this conversation"
            onRetry={() => void loadMessages(selectedId)}
          />
        </div>
      ) : (
        <>
          <div className="nexa-chat-thread">
            {threadLoading ? (
              <MessageSkeleton rows={4} />
            ) : messages.length === 0 && visibleStream === null ? (
              <EmptyState
                title="This conversation is empty"
                description="Send the first message below to begin."
              />
            ) : (
              <>
                <MessageThread
                  messages={messages}
                  onRegenerate={(message) => void regenerate(message)}
                  onRate={(message, rating) => void rate(message, rating)}
                  onEdit={(message) => setEditing({ id: message.id, content: message.content })}
                />
                {visibleStream === null ? null : (
                  <StreamingBubble
                    reply={visibleStream}
                    onStop={stopStream}
                    onRetry={retryStream}
                  />
                )}
              </>
            )}
          </div>

          <Composer
            // Remounts when an edit begins or ends. That is how the textarea is prefilled
            // with the original text and cleared afterwards, without an effect trying to
            // synchronise state after the fact.
            key={editing?.id ?? 'new-message'}
            // Disabled during a generation so a second question cannot be sent mid-answer, which
            // would interleave two replies into one thread.
            disabled={visibleStream !== null}
            sending={sending}
            editing={editing}
            onSend={(content) => void send(content)}
            onCancelEdit={() => setEditing(null)}
          />
        </>
      )}
    </AppShell>
  )
}

/**
 * The assistant's reply while it is being produced.
 *
 * <p>Three states, and the difference is the whole design:
 *
 * - no text yet: a "thinking" indicator, because there is genuinely nothing to show and a blank
 *   bubble reads as a rendering failure
 * - text arriving: plain text with a blinking cursor. Deliberately NOT Markdown — see
 *   {@link Markdown}
 * - finished or stopped: the text is rendered, and Stop is gone
 *
 * <p>The Retry button appears only when the server said retrying could help.
 */
function StreamingBubble({
  reply,
  onStop,
  onRetry,
}: {
  readonly reply: StreamingReply
  readonly onStop: () => void
  readonly onRetry: () => void
}) {
  const inProgress = reply.text === '' && reply.errorMessage === null

  return (
    <div className="message message--assistant">
      <div className="message__bubble">
        {inProgress ? (
          <div className="stream-thinking" aria-live="polite">
            <span className="stream-thinking__dot" />
            <span className="stream-thinking__dot" />
            <span className="stream-thinking__dot" />
            <span className="visually-hidden">Waiting for the first token</span>
          </div>
        ) : (
          <>
            {reply.text === '' ? null : <Markdown content={reply.text} streaming />}
            {reply.stopped ? (
              <p className="stream-note">Stopped. The text above is what arrived.</p>
            ) : null}
            {reply.errorMessage !== null ? (
              <div className="stream-error" role="alert">
                <span>{reply.errorMessage}</span>
                {reply.retryable ? (
                  <button type="button" className="btn btn-sm" onClick={onRetry}>
                    Retry
                  </button>
                ) : null}
              </div>
            ) : null}
          </>
        )}

        {reply.errorMessage === null && !reply.stopped ? (
          <div className="stream-actions">
            <button type="button" className="btn btn-sm btn-ghost" onClick={onStop}>
              <Square size={12} aria-hidden /> Stop
            </button>
          </div>
        ) : null}
      </div>
    </div>
  )
}
