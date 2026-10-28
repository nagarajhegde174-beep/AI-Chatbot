import { LogOut, MessageSquare, Shield, User as UserIcon } from 'lucide-react'
import { useAuth } from '../../context/AuthContext.tsx'
import { ThemeToggle } from '../common/ThemeToggle.tsx'
import type { AppRoute } from '../../types/index.ts'

interface HeaderProps {
  readonly currentRoute: AppRoute
  readonly onNavigate: (route: AppRoute) => void
}

export function Header({ currentRoute, onNavigate }: HeaderProps) {
  const { user, isAuthenticated, logout } = useAuth()

  return (
    <header className="navbar navbar-expand border-bottom px-3 py-2 bg-body">
      <div className="container-fluid px-0 d-flex justify-content-between align-items-center">
        <div className="d-flex align-items-center gap-3">
          <button
            type="button"
            className="btn btn-link text-decoration-none p-0 d-flex align-items-center gap-2 text-body"
            onClick={() => onNavigate(isAuthenticated ? 'chat' : 'login')}
          >
            <div className="badge bg-primary p-2 rounded-3 text-white d-flex align-items-center justify-content-center">
              <MessageSquare size={18} />
            </div>
            <span className="fw-bold fs-5 tracking-tight">NexaAI</span>
          </button>
          <span className="badge text-bg-secondary-subtle border text-secondary small d-none d-md-inline">
            Phase 9 — Usage + Admin + Analytics
          </span>
          {isAuthenticated && (
            <div className="btn-group btn-group-sm ms-2">
              <button
                type="button"
                className={`btn ${currentRoute === 'chat' ? 'btn-primary' : 'btn-outline-secondary'}`}
                onClick={() => onNavigate('chat')}
              >
                Chat
              </button>
              <button
                type="button"
                className={`btn ${currentRoute === 'usage' ? 'btn-primary' : 'btn-outline-secondary'}`}
                onClick={() => onNavigate('usage')}
              >
                Usage
              </button>
              {user?.roles?.includes('ADMIN') && (
                <button
                  type="button"
                  className={`btn ${currentRoute === 'admin' ? 'btn-warning' : 'btn-outline-warning'}`}
                  onClick={() => onNavigate('admin')}
                >
                  Admin
                </button>
              )}
            </div>
          )}
        </div>

        <div className="d-flex align-items-center gap-2">
          {isAuthenticated && (
            <div className="d-flex align-items-center gap-2 me-2">
              <span className="badge text-bg-light border d-flex align-items-center gap-1 text-secondary">
                <UserIcon size={14} />
                <span className="d-none d-sm-inline">{user?.displayName || user?.email}</span>
              </span>
              {user?.roles?.includes('ADMIN') && (
                <span className="badge text-bg-warning text-dark d-flex align-items-center gap-1">
                  <Shield size={12} />
                  ADMIN
                </span>
              )}
            </div>
          )}

          <ThemeToggle />

          {isAuthenticated ? (
            <button
              type="button"
              className="btn btn-sm btn-outline-danger d-flex align-items-center gap-1 ms-1"
              onClick={() => {
                logout()
                onNavigate('login')
              }}
              title="Sign out"
            >
              <LogOut size={16} />
              <span className="d-none d-sm-inline">Sign out</span>
            </button>
          ) : (
            <div className="btn-group btn-group-sm ms-1">
              <button
                type="button"
                className={`btn ${currentRoute === 'login' ? 'btn-primary' : 'btn-outline-primary'}`}
                onClick={() => onNavigate('login')}
              >
                Sign in
              </button>
              <button
                type="button"
                className={`btn ${currentRoute === 'register' ? 'btn-primary' : 'btn-outline-primary'}`}
                onClick={() => onNavigate('register')}
              >
                Sign up
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  )
}
