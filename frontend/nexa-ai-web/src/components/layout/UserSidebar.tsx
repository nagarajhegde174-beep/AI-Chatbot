import { Plus, MessageSquare, FileText, Activity, Settings, X } from 'lucide-react'
import type { AppRoute } from '../../types/index.ts'

interface UserSidebarProps {
  readonly currentRoute: AppRoute
  readonly onNavigate: (route: AppRoute) => void
  readonly onNewChat: () => void
  readonly isOpen: boolean
  readonly onClose: () => void
}

export function UserSidebar({
  currentRoute,
  onNavigate,
  onNewChat,
  isOpen,
  onClose,
}: UserSidebarProps) {
  const handleNav = (route: AppRoute) => {
    onNavigate(route)
    onClose()
  }

  const handleNewChatClick = () => {
    onNewChat()
    onClose()
  }

  return (
    <>
      {/* Mobile Backdrop overlay */}
      <div
        className={`nexa-sidebar-overlay ${isOpen ? 'is-open' : ''}`}
        onClick={onClose}
        aria-hidden="true"
      />

      {/* Sidebar container */}
      <aside className={`nexa-sidebar ${isOpen ? 'is-open' : ''}`}>
        {/* Top: Mobile header & New Chat button */}
        <div className="p-3 border-bottom border-subtle">
          <div className="d-flex align-items-center justify-content-between d-lg-none mb-2.5">
            <span className="small fw-semibold text-secondary">Navigation</span>
            <button
              type="button"
              className="btn btn-sm btn-ghost p-1 text-secondary"
              onClick={onClose}
              aria-label="Close sidebar"
            >
              <X size={18} />
            </button>
          </div>

          <button
            type="button"
            className="nexa-btn-primary w-100 py-2 d-flex align-items-center justify-content-center gap-2"
            onClick={handleNewChatClick}
          >
            <Plus size={16} />
            <span>New Chat</span>
          </button>
        </div>

        {/* Main Navigation links */}
        <div className="p-2 d-flex flex-column gap-1 flex-grow-1 overflow-y-auto">
          <button
            type="button"
            className={`nexa-nav-item w-100 text-start ${currentRoute === 'chat' ? 'is-active' : ''}`}
            onClick={() => handleNav('chat')}
          >
            <MessageSquare size={16} />
            <span>Chat</span>
          </button>

          <button
            type="button"
            className={`nexa-nav-item w-100 text-start ${currentRoute === 'documents' ? 'is-active' : ''}`}
            onClick={() => handleNav('documents')}
          >
            <FileText size={16} />
            <span>Documents</span>
          </button>

          <button
            type="button"
            className={`nexa-nav-item w-100 text-start ${currentRoute === 'usage' ? 'is-active' : ''}`}
            onClick={() => handleNav('usage')}
          >
            <Activity size={16} />
            <span>Usage</span>
          </button>

          <button
            type="button"
            className={`nexa-nav-item w-100 text-start ${currentRoute === 'settings' ? 'is-active' : ''}`}
            onClick={() => handleNav('settings')}
          >
            <Settings size={16} />
            <span>Settings</span>
          </button>
        </div>

        {/* Bottom subtle footer indicator */}
        <div className="p-3 border-top border-subtle text-muted small" style={{ fontSize: '0.75rem' }}>
          <div className="d-flex align-items-center justify-content-between">
            <span>NexaAI v1.0</span>
            <span className="nexa-badge nexa-badge-subtle">Online</span>
          </div>
        </div>
      </aside>
    </>
  )
}
