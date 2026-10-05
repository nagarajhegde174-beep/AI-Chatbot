/**
 * Authentication bindings.
 *
 * <p>Everything here goes through the HTTP-only cookie the backend sets. This module never reads,
 * stores or sends a token: an access token in JavaScript is readable by any script on the page,
 * and readable by anything able to inject one. The cookie cannot be read by script at all, which
 * is the reason for using it.
 */

import { request, type ApiErrorBody } from './client'

/** The authenticated caller's own account, as this frontend needs it. */
export interface CurrentUser {
  readonly email: string
  readonly roles: readonly string[]
}

export interface Credentials {
  readonly email: string
  readonly password: string
}

export interface Registration extends Credentials {
  readonly displayName: string
}

export interface AuthResult {
  readonly email: string
  readonly roles: readonly string[]
}

export async function login(credentials: Credentials): Promise<AuthResult> {
  return request<AuthResult>('/auth/login', {
    method: 'POST',
    body: { email: credentials.email, password: credentials.password },
  })
}

export async function register(details: Registration): Promise<AuthResult> {
  return request<AuthResult>('/auth/register', {
    method: 'POST',
    body: {
      email: details.email,
      password: details.password,
      displayName: details.displayName,
    },
  })
}

export async function logout(): Promise<void> {
  await request<void>('/auth/logout', { method: 'POST' })
}

/**
 * Whether there is a signed-in session.
 *
 * <p>Probes a cheap authenticated endpoint rather than reading the cookie, because the cookie is
 * HTTP-only and unreadable by design. A 401 is a legitimate answer meaning "not signed in", not
 * an error, so it resolves `false` rather than throwing.
 */
export async function isAuthenticated(): Promise<boolean> {
  try {
    await request('/auth/me')
    return true
  } catch (cause) {
    const status = (cause as { status?: number }).status
    // 0 is a network failure, which is NOT the same as "not signed in". Reporting false for an
    // unreachable server would silently sign the user out during an outage.
    return status !== 401 && status !== 403 && status !== 0
  }
}

/**
 * The display name to show in the shell.
 *
 * <p>Falls back to the email's local part, because an empty header looks broken and the email is
 * already on screen elsewhere.
 */
export function displayNameFor(user: CurrentUser): string {
  return user.email.split('@')[0] ?? user.email
}

export type { ApiErrorBody }
