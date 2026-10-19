const GATEWAY_BASE_URL = import.meta.env.VITE_GATEWAY_URL || 'http://localhost:8080'

export class ApiError extends Error {
  readonly status: number
  readonly code?: string

  constructor(message: string, status: number, code?: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

export async function request<T>(
  path: string,
  options: RequestInit = {}
): Promise<T> {
  const token = localStorage.getItem('nexa_access_token')
  const headers = new Headers(options.headers || {})

  if (!headers.has('Content-Type') && !(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json')
  }

  if (token && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  const url = path.startsWith('http') ? path : `${GATEWAY_BASE_URL}${path}`

  try {
    const res = await fetch(url, {
      ...options,
      headers,
    })

    if (res.status === 204) {
      return undefined as unknown as T
    }

    const contentType = res.headers.get('content-type')
    const isJson = contentType && contentType.includes('application/json')
    const data = isJson ? await res.json() : await res.text()

    if (!res.ok) {
      const errorObj = typeof data === 'object' && data !== null && 'error' in data ? (data as { error?: { message?: string; code?: string } }).error : null
      const message = errorObj?.message || (typeof data === 'string' ? data : `Request failed with status ${res.status}`)
      throw new ApiError(message, res.status, errorObj?.code)
    }

    return data as T
  } catch (err: unknown) {
    if (err instanceof ApiError) {
      throw err
    }
    throw new ApiError(
      err instanceof Error ? err.message : 'Network error or service unavailable',
      0
    )
  }
}
