/**
 * Routes and the authenticated-route guard.
 *
 * <p>The guard waits for the `checking` status before deciding. Redirecting to sign-in while the
 * session is still being established is what produces the sign-in page flashing at a signed-in
 * user on every refresh; showing a quiet placeholder instead is nearly invisible and honest.
 */

import { Navigate, Route, Routes, useLocation } from 'react-router-dom'

import { useAuth } from './auth/AuthContext'
import { LoadingState } from './components/States'
import { AuthPage } from './pages/AuthPage'
import { ChatPage } from './pages/ChatPage'
import { EmptyState } from './components/States'

export function AppRoutes(): React.JSX.Element {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/chat" replace />} />
      <Route path="/chat" element={<ProtectedRoute />} />
      <Route path="/chat/:conversationId" element={<ProtectedRoute />} />
      <Route path="/signin" element={<AuthPage mode="signin" />} />
      <Route path="/register" element={<AuthPage mode="register" />} />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}

function ProtectedRoute(): React.JSX.Element {
  const { status } = useAuth()
  const location = useLocation()

  if (status === 'checking') {
    return <LoadingState label="Checking your session" />
  }

  if (status === 'anonymous') {
    // state carries where the user was heading, so signing in returns them there rather than
    // dumping them on a default page.
    return <Navigate to="/signin" replace state={{ from: location.pathname }} />
  }

  return <ChatPage />
}

function NotFoundPage(): React.JSX.Element {
  return (
    <div className="nexa-auth-page">
      <EmptyState
        title="Page not found"
        description="That address does not match anything in this application."
        action={
          <a className="btn btn-primary" href="/chat">
            Go to your chats
          </a>
        }
      />
    </div>
  )
}
