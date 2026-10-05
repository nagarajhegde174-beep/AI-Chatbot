/**
 * The chat page: sidebar plus the selected conversation.
 *
 * <p>All state lives here rather than in a store, because there is exactly one screen that needs
 * it. A reducer would be the right call the moment a second surface needed the same state, and
 * introducing one now would be structure justified by a hypothetical.
 *
 * <p><strong>AI replies do not arrive in this phase.</strong> Sending stores the message and
 * creates a placeholder that stays `PENDING`. The thread renders that as "Waiting for a reply",
 * because that is the truth: a reply is expected and has not come. It is not an error, and this
 * component never fabricates content to fill the gap.
 */

import { useCallback, useEffect, useState } from 'react'

import * as chatApi from '../api/chat'
import type { Conversation, FeedbackRating, Message } from '../api/chat'
import { toApiError } from '../api/client'
import { AppShell } from '../components/AppShell'
import { Composer } from '../components/Composer'
import { ConversationList, type ConversationFilter } from '../components/ConversationList'
import { MessageThread } from '../components/MessageThread'
import { EmptyState, ErrorState, LoadingState, MessageSkeleton } from '../components/States'

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

  const send = useCallback(
    async (content: string): Promise<void> => {
      if (selectedId === null) {
        return
      }
      setSending(true)
      setActionError(null)
      try {
        if (editing !== null) {
          await chatApi.editMessage(editing.id, content)
        } else {
          await chatApi.sendMessage(selectedId, content)
        }
        setEditing(null)
        // Re-read rather than appending: an edit supersedes the original and a send adds two
        // rows, so the local list would be wrong in both cases.
        await loadMessages(selectedId)
        await loadConversations(search, filter)
      } catch (cause) {
        setActionError(toApiError(cause))
      } finally {
        setSending(false)
      }
    },
    [selectedId, editing, loadMessages, loadConversations, search, filter],
  )

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
            ) : messages.length === 0 ? (
              <EmptyState
                title="This conversation is empty"
                description="Send the first message below to begin."
              />
            ) : (
              <MessageThread
                messages={messages}
                onRegenerate={(message) => void regenerate(message)}
                onRate={(message, rating) => void rate(message, rating)}
                onEdit={(message) => setEditing({ id: message.id, content: message.content })}
              />
            )}
          </div>

          <Composer
            // Remounts when an edit begins or ends. That is how the textarea is prefilled
            // with the original text and cleared afterwards, without an effect trying to
            // synchronise state after the fact.
            key={editing?.id ?? 'new-message'}
            disabled={false}
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
