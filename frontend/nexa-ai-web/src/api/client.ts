/**
 * The HTTP client every API call goes through.
 *
 * ## Why this exists rather than calling `fetch` inline
 *
 * Four things have to be true of every request, and repeating them at each call site is how one
 * of them ends up missing:
 *
 * 1. **Credentials.** The API authenticates with an HTTP-only cookie, so every call must send
 *    credentials. A call that forgets is a confusing 401 rather than an obvious bug.
 * 2. **CSRF.** A cookie-authenticated write must carry the CSRF token. The token lives in a
 *    readable cookie and has to be echoed into a header.
 * 3. **A uniform error.** Every backend returns the same `{code, message, traceId}` envelope, so
 *    one parse here means every caller can branch on `code` instead of matching on message text.
 * 4. **No secrets.** Nothing here reads an environment variable that could hold a credential. The
 *    browser holds no API key, and this module is the reason: the only credential it ever touches
 *    is the cookie the server set.
 *
 * ## No token in JavaScript
 *
 * Deliberately no `Authorization` header. An access token held in JavaScript is readable by any
 * script that runs on the page, and readable by anything that can inject one. The HTTP-only
 * cookie cannot be read by script at all, which is the whole reason for using it.
 */

/** The uniform error body every service returns. */
export interface ApiErrorBody {
  readonly status: number
  readonly code: string
  readonly message: string
  readonly traceId: string | null
  readonly violations?: readonly FieldViolation[]
}

export interface FieldViolation {
  readonly field: string
  readonly message: string
}

/**
 * A failed request, carrying the machine-readable code.
 *
 * <p>`code` rather than `message` is what callers branch on: a message is written for a person
 * and will be reworded, which silently breaks any logic that matched it.
 */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly traceId: string | null
  readonly violations: readonly FieldViolation[]

  constructor(body: ApiErrorBody) {
    super(body.message)
    this.name = 'ApiError'
    this.status = body.status
    this.code = body.code
    this.traceId = body.traceId
    this.violations = body.violations ?? []
  }

  /** True when the caller is not authenticated, so the UI should send them to sign in. */
  get isUnauthorised(): boolean {
    return this.status === 401
  }

  /** True when the caller is authenticated but not permitted. */
  get isForbidden(): boolean {
    return this.status === 403
  }

  /**
   * A message safe to show a person.
   *
   * <p>Validation failures list the offending fields, because "the request failed" tells a user
   * nothing they can act on.
   */
  get displayMessage(): string {
    if (this.violations.length === 0) {
      return this.message
    }
    return `${this.message} ${this.violations.map((v) => `${v.field}: ${v.message}`).join(' ')}`
  }
}

/** The name of the CSRF cookie the backend sets and expects echoed back. */
const CSRF_COOKIE = 'XSRF-TOKEN'

/** The header the token must be echoed into. */
const CSRF_HEADER = 'X-XSRF-TOKEN'

/**
 * Reads a cookie by name.
 *
 * <p>`document.cookie` cannot see HTTP-only cookies, which is correct: this exists for the CSRF
 * token, which the server deliberately makes readable by script.
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
 * Methods that need the CSRF token.
 *
 * <p>GET, HEAD and OPTIONS are safe by definition and must not require a token, so the check is
 * on the method rather than on the URL.
 */
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS'])

export interface RequestOptions {
  readonly method?: string
  readonly body?: unknown
  readonly signal?: AbortSignal
  readonly accept?: string
}

const BASE = '/api/v1'

/**
 * Issues a request and returns the parsed body.
 *
 * @throws ApiError on any non-2xx response, or on a body that is not JSON
 */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = (options.method ?? 'GET').toUpperCase()

  const headers: Record<string, string> = {
    Accept: options.accept ?? 'application/json',
  }

  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }

  if (!SAFE_METHODS.has(method)) {
    const csrfToken = readCookie(CSRF_COOKIE)
    if (csrfToken !== null) {
      headers[CSRF_HEADER] = csrfToken
    }
    // Absent token is not an error here. The backend will answer 403 and the caller shows that
    // message; silently continuing would hide a genuine misconfiguration.
  }

  const response = await fetch(`${BASE}${path}`, {
    method,
    headers,
    // Required for the HTTP-only session cookie to be sent at all.
    credentials: 'include',
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
    signal: options.signal,
  })

  if (response.status === 204) {
    return undefined as T
  }

  const text = await response.text()

  if (!response.ok) {
    throw new ApiError(parseErrorBody(response.status, text))
  }

  if (text === '') {
    return undefined as T
  }

  return JSON.parse(text) as T
}

/**
 * Builds an ApiError from whatever came back.
 *
 * <p>Falls back to a synthetic body when the response is not JSON, which happens for a proxy 502
 * and for anything else that never reached a service. A parse failure must not turn into an
 * unhandled rejection that leaves the user staring at a blank page.
 */
function parseErrorBody(status: number, text: string): ApiErrorBody {
  try {
    const parsed = JSON.parse(text) as Partial<ApiErrorBody>
    if (typeof parsed.code === 'string' && typeof parsed.message === 'string') {
      return {
        status,
        code: parsed.code,
        message: parsed.message,
        traceId: parsed.traceId ?? null,
        violations: parsed.violations ?? [],
      }
    }
  } catch {
    // Not JSON. Fall through to the generic body below.
  }

  return {
    status,
    code: status >= 500 ? 'INTERNAL_ERROR' : 'REQUEST_FAILED',
    message:
      status >= 500
        ? 'The service could not complete the request.'
        : 'The request could not be completed.',
    traceId: null,
    violations: [],
  }
}

/** Wraps anything thrown into an ApiError, so callers only ever handle one error type. */
export function toApiError(cause: unknown): ApiError {
  if (cause instanceof ApiError) {
    return cause
  }
  // A network failure or an aborted request. Not an HTTP status, so 0 says "no response".
  return new ApiError({
    status: 0,
    code: 'NETWORK_ERROR',
    message: 'Could not reach the server. Check your connection and try again.',
    traceId: null,
  })
}
