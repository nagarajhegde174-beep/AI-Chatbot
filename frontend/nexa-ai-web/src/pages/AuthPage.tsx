/**
 * Sign in and create account.
 *
 * <p>One component for both, because they are the same form with a different endpoint and a
 * different field set. Two near-identical components would drift: a validation rule fixed in one
 * and not the other is the normal outcome of splitting them.
 *
 * <p>The mode comes from the route rather than from component state, so the URL always says which
 * form is showing and the back button does something sensible.
 */

import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'

import { useAuth } from '../auth/AuthContext'
import { ApiError } from '../api/client'
import { ErrorState } from '../components/States'

type Mode = 'signin' | 'register'

export function AuthPage({ mode }: { readonly mode: Mode }): React.JSX.Element {
  const { signIn, signUp } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<unknown>(null)

  // Where to go after signing in. Defaults to /chat, so a direct link to a protected page still
  // lands somewhere sensible after authentication.
  const from = (location.state as { readonly from?: string } | null)?.from ?? '/chat'

  const onSubmit = async (event: FormEvent): Promise<void> => {
    event.preventDefault()
    setSubmitting(true)
    setError(null)

    try {
      if (mode === 'signin') {
        await signIn(email, password)
      } else {
        await signUp(email, password, displayName)
      }
      navigate(from, { replace: true })
    } catch (cause) {
      setError(cause)
    } finally {
      setSubmitting(false)
    }
  }

  const isRegister = mode === 'register'
  const invalidCredentials =
    error instanceof ApiError && (error.status === 401 || error.status === 403)

  return (
    <div className="nexa-auth-page">
      <div className="nexa-surface-raised p-4 w-100" style={{ maxWidth: '26rem' }}>
        <h1 className="h4 mb-1">{isRegister ? 'Create your account' : 'Sign in'}</h1>
        <p className="text-body-secondary small mb-4">
          {isRegister
            ? 'Upload documents, ask questions, get answers with citations.'
            : 'Welcome back.'}
        </p>

        {invalidCredentials ? (
          <div className="alert alert-warning py-2" role="alert">
            That email and password combination was not accepted.
          </div>
        ) : null}

        {error !== null && !invalidCredentials ? <ErrorState error={error} title="Could not continue" /> : null}

        <form onSubmit={(event) => void onSubmit(event)} noValidate>
          {isRegister ? (
            <div className="mb-3">
              <label htmlFor="displayName" className="form-label">
                Name
              </label>
              <input
                id="displayName"
                type="text"
                className="form-control"
                value={displayName}
                required
                maxLength={120}
                autoComplete="name"
                onChange={(event) => setDisplayName(event.target.value)}
              />
            </div>
          ) : null}

          <div className="mb-3">
            <label htmlFor="email" className="form-label">
              Email
            </label>
            <input
              id="email"
              type="email"
              className="form-control"
              value={email}
              required
              autoComplete="email"
              onChange={(event) => setEmail(event.target.value)}
            />
          </div>

          <div className="mb-3">
            <label htmlFor="password" className="form-label">
              Password
            </label>
            <input
              id="password"
              type="password"
              className="form-control"
              value={password}
              required
              minLength={12}
              autoComplete={isRegister ? 'new-password' : 'current-password'}
              onChange={(event) => setPassword(event.target.value)}
            />
            {isRegister ? (
              <div className="form-text">At least 12 characters.</div>
            ) : null}
          </div>

          <button type="submit" className="btn btn-primary w-100" disabled={submitting}>
            {submitting ? (
              <span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />
            ) : null}
            {isRegister ? 'Create account' : 'Sign in'}
          </button>
        </form>

        <p className="small text-body-secondary text-center mt-3 mb-0">
          {isRegister ? (
            <>
              Already have an account? <Link to="/signin">Sign in</Link>
            </>
          ) : (
            <>
              No account yet? <Link to="/register">Create one</Link>
            </>
          )}
        </p>
      </div>
    </div>
  )
}
