/**
 * Memory routes, on top of the shared API client.
 *
 * <p>Every call goes through {@link request}, so credentials, CSRF and error mapping are handled
 * once. Components never call `fetch` directly.
 */

import { request } from './client'
import type { MemoryClear, MemoryRefresh, MemoryStatus } from './memory'

/** What a conversation remembers. Never throws for a conversation that has none. */
export async function getMemory(conversationId: string): Promise<MemoryStatus> {
  return request<MemoryStatus>(`/conversations/${encodeURIComponent(conversationId)}/memory`)
}

/**
 * Compresses older turns now rather than waiting for the budget to be exceeded.
 *
 * <p>Offered because "summarise when it overflows" is the right default and the wrong answer to
 * someone who has just been told their conversation lost its early turns.
 */
export async function refreshMemory(conversationId: string): Promise<MemoryRefresh> {
  return request<MemoryRefresh>(`/conversations/${encodeURIComponent(conversationId)}/memory/refresh`, {
    method: 'POST',
  })
}

/**
 * Forgets a conversation's memory.
 *
 * <p><strong>Messages are not deleted.</strong> "Forget what we discussed" and "delete the
 * conversation" are different requests, and the UI offers them in different places for that
 * reason. Deleting the conversation is a separate, separately confirmed action.
 */
export async function clearMemory(conversationId: string): Promise<MemoryClear> {
  return request<MemoryClear>(`/conversations/${encodeURIComponent(conversationId)}/memory`, {
    method: 'DELETE',
  })
}