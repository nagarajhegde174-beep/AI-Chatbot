/**
 * Server-Sent Events over `fetch`.
 *
 * ## Why not `EventSource`
 *
 * `EventSource` is the obvious tool and cannot be used here: it is GET-only, and sending a chat
 * message requires a POST with a body. Every workaround for that — base64 the payload into a
 * query parameter, or open a second GET once the POST has created the message — puts a
 * credential or a prompt where it is easy to log by accident. `fetch` does the real thing.
 *
 * ## Why the parser is here rather than in a library
 *
 * The framing is a dozen lines and the contract is ours ({@code docs/SERVICE_CONTRACTS.md} §8:
 * `meta`, then `token`s, then `done` or `error`). A library would hide the one detail that
 * matters here: frames arrive **split across network chunks**, so a parser that assumes one
 * chunk equals one line silently drops or duplicates text depending on timing. That failure looks
 * like an intermittent rendering bug and is close to impossible to reproduce.
 *
 * ## No provider credentials here
 *
 * The only credential this module ever sends is the HTTP-only cookie the server set. There is no
 * provider key in this file, in the bundle, or reachable from it — the browser cannot call AI
 * Service at all, which is why Chat Service relays the stream.
 */

import { ApiError, toApiError } from './client'

/** One decoded SSE frame. */
export interface StreamFrame {
  readonly event: string
  readonly data: string
}

/** The terminal outcome of a stream. */
export type StreamResult =
  | { readonly kind: 'done'; readonly text: string; readonly model: string | null }
  | { readonly kind: 'error'; readonly code: string; readonly message: string; readonly retryable: boolean }
  | { readonly kind: 'stopped'; readonly text: string }

/** How a stream reports progress to the UI. */
export interface StreamCallbacks {
  /** Called for each token chunk, with the full text accumulated so far. */
  readonly onToken: (text: string) => void
  /** Called once, when the stream has produced its terminal frame. */
  readonly onDone: (result: StreamResult) => void
}

const BASE = '/api/v1'
const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'

/**
 * Reads the CSRF cookie.
 *
 * <p>Duplicated from `client.ts` rather than exported from there. Small, and it keeps this
 * module's dependency on the rest of the app to exactly one function.
 */
function readCookie(name: string): string | null {
  const prefix = `${name}=`
  for (const part of document.cookie.split(';')) {
    const trimmed = part.trim()
    if (trimmed.startsWith(prefix)) {
      return decodeURIComponent(trimmed.slice(prefix.length))
    }
  }
  return null
}

/**
 * Splits a growing buffer into complete lines, returning the remainder.
 *
 * <p>The reason this exists rather than a one-liner: SSE frames end at a blank line, and network
 * chunks end wherever the packet boundary falls. The two do not agree. Holding the tail back
 * until the next chunk is what makes the stream correct under any chunking.
 */
function drainLines(buffer: string): { lines: string[]; rest: string } {
  const parts = buffer.split('\n')
  // The last element is an incomplete line unless the buffer ended with a newline, in which case
  // it is an empty string and there is nothing to hold back.
  const rest = parts.pop() ?? ''
  return { lines: parts.map((line) => line.replace(/\r$/, '')), rest }
}

/**
 * Accumulates raw SSE text into frames.
 *
 * <p>Frame boundaries are the blank line. A frame with no {@code data:} field is a keep-alive
 * comment and is discarded: surfacing it as an event with an empty body makes the UI flash.
 */
export class SseParser {
  private buffer = ''

  push(chunk: string): StreamFrame[] {
    this.buffer += chunk
    const { lines, rest } = drainLines(this.buffer)
    this.buffer = rest

    const frames: StreamFrame[] = []
    let event = 'message'
    const data: string[] = []
    let sawField = false

    const flush = (): void => {
      if (sawField && data.length > 0) {
        frames.push({ event, data: data.join('\n') })
      }
      event = 'message'
      data.length = 0
      sawField = false
    }

    for (const line of lines) {
      if (line === '') {
        flush()
        continue
      }
      if (line.startsWith(':')) {
        // Comment / keep-alive.
        continue
      }
      const colon = line.indexOf(':')
      const field = colon === -1 ? line : line.slice(0, colon)
      let value = colon === -1 ? '' : line.slice(colon + 1)
      if (value.startsWith(' ')) {
        value = value.slice(1)
      }

      if (field === 'event') {
        event = value
        sawField = true
      } else if (field === 'data') {
        data.push(value)
        sawField = true
      }
      // id: and retry: are ignored. Nothing in the contract uses them, and honouring a
      // Last-Event-ID the server never sent would imply a resume capability that does not exist.
    }

    return frames
  }

  /**
   * Emits a final frame that arrived without a trailing blank line.
   *
   * <p>A stream closed by the server rather than by a blank line is the normal shape of a
   * truncated response, and dropping the last partial frame would lose the user's answer.
   */
  flush(): StreamFrame[] {
    const frames = this.push('\n\n')
    return frames
  }
}

/** Parses an SSE `meta` payload. */
function parseMeta(data: string): { model: string | null; requestId: string | null } {
  try {
    const parsed = JSON.parse(data) as { model?: unknown; requestId?: unknown }
    return {
      model: typeof parsed.model === 'string' ? parsed.model : null,
      requestId: typeof parsed.requestId === 'string' ? parsed.requestId : null,
    }
  } catch {
    // A malformed meta frame must not abandon a stream whose tokens are still coming.
    return { model: null, requestId: null }
  }
}

/** Parses an SSE `token` payload. Returns null when the frame is not a token. */
function parseToken(data: string): string | null {
  try {
    const parsed = JSON.parse(data) as { text?: unknown }
    return typeof parsed.text === 'string' ? parsed.text : null
  } catch {
    // A dropped token is better than a whole failed generation; the next token usually parses.
    return null
  }
}

/** Parses an SSE `error` payload. */
function parseError(data: string): { code: string; message: string; retryable: boolean } {
  try {
    const parsed = JSON.parse(data) as { code?: unknown; message?: unknown; retryable?: unknown }
    return {
      code: typeof parsed.code === 'string' ? parsed.code : 'GENERATION_FAILED',
      message:
        typeof parsed.message === 'string' && parsed.message.length > 0
          ? parsed.message
          : 'The message could not be generated.',
      retryable: parsed.retryable !== false,
    }
  } catch {
    return {
      code: 'GENERATION_FAILED',
      message: 'The message could not be generated.',
      retryable: true,
    }
  }
}

/**
 * Streams one reply.
 *
 * <p>Resolves with the terminal result rather than throwing: a failed generation is an outcome the
 * UI renders, not an exception it catches. Only a pre-stream failure (auth, bad request, network)
 * throws, because nothing has been streamed yet and the UI has nothing to show.
 *
 * @param controller passed in so the caller can abort it — this is the Stop button
 */
export async function streamReply(
  conversationId: string,
  content: string,
  controller: AbortController,
  callbacks: StreamCallbacks,
  model?: string,
): Promise<StreamResult> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'text/event-stream',
  }
  const csrf = readCookie(CSRF_COOKIE)
  if (csrf !== null) {
    headers[CSRF_HEADER] = csrf
  }

  const response = await fetch(
    `${BASE}/conversations/${encodeURIComponent(conversationId)}/messages/stream`,
    {
      method: 'POST',
      headers,
      credentials: 'include',
      signal: controller.signal,
      body: JSON.stringify(model ? { content, model } : { content }),
    },
  )

  if (!response.ok) {
    // Nothing has been streamed yet, so a real HTTP status is available and useful.
    const text = await response.text()
    throw new ApiError(parseStreamError(response.status, text))
  }

  if (response.body === null) {
    throw new ApiError({
      status: 0,
      code: 'STREAM_UNSUPPORTED',
      message: 'This browser cannot read a streamed response.',
      traceId: null,
    })
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  const parser = new SseParser()

  let accumulated = ''
  let modelName: string | null = null
  let stopped = false

  const handle = (frames: readonly StreamFrame[]): StreamResult | null => {
    for (const frame of frames) {
      switch (frame.event) {
        case 'meta': {
          const meta = parseMeta(frame.data)
          modelName = meta.model
          break
        }
        case 'token': {
          const chunk = parseToken(frame.data)
          if (chunk !== null) {
            accumulated += chunk
            callbacks.onToken(accumulated)
          }
          break
        }
        case 'done': {
          return { kind: 'done', text: accumulated, model: modelName }
        }
        case 'error': {
          const error = parseError(frame.data)
          return { kind: 'error', ...error }
        }
        default:
          // An unknown event is ignored rather than treated as an error. A newer server adding
          // an event must not break an older client mid-answer.
          break
      }
    }
    return null
  }

  try {
    for (;;) {
      const { done, value } = await reader.read()
      if (done) {
        break
      }
      const result = handle(parser.push(decoder.decode(value, { stream: true })))
      if (result !== null) {
        callbacks.onDone(result)
        return result
      }
    }

    // The stream closed without a terminal frame. That is a truncated response, not a completed
    // one, and the difference decides whether the retry button appears.
    const trailing = handle(parser.flush())
    if (trailing !== null) {
      callbacks.onDone(trailing)
      return trailing
    }

    if (stopped) {
      return { kind: 'stopped', text: accumulated }
    }

    const truncated: StreamResult = {
      kind: 'error',
      code: 'STREAM_TRUNCATED',
      message: 'The response ended before it was complete.',
      retryable: true,
    }
    callbacks.onDone(truncated)
    return truncated
  } catch (cause) {
    if (controller.signal.aborted) {
      // A stop is a user decision, not a failure. Whatever arrived is kept.
      stopped = true
      const result: StreamResult = { kind: 'stopped', text: accumulated }
      callbacks.onDone(result)
      return result
    }
    throw toApiError(cause)
  } finally {
    reader.releaseLock()
  }
}

/** Builds an ApiError from an error status that arrived before the stream opened. */
function parseStreamError(status: number, text: string): {
  status: number
  code: string
  message: string
  traceId: string | null
} {
  try {
    const parsed = JSON.parse(text) as {
      code?: unknown
      message?: unknown
      traceId?: unknown
    }
    if (typeof parsed.code === 'string' && typeof parsed.message === 'string') {
      return {
        status,
        code: parsed.code,
        message: parsed.message,
        traceId: typeof parsed.traceId === 'string' ? parsed.traceId : null,
      }
    }
  } catch {
    // Not JSON. Fall through.
  }
  return {
    status,
    code: status >= 500 ? 'INTERNAL_ERROR' : 'REQUEST_FAILED',
    message:
      status === 503
        ? 'The AI service is temporarily unavailable. Please try again.'
        : 'The message could not be sent.',
    traceId: null,
  }
}