import { BrandLogo } from '../common/BrandLogo.tsx'
import type { AppRoute } from '../../types/index.ts'

export function PublicFooter({ onNavigate }: { readonly onNavigate: (route: AppRoute) => void }) {
  return (
    <footer className="border-top border-subtle bg-base py-5 mt-auto">
      <div className="container-xl px-3">
        <div className="row g-4 justify-content-between mb-4">
          <div className="col-12 col-md-4">
            <BrandLogo size="md" onClick={() => onNavigate('home')} />
            <p className="text-secondary small mt-2 mb-0" style={{ maxWidth: '340px' }}>
              One intelligent workspace for your AI conversations. Ground your reasoning with real documents, persistent memory, and multi-model flexibility.
            </p>
          </div>
          <div className="col-6 col-sm-4 col-md-2">
            <div className="fw-semibold small text-body mb-2.5">Product</div>
            <ul className="list-unstyled d-flex flex-column gap-1.5 small text-secondary mb-0">
              <li>
                <button
                  type="button"
                  className="btn btn-link p-0 text-secondary text-decoration-none small"
                  onClick={() => onNavigate('home')}
                >
                  Features
                </button>
              </li>
              <li>
                <button
                  type="button"
                  className="btn btn-link p-0 text-secondary text-decoration-none small"
                  onClick={() => onNavigate('pricing')}
                >
                  Pricing
                </button>
              </li>
              <li>
                <button
                  type="button"
                  className="btn btn-link p-0 text-secondary text-decoration-none small"
                  onClick={() => onNavigate('faq')}
                >
                  FAQ
                </button>
              </li>
            </ul>
          </div>
          <div className="col-6 col-sm-4 col-md-2">
            <div className="fw-semibold small text-body mb-2.5">Workspace</div>
            <ul className="list-unstyled d-flex flex-column gap-1.5 small text-secondary mb-0">
              <li>
                <button
                  type="button"
                  className="btn btn-link p-0 text-secondary text-decoration-none small"
                  onClick={() => onNavigate('chat')}
                >
                  AI Chat
                </button>
              </li>
              <li>
                <button
                  type="button"
                  className="btn btn-link p-0 text-secondary text-decoration-none small"
                  onClick={() => onNavigate('documents')}
                >
                  Document RAG
                </button>
              </li>
              <li>
                <button
                  type="button"
                  className="btn btn-link p-0 text-secondary text-decoration-none small"
                  onClick={() => onNavigate('usage')}
                >
                  Usage Dashboard
                </button>
              </li>
            </ul>
          </div>
          <div className="col-6 col-sm-4 col-md-2">
            <div className="fw-semibold small text-body mb-2.5">Account</div>
            <ul className="list-unstyled d-flex flex-column gap-1.5 small text-secondary mb-0">
              <li>
                <button
                  type="button"
                  className="btn btn-link p-0 text-secondary text-decoration-none small"
                  onClick={() => onNavigate('login')}
                >
                  Sign In
                </button>
              </li>
              <li>
                <button
                  type="button"
                  className="btn btn-link p-0 text-secondary text-decoration-none small"
                  onClick={() => onNavigate('register')}
                >
                  Create Account
                </button>
              </li>
              <li>
                <button
                  type="button"
                  className="btn btn-link p-0 text-secondary text-decoration-none small"
                  onClick={() => onNavigate('settings')}
                >
                  Preferences
                </button>
              </li>
            </ul>
          </div>
        </div>

        <div className="pt-4 border-top border-subtle d-flex flex-column flex-sm-row align-items-center justify-content-between gap-2 small text-muted">
          <div>© {new Date().getFullYear()} NexaAI. All rights reserved.</div>
          <div className="d-flex align-items-center gap-3">
            <span>Inter & JetBrains Mono</span>
            <span>•</span>
            <span>Razorpay Test Mode Verified</span>
          </div>
        </div>
      </div>
    </footer>
  )
}
