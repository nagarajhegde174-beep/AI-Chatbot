/**
 * Conversation memory, as the API exposes it.
 *
 * <p><strong>No content, ever.</strong> A summary is a paraphrase of the user's own words, and the
 * status route deliberately does not return it. These types mirror that: there is no field to
 * accidentally render, so the frontend cannot become a place a summary leaks from.
 */

/** What a conversation remembers. */
export interface MemoryStatus {
  /** Whether a compressed summary exists. */
  readonly hasSummary: boolean
  /** How many earlier turns that summary covers. */
  readonly summarisedTurns: number
  /** What the summary is judged to cost, in approximate tokens. */
  readonly summaryEstimatedTokens: number
  /** When it was last rebuilt, or null if never. */
  readonly summarisedAt: string | null
  /** The budget every generation's context is measured against. */
  readonly contextTokenBudget: number
  /** Whether compression runs at all, for this deployment. */
  readonly summarisationEnabled: boolean
}

/** The outcome of a refresh. */
export interface MemoryRefresh {
  readonly conversationId: string
  readonly compressedTurns: number
  readonly memory: MemoryStatus
}

/** The outcome of clearing. */
export interface MemoryClear {
  readonly conversationId: string
  /** False when there was nothing remembered. A clear that reports success on an empty
   *  conversation is technically true and tells the user nothing. */
  readonly hadMemory: boolean
  readonly memory: MemoryStatus
}

/**
 * What the UI should say about a conversation's memory.
 *
 * <p>Derived rather than shown raw, because the useful question for a user is not "how many
 * tokens is the summary" but "does this conversation still remember its beginning".
 */
export function describeMemory(status: MemoryStatus | null): string | null {
  if (status === null || !status.hasSummary) {
    return null
  }
  const turns = status.summarisedTurns
  return `Remembering the first ${turns} message${turns === 1 ? '' : 's'}`
}

/** Whether "Forget" should be offered at all. */
export function canClear(status: MemoryStatus | null): boolean {
  return status !== null && status.hasSummary
}