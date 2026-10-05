/**
 * The application shell: header, sidebar slot, main region.
 *
 * <p>The layout is a CSS grid rather than nested flex rows with fixed widths, because a chat
 * surface is two independently-scrolling regions and the sidebar must collapse to a drawer on a
 * narrow screen. Grid makes the relationship explicit; nested flex does not.
 *
 * <p>Responsive behaviour is a drawer toggled by a button, not a hidden sidebar. A sidebar that
 * simply disappears on mobile leaves no way to reach a conversation at all.
 */

import { useState, type ReactNode } from 'react'
import { useLocation } from 'react-router-dom'
import { LogOut, Menu, Monitor, Moon, PanelLeftClose, Sun } from 'lucide-react'

import { useAuth } from '../auth/AuthContext'
import { useTheme, type ThemePreference } from '../theme/ThemeContext'

export function AppShell({
  sidebar,
  children,
}: {
  /** The conversation list, or null on a screen that has none. */
  readonly sidebar?: ReactNode
  readonly children: ReactNode
}): React.JSX.Element {
  const { email, signOut } = useAuth()
  const [drawerOpen, setDrawerOpen] = useState(false)

  // Close the drawer when the ROUTE changes.
  //
  // Done by adjusting state during render rather than in an effect. Two reasons, and the second
  // is the real one:
  //
  // 1. An effect keyed on drawerOpen fired the instant the drawer opened and closed it again,
  //    so on a narrow screen the drawer could never stay open. Depending on the pathname is
  //    what was meant: leaving a drawer open over the content it just navigated to is the
  //    classic mobile bug.
  // 2. setState directly in an effect starts a second render pass for something React already
  //    knows during this one. Comparing against the route the drawer was opened at is the
  //    documented "adjust state when a prop changes" form, and it needs no effect at all.
  const { pathname } = useLocation()
  const [openedAt, setOpenedAt] = useState(pathname)

  if (openedAt !== pathname) {
    setOpenedAt(pathname)
    setDrawerOpen(false)
  }

  return (
    <div className="nexa-shell">
      <header className="nexa-shell-header">
        <div className="d-flex align-items-center gap-2">
          {sidebar !== undefined ? (
            <button
              type="button"
              className="btn btn-sm btn-ghost d-lg-none"
              aria-label="Show conversations"
              aria-expanded={drawerOpen}
              onClick={() => setDrawerOpen((open) => !open)}
            >
              <Menu size={18} aria-hidden="true" />
            </button>
          ) : null}

          <span className="nexa-wordmark">NexaAI</span>
        </div>

        <div className="d-flex align-items-center gap-2">
          <ThemeToggle />
          {email !== null ? (
            <>
              <span className="text-body-secondary small d-none d-sm-inline">
                {email}
              </span>
              <button
                type="button"
                className="btn btn-sm btn-ghost"
                onClick={() => {
                  void signOut()
                }}
              >
                <LogOut size={16} aria-hidden="true" />
                <span className="d-none d-sm-inline ms-1">Sign out</span>
              </button>
            </>
          ) : null}
        </div>
      </header>

      <div className="nexa-shell-body">
        {sidebar !== undefined ? (
          <>
            {/* The backdrop only exists on small screens, where the drawer overlays. */}
            {drawerOpen ? (
              <div
                className="nexa-drawer-backdrop d-lg-none"
                role="presentation"
                onClick={() => setDrawerOpen(false)}
              />
            ) : null}

            <aside
              className={`nexa-shell-sidebar ${drawerOpen ? 'is-open' : ''}`}
              aria-label="Conversations"
            >
              <div className="nexa-sidebar-header">
                <button
                  type="button"
                  className="btn btn-sm btn-primary w-100"
                  onClick={() => {
                    window.dispatchEvent(new CustomEvent('nexa:new-conversation'))
                  }}
                >
                  New chat
                </button>
                <button
                  type="button"
                  className="btn btn-sm btn-ghost d-lg-none ms-1"
                  aria-label="Close conversations"
                  onClick={() => setDrawerOpen(false)}
                >
                  <PanelLeftClose size={16} aria-hidden="true" />
                </button>
              </div>
              <div className="nexa-sidebar-scroll">{sidebar}</div>
            </aside>
          </>
        ) : null}

        <main className="nexa-shell-main">{children}</main>
      </div>
    </div>
  )
}

/**
 * The theme toggle.
 *
 * <p>Shows the CURRENT setting rather than the one a click would apply. A button labelled with
 * the target state ("Switch to dark") is a prediction; a button showing the current state
 * ("Dark") is a fact, and the icon carries the action.
 */
function ThemeToggle(): React.JSX.Element {
  const { preference, resolved, cycle } = useTheme()

  const icon =
    preference === 'system' ? (
      <Monitor size={16} aria-hidden="true" />
    ) : resolved === 'dark' ? (
      <Moon size={16} aria-hidden="true" />
    ) : (
      <Sun size={16} aria-hidden="true" />
    )

  const label: Record<ThemePreference, string> = {
    light: 'Light',
    dark: 'Dark',
    system: 'System',
  }

  return (
    <button
      type="button"
      className="btn btn-sm btn-ghost"
      onClick={cycle}
      title={`Theme: ${label[preference]}`}
    >
      {icon}
      <span className="visually-hidden">
        Theme is {label[preference]}. Activate to change.
      </span>
    </button>
  )
}
