/**
 * Typed bindings for the Chat Service API.
 *
 * <p>The types here are the **contract as implemented**, transcribed from
 * `docs/SERVICE_CONTRACTS.md` §7. They are not `any` and not inferred from a response, because a
 * streaming chat UI is exactly where loose typing lets a real bug through unnoticed: a nullable
 * `lastMessageAt`, a sparse page, an enum that arrives as an unexpected string.
 *
 * <p>`readonly` throughout. These are responses; nothing in the UI should mutate one, and the
 * compiler should say so.
 */

import { request } from './client'

/** Mirrors the backend enum. Uppercase, because that is what the wire format is. */
export type ConversationStatus = 'ACTIVE' | 'ARCHIVED'
export type MessageRole = 'USER' | 'ASSISTANT' | 'SYSTEM'
export type MessageStatus = 'COMPLETE' | 'PENDING' | 'FAILED'
export type FeedbackRating = 'UP' | 'DOWN'

export interface Conversation {
  readonly id: string
  readonly title: string
  readonly model: string | null
  readonly status: ConversationStatus
  readonly messageCount: number
  readonly lastMessageAt: string | null
  readonly createdAt: string
  readonly updatedAt: string
}

export interface Feedback {
  readonly id: string
  readonly rating: FeedbackRating
  readonly comment: string | null
  readonly updatedAt: string
}

export interface Message {
  readonly id: string
  readonly conversationId: string
  readonly sequenceNo: number
  readonly role: MessageRole
  readonly content: string
  readonly status: MessageStatus
  readonly model: string | null
  readonly inputTokens: number | null
  readonly outputTokens: number | null
  readonly edited: boolean
  readonly regenerated: boolean
  readonly failureReason: string | null
  readonly createdAt: string
  readonly feedback: Feedback | null
}

/** What a send returns: the stored user message and the assistant placeholder for it. */
export interface SendResult {
  readonly userMessage: Message
  readonly assistantMessage: Message
}

export interface Page<T> {
  readonly page: number
  readonly size: number
  readonly totalItems: number
  readonly totalPages: number
  readonly content: readonly T[]
}

// ---------------------------------------------------------------------
// Conversations
// ---------------------------------------------------------------------

/**
 * Creates a conversation.
 *
 * <p>Both arguments are optional: an empty body creates an untitled conversation, which is what
 * the "New chat" button wants. It should not have to ask a question before it can create
 * anything.
 */
export async function createConversation(options?: {
  readonly title?: string
  readonly model?: string
}): Promise<Conversation> {
  return request<Conversation>('/conversations', {
    method: 'POST',
    body: options ?? {},
  })
}

/** The active conversations: the sidebar listing. Excludes archived. */
export async function listConversations(
  options: { readonly search?: string; readonly page?: number; readonly size?: number } = {},
): Promise<Page<Conversation>> {
  const query = new URLSearchParams()
  if (options.search !== undefined && options.search !== '') {
    query.set('search', options.search)
  }
  if (options.page !== undefined) {
    query.set('page', String(options.page))
  }
  if (options.size !== undefined) {
    query.set('size', String(options.size))
  }
  const suffix = query.size > 0 ? `?${query.toString()}` : ''
  return request<Page<Conversation>>(`/conversations${suffix}`)
}

export async function listArchivedConversations(
  options: { readonly page?: number; readonly size?: number } = {},
): Promise<Page<Conversation>> {
  const query = new URLSearchParams()
  if (options.page !== undefined) {
    query.set('page', String(options.page))
  }
  if (options.size !== undefined) {
    query.set('size', String(options.size))
  }
  const suffix = query.size > 0 ? `?${query.toString()}` : ''
  return request<Page<Conversation>>(`/conversations/archived${suffix}`)
}

export async function getConversation(id: string): Promise<Conversation> {
  return request<Conversation>(`/conversations/${id}`)
}

/** Searches by title or message text. */
export async function searchConversations(
  query: string,
  options: { readonly page?: number; readonly size?: number } = {},
): Promise<Page<Conversation>> {
  const params = new URLSearchParams({ q: query })
  if (options.page !== undefined) {
    params.set('page', String(options.page))
  }
  if (options.size !== undefined) {
    params.set('size', String(options.size))
  }
  return request<Page<Conversation>>(`/conversations/search?${params.toString()}`)
}

export async function renameConversation(id: string, title: string): Promise<Conversation> {
  return request<Conversation>(`/conversations/${id}`, {
    method: 'PATCH',
    body: { title },
  })
}

export async function archiveConversation(id: string): Promise<Conversation> {
  return request<Conversation>(`/conversations/${id}/archive`, { method: 'POST' })
}

export async function restoreConversation(id: string): Promise<Conversation> {
  return request<Conversation>(`/conversations/${id}/restore`, { method: 'POST' })
}

export async function deleteConversation(id: string): Promise<void> {
  await request<void>(`/conversations/${id}`, { method: 'DELETE' })
}

// ---------------------------------------------------------------------
// Messages
// ---------------------------------------------------------------------

/** The conversation's history, oldest first. Superseded messages are excluded. */
export async function listMessages(conversationId: string): Promise<readonly Message[]> {
  return request<readonly Message[]>(`/conversations/${conversationId}/messages`)
}

/**
 * Sends a message.
 *
 * <p>Returns both the stored user message and the assistant placeholder. The placeholder arrives
 * with status `PENDING`: AI generation is not wired yet, so a reply is expected and has not come.
 * The UI renders that honestly rather than as an error.
 */
export async function sendMessage(
  conversationId: string,
  content: string,
): Promise<SendResult> {
  return request<SendResult>(`/conversations/${conversationId}/messages`, {
    method: 'POST',
    body: { content },
  })
}

export async function regenerate(messageId: string): Promise<Message> {
  return request<Message>(`/messages/${messageId}/regenerate`, { method: 'POST' })
}

export async function editMessage(messageId: string, content: string): Promise<SendResult> {
  return request<SendResult>(`/messages/${messageId}`, {
    method: 'PUT',
    body: { content },
  })
}

export async function rateMessage(
  messageId: string,
  rating: FeedbackRating,
  comment?: string,
): Promise<Feedback> {
  return request<Feedback>(`/messages/${messageId}/feedback`, {
    method: 'PUT',
    body: { rating, comment: comment ?? null },
  })
}

export async function clearRating(messageId: string): Promise<void> {
  await request<void>(`/messages/${messageId}/feedback`, { method: 'DELETE' })
}

export async function deleteMessage(messageId: string): Promise<void> {
  await request<void>(`/messages/${messageId}`, { method: 'DELETE' })
}

// ---------------------------------------------------------------------
// Export
// ---------------------------------------------------------------------

/** The Markdown export URL, for a download link rather than a fetch. */
export function exportUrl(conversationId: string): string {
  return `/api/v1/conversations/${conversationId}/export`
}

/**
 * Narrows an unknown value to a conversation status.
 *
 * <p>An enum that arrives as an unexpected string must not silently become `ACTIVE`; treating it
 * as unknown keeps the UI from showing a wrong state.
 */
export function isConversationStatus(value: string): value is ConversationStatus {
  return value === 'ACTIVE' || value === 'ARCHIVED'
}
