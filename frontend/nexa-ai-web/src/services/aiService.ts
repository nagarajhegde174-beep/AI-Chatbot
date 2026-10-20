import { request } from './apiClient.ts'

const GATEWAY_BASE_URL = import.meta.env.VITE_GATEWAY_URL || 'http://localhost:8080'

export interface StreamChunk {
  requestId: string
  token: string | null
  done: boolean
  summary?: StreamSummary
}

export interface StreamSummary {
  requestId: string
  provider: string
  model: string
  content: string
  finishReason: string
  usage: { promptTokens: number; completionTokens: number; totalTokens: number }
  latencyMs: number
}

export interface ModelInfo {
  id: string
  displayName: string
  maxContextTokens: number
  maxOutputTokens: number
  supportsStreaming: boolean
  supportsTemperature: boolean
  tags: Record<string, string>
}

export interface ProviderModels {
  provider: string
  available: boolean
  models: ModelInfo[]
}

export interface ModelCatalog {
  providers: ProviderModels[]
}

/**
 * Fetch the available AI model catalogue from the backend.
 * Routed through the API gateway at /api/v1/models.
 */
export async function fetchModelCatalog(): Promise<ModelCatalog> {
  return request<ModelCatalog>('/api/v1/models', {
    method: 'GET',
  })
}

/**
 * Stream a chat message via SSE.
 *
 * Returns an AbortController so the caller can stop generation.
 * Each token is passed to onToken; the final summary is passed to onDone.
 * Errors surface through onError.
 */
export function streamMessage(
  conversationId: string,
  content: string,
  model: string,
  onToken: (token: string) => void,
  onDone: (summary: StreamSummary) => void,
  onError: (err: string) => void,
): AbortController {
  const controller = new AbortController()

  void (async () => {
    try {
      const token = localStorage.getItem('nexa_access_token')
      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream',
      }
      if (token) {
        headers['Authorization'] = `Bearer ${token}`
      }

      const url = `${GATEWAY_BASE_URL}/api/v1/chat/conversations/${conversationId}/messages/stream`
      const res = await fetch(url, {
        method: 'POST',
        headers,
        body: JSON.stringify({ content, model }),
        signal: controller.signal,
      })

      if (!res.ok) {
        onError(`Stream request failed: ${res.status}`)
        return
      }

      const reader = res.body?.getReader()
      if (!reader) {
        onError('No response body from stream endpoint')
        return
      }

      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })

        // SSE lines are separated by \n\n
        const events = buffer.split('\n\n')
        buffer = events.pop() ?? ''

        for (const event of events) {
          const dataLine = event.split('\n').find((l) => l.startsWith('data:'))
          if (!dataLine) continue

          const json = dataLine.slice(5).trim()
          if (!json || json === '[DONE]') continue

          try {
            const chunk = JSON.parse(json) as StreamChunk
            if (chunk.done && chunk.summary) {
              onDone(chunk.summary)
            } else if (chunk.token) {
              onToken(chunk.token)
            }
          } catch {
            // malformed chunk — skip
          }
        }
      }
    } catch (err: unknown) {
      if (err instanceof DOMException && err.name === 'AbortError') return
      onError(err instanceof Error ? err.message : 'Streaming error')
    }
  })()

  return controller
}
