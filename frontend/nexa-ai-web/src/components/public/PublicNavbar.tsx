import { useState } from 'react'
import { Menu, X, ArrowRight } from 'lucide-react'
import { BrandLogo } from '../common/BrandLogo.tsx'
import { ThemeToggle } from '../common/ThemeToggle.tsx'
import type { AppRoute } from '../../types/index.ts'

interface PublicNavbarProps {
  readonly currentRoute: AppRoute
  readonly onNavigate: (route: AppRoute) => void
  readonly isAuthenticated?: boolean
}

export function PublicNavbar({
  currentRoute,
  onNavigate,
  isAuthenticated = false,
}: PublicNavbarProps) {
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)

  const handleNav = (route: AppRoute) => {
    onNavigate(route)
    setMobileMenuOpen(false)
  }

  return (
    <header className="border-bottom border-subtle bg-elevated sticky-top" style={{ zIndex: 1020 }}>
      <div className="container-xl d-flex align-items-center justify-content-between py-2.5 px-3">
        {/* Brand */}
        <BrandLogo onClick={() => handleNav('home')} />

        {/* Desktop Navigation Links */}
        <nav className="d-none d-md-flex align-items-center gap-4" aria-label="Main Navigation">
          <button
            type="button"
            className={`btn btn-link p-0 text-decoration-none small ${
              currentRoute === 'home' ? 'text-body fw-semibold' : 'text-secondary'
            }`}
            onClick={() => handleNav('home')}
          >
            Home
          </button>
          <button
            type="button"
            className={`btn btn-link p-0 text-decoration-none small ${
              currentRoute === 'pricing' ? 'text-body fw-semibold' : 'text-secondary'
            }`}
            onClick={() => handleNav('pricing')}
          >
            Pricing
          </button>
          <button
            type="button"
            className={`btn btn-link p-0 text-decoration-none small ${
              currentRoute === 'faq' ? 'text-body fw-semibold' : 'text-secondary'
            }`}
            onClick={() => handleNav('faq')}
          >
            FAQ
          </button>
        </nav>

        {/* Desktop Right Actions */}
        <div className="d-none d-md-flex align-items-center gap-2.5">
          <ThemeToggle />
          {isAuthenticated ? (
            <button
              type="button"
              className="nexa-btn-primary"
              onClick={() => handleNav('app')}
            >
              <span>Workspace</span>
              <ArrowRight size={15} />
            </button>
          ) : (
            <>
              <button
                type="button"
                className="nexa-btn-ghost"
                onClick={() => handleNav('login')}
              >
                Login
              </button>
              <button
                type="button"
                className="nexa-btn-primary"
                onClick={() => handleNav('register')}
              >
                <span>Get Started</span>
                <ArrowRight size={15} />
              </button>
            </>
          )}
        </div>

        {/* Mobile Hamburger Button */}
        <div className="d-flex d-md-none align-items-center gap-2">
          <ThemeToggle />
          <button
            type="button"
            className="btn btn-sm btn-outline-secondary p-1.5"
            onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
            aria-label="Toggle navigation menu"
            aria-expanded={mobileMenuOpen}
          >
            {mobileMenuOpen ? <X size={20} /> : <Menu size={20} />}
          </button>
        </div>
      </div>

      {/* Mobile Dropdown / Drawer */}
      {mobileMenuOpen && (
        <div className="d-md-none border-top border-subtle bg-elevated px-3 py-3 shadow-sm">
          <div className="d-flex flex-column gap-2 mb-3">
            <button
              type="button"
              className="nexa-nav-item text-start"
              onClick={() => handleNav('home')}
            >
              Home
            </button>
            <button
              type="button"
              className="nexa-nav-item text-start"
              onClick={() => handleNav('pricing')}
            >
              Pricing
            </button>
            <button
              type="button"
              className="nexa-nav-item text-start"
              onClick={() => handleNav('faq')}
            >
              FAQ
            </button>
          </div>
          <div className="pt-2 border-top border-subtle d-flex flex-column gap-2">
            {isAuthenticated ? (
              <button
                type="button"
                className="nexa-btn-primary w-100"
                onClick={() => handleNav('app')}
              >
                Go to Workspace
              </button>
            ) : (
              <>
                <button
                  type="button"
                  className="nexa-btn-secondary w-100"
                  onClick={() => handleNav('login')}
                >
                  Login
                </button>
                <button
                  type="button"
                  className="nexa-btn-primary w-100"
                  onClick={() => handleNav('register')}
                >
                  Get Started
                </button>
              </>
            )}
          </div>
        </div>
      )}
    </header>
  )
}
