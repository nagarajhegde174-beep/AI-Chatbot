import { useState, useRef, useEffect } from 'react'
import {
  Search, User, CreditCard, Activity, Settings, LogOut,
  Shield, Sparkles, ChevronDown
} from 'lucide-react'
import { BrandLogo } from '../common/BrandLogo.tsx'
import { ThemeToggle } from '../common/ThemeToggle.tsx'
import { useAuth } from '../../context/AuthContext.tsx'
import type { AppRoute } from '../../types/index.ts'

interface HeaderProps {
  readonly currentRoute: AppRoute
  readonly onNavigate: (route: AppRoute) => void
  readonly onSearch?: (query: string) => void
}

export function Header({ currentRoute, onNavigate, onSearch }: HeaderProps) {
  const { user, logout } = useAuth()
  const [dropdownOpen, setDropdownOpen] = useState(false)
  const [searchVal, setSearchVal] = useState('')
  const dropdownRef = useRef<HTMLDivElement>(null)

  const isAdmin = user?.roles?.includes('ADMIN')
  const userInitials = (user?.displayName || user?.email || 'U')
    .slice(0, 2)
    .toUpperCase()

  // Close dropdown on outside click
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setDropdownOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  const handleSearchChange = (val: string) => {
    setSearchVal(val)
    if (onSearch) {
      onSearch(val)
    }
  }

  const handleProfileNav = (route: AppRoute) => {
    onNavigate(route)
    setDropdownOpen(false)
  }

  const handleLogout = () => {
    logout()
    setDropdownOpen(false)
    onNavigate('login')
  }

  return (
    <header className="border-bottom border-subtle bg-elevated px-3 py-2 d-flex align-items-center justify-content-between" style={{ zIndex: 1010 }}>
      {/* Brand & Search */}
      <div className="d-flex align-items-center gap-3">
        <BrandLogo size="md" onClick={() => onNavigate('app')} />

        <div className="d-none d-md-flex align-items-center position-relative" style={{ width: '260px' }}>
          <Search size={14} className="position-absolute start-0 ms-2.5 text-muted pointer-events-none" />
          <input
            type="search"
            className="nexa-input ps-4 py-1.5 small"
            style={{ fontSize: '0.8125rem' }}
            placeholder="Search conversations & docs..."
            value={searchVal}
            onChange={(e) => handleSearchChange(e.target.value)}
          />
        </div>
      </div>

      {/* Right Controls: Theme, Subscription Badge, Admin link, Avatar & Profile Menu */}
      <div className="d-flex align-items-center gap-2.5">
        {/* Admin Link if authorized */}
        {isAdmin && (
          <button
            type="button"
            className={`btn btn-sm d-flex align-items-center gap-1.5 ${
              currentRoute === 'admin' ? 'btn-primary' : 'btn-outline-secondary'
            }`}
            onClick={() => onNavigate('admin')}
            title="Open Admin Operations Center"
          >
            <Shield size={14} />
            <span className="small d-none d-sm-inline">Admin</span>
          </button>
        )}

        {/* Subscription Plan Badge */}
        <button
          type="button"
          className="nexa-badge nexa-badge-brand text-decoration-none border-0"
          style={{ cursor: 'pointer' }}
          onClick={() => onNavigate('billing')}
          title="Manage Subscription"
        >
          <Sparkles size={12} />
          <span>PRO TIER</span>
        </button>

        {/* Theme Toggle */}
        <ThemeToggle />

        {/* Profile Menu Dropdown */}
        <div className="position-relative" ref={dropdownRef}>
          <button
            type="button"
            className="btn btn-sm p-0 d-flex align-items-center gap-1.5 border-0 bg-transparent text-body"
            onClick={() => setDropdownOpen(!dropdownOpen)}
            aria-expanded={dropdownOpen}
            aria-label="User profile menu"
          >
            <div
              className="rounded-circle d-flex align-items-center justify-content-center fw-semibold text-white small"
              style={{
                width: '32px',
                height: '32px',
                backgroundColor: 'var(--brand-primary)',
                fontSize: '0.8rem',
              }}
            >
              {userInitials}
            </div>
            <ChevronDown size={14} className="text-secondary d-none d-sm-inline" />
          </button>

          {dropdownOpen && (
            <div
              className="position-absolute end-0 mt-2 py-2 shadow-lg border border-subtle rounded-3 bg-elevated"
              style={{ width: '220px', zIndex: 1050 }}
            >
              <div className="px-3 py-1.5 border-bottom border-subtle mb-1">
                <div className="fw-semibold text-body small text-truncate">
                  {user?.displayName || 'User'}
                </div>
                <div className="text-muted small text-truncate" style={{ fontSize: '0.75rem' }}>
                  {user?.email}
                </div>
              </div>

              <button
                type="button"
                className="dropdown-item d-flex align-items-center gap-2 px-3 py-1.5 small text-body"
                onClick={() => handleProfileNav('settings')}
              >
                <User size={14} className="text-secondary" />
                <span>Profile</span>
              </button>

              <button
                type="button"
                className="dropdown-item d-flex align-items-center gap-2 px-3 py-1.5 small text-body"
                onClick={() => handleProfileNav('billing')}
              >
                <CreditCard size={14} className="text-secondary" />
                <span>Billing</span>
              </button>

              <button
                type="button"
                className="dropdown-item d-flex align-items-center gap-2 px-3 py-1.5 small text-body"
                onClick={() => handleProfileNav('usage')}
              >
                <Activity size={14} className="text-secondary" />
                <span>Usage</span>
              </button>

              <button
                type="button"
                className="dropdown-item d-flex align-items-center gap-2 px-3 py-1.5 small text-body"
                onClick={() => handleProfileNav('settings')}
              >
                <Settings size={14} className="text-secondary" />
                <span>Settings</span>
              </button>

              <div className="dropdown-divider my-1 border-subtle" />

              <button
                type="button"
                className="dropdown-item d-flex align-items-center gap-2 px-3 py-1.5 small text-danger"
                onClick={handleLogout}
              >
                <LogOut size={14} />
                <span>Logout</span>
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  )
}
