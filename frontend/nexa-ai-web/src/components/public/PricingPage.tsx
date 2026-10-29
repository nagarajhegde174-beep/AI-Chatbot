import { Check, AlertTriangle } from 'lucide-react'
import type { AppRoute } from '../../types/index.ts'

interface PricingPageProps {
  readonly onNavigate: (route: AppRoute) => void
  readonly currentPlan?: string
  readonly isAuthenticated?: boolean
}

export function PricingPage({
  onNavigate,
  currentPlan = 'FREE',
  isAuthenticated = false,
}: PricingPageProps) {
  const plans = [
    {
      id: 'FREE',
      name: 'FREE',
      price: '₹0',
      period: 'forever',
      description: 'Ideal for getting started with basic chat and testing AI capabilities.',
      tokens: '100,000 tokens',
      storage: '25 MB storage',
      docLimit: 'Up to 3 documents',
      models: 'gpt-4o-mini, gemini-1.5-flash',
      features: [
        'Multi-turn conversation history',
        'Basic document upload (PDF, TXT)',
        'Full Markdown formatting with syntax highlighting',
        'Export conversations to Markdown & JSON',
        'Standard community support',
      ],
      highlight: false,
    },
    {
      id: 'NORMAL',
      name: 'NORMAL',
      price: '₹399',
      period: '30 days',
      description: 'Designed for professionals who need daily AI research and document grounding.',
      tokens: '500,000 tokens / 30 days',
      storage: '250 MB storage',
      docLimit: 'Up to 25 documents',
      models: 'All standard models + Groq LLaMA 3.1 70B',
      features: [
        'Everything in Free tier',
        'Semantic RAG document grounding with citations',
        'Multi-format ingestion: PDF, DOCX, TXT, CSV',
        'Detailed token & storage analytics breakdown',
        'Faster streaming response times',
        'Priority email support',
      ],
      highlight: true,
    },
    {
      id: 'PRO',
      name: 'PRO',
      price: '₹1,099',
      period: '30 days',
      description: 'Unrestricted power for power users and extensive document libraries.',
      tokens: '2,000,000 tokens / 30 days',
      storage: '2 GB storage',
      docLimit: 'Unlimited documents',
      models: 'gpt-4o, gemini-1.5-pro, llama-3.1-70b',
      features: [
        'Everything in Normal tier',
        'Large context reasoning models enabled',
        'Parallel document processing queues',
        'Granular prompt configuration & temperature control',
        'Direct session revocation & security management',
        'Priority customer support',
      ],
      highlight: false,
    },
  ]

  const handleSelectPlan = (planId: string) => {
    if (!isAuthenticated) {
      onNavigate('login')
      return
    }
    if (planId === currentPlan) return
    onNavigate('billing')
  }

  return (
    <div className="container-xl px-3 py-5">
      {/* Test Mode Notification Banner */}
      <div className="alert alert-warning border border-warning d-flex align-items-center justify-content-between p-3 mb-5 rounded-3">
        <div className="d-flex align-items-center gap-2.5">
          <AlertTriangle size={20} className="text-warning flex-shrink-0" />
          <div className="small">
            <strong>RAZORPAY TEST MODE ACTIVE:</strong> All subscription actions and transactions are conducted in sandbox test mode. No real money or credit card charges will occur.
          </div>
        </div>
        <span className="nexa-badge nexa-badge-test d-none d-md-inline-flex">TEST SANDBOX</span>
      </div>

      <div className="text-center mb-5">
        <span className="nexa-badge nexa-badge-brand mb-2">Subscription Plans</span>
        <h1 className="fw-bold tracking-tight mb-3">Simple, Predictable Pricing</h1>
        <p className="text-secondary mx-auto mb-0" style={{ maxWidth: '580px' }}>
          Select the subscription tier that matches your workload. Upgrade or downgrade at any time with transparent token quota accounting.
        </p>
      </div>

      {/* Plan Cards */}
      <div className="row g-4 justify-content-center mb-5">
        {plans.map((p) => {
          const isCurrent = p.id === currentPlan
          return (
            <div key={p.id} className="col-12 col-md-4">
              <div
                className={`nexa-card p-4 h-100 d-flex flex-column ${
                  p.highlight ? 'border-primary shadow-sm' : ''
                }`}
                style={p.highlight ? { borderColor: 'var(--brand-primary)' } : undefined}
              >
                <div className="d-flex align-items-center justify-content-between mb-2">
                  <h4 className="fw-bold mb-0">{p.name}</h4>
                  {isCurrent ? (
                    <span className="nexa-badge nexa-badge-secondary">Current Plan</span>
                  ) : p.highlight ? (
                    <span className="nexa-badge nexa-badge-brand">Recommended</span>
                  ) : null}
                </div>

                <div className="d-flex align-items-baseline gap-1 my-3">
                  <span className="fs-1 fw-bold text-body">{p.price}</span>
                  <span className="text-secondary small">/ {p.period}</span>
                </div>

                <p className="text-secondary small mb-4">{p.description}</p>

                <div className="p-3 rounded bg-surface border border-subtle mb-4 small d-flex flex-column gap-1.5">
                  <div className="d-flex justify-content-between">
                    <span className="text-secondary">Token Quota:</span>
                    <strong className="text-body font-monospace">{p.tokens}</strong>
                  </div>
                  <div className="d-flex justify-content-between">
                    <span className="text-secondary">Storage:</span>
                    <strong className="text-body font-monospace">{p.storage}</strong>
                  </div>
                  <div className="d-flex justify-content-between">
                    <span className="text-secondary">Documents:</span>
                    <strong className="text-body font-monospace">{p.docLimit}</strong>
                  </div>
                </div>

                <ul className="list-unstyled d-flex flex-column gap-2 small mb-4 flex-grow-1">
                  {p.features.map((f, i) => (
                    <li key={i} className="d-flex align-items-start gap-2">
                      <Check size={15} className="text-primary mt-0.5 flex-shrink-0" />
                      <span>{f}</span>
                    </li>
                  ))}
                </ul>

                <button
                  type="button"
                  className={
                    isCurrent
                      ? 'nexa-btn-secondary w-100 disabled'
                      : p.highlight
                      ? 'nexa-btn-primary w-100'
                      : 'nexa-btn-secondary w-100'
                  }
                  disabled={isCurrent}
                  onClick={() => handleSelectPlan(p.id)}
                >
                  {isCurrent
                    ? 'Current Plan'
                    : p.id === 'FREE'
                    ? 'Downgrade to Free'
                    : `Select ${p.name}`}
                </button>
              </div>
            </div>
          )
        })}
      </div>

      {/* Feature Comparison Table */}
      <div className="mt-5">
        <h3 className="fw-bold tracking-tight mb-4 text-center">Plan Comparison Matrix</h3>
        <div className="nexa-table-container">
          <table className="nexa-table">
            <thead>
              <tr>
                <th style={{ width: '40%' }}>Capability</th>
                <th className="text-center" style={{ width: '20%' }}>FREE</th>
                <th className="text-center" style={{ width: '20%' }}>NORMAL</th>
                <th className="text-center" style={{ width: '20%' }}>PRO</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>Monthly / Periodic Price</td>
                <td className="text-center font-monospace">₹0</td>
                <td className="text-center font-monospace">₹399</td>
                <td className="text-center font-monospace">₹1,099</td>
              </tr>
              <tr>
                <td>Billing Cycle</td>
                <td className="text-center">Permanent</td>
                <td className="text-center">30 Days</td>
                <td className="text-center">30 Days</td>
              </tr>
              <tr>
                <td>Token Allowance</td>
                <td className="text-center font-monospace">100,000</td>
                <td className="text-center font-monospace">500,000</td>
                <td className="text-center font-monospace">2,000,000</td>
              </tr>
              <tr>
                <td>Document Storage</td>
                <td className="text-center">25 MB</td>
                <td className="text-center">250 MB</td>
                <td className="text-center">2 GB</td>
              </tr>
              <tr>
                <td>Document Count Limit</td>
                <td className="text-center">3 files</td>
                <td className="text-center">25 files</td>
                <td className="text-center">Unlimited</td>
              </tr>
              <tr>
                <td>Semantic Citations in Chat</td>
                <td className="text-center text-muted">—</td>
                <td className="text-center text-success"><Check size={16} /></td>
                <td className="text-center text-success"><Check size={16} /></td>
              </tr>
              <tr>
                <td>High-Reasoning Models (gpt-4o, gemini-1.5-pro)</td>
                <td className="text-center text-muted">—</td>
                <td className="text-center text-muted">—</td>
                <td className="text-center text-success"><Check size={16} /></td>
              </tr>
              <tr>
                <td>Payment Gateway Integration</td>
                <td className="text-center text-muted">—</td>
                <td className="text-center font-monospace small">Razorpay Test</td>
                <td className="text-center font-monospace small">Razorpay Test</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
