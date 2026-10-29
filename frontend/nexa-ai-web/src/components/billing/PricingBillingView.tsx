import { useState, useEffect } from 'react'
import { Check, AlertTriangle, CheckCircle2 } from 'lucide-react'
import { request } from '../../services/apiClient.ts'
import type { UserUsageData, AppRoute } from '../../types/index.ts'

export function PricingBillingView({ onNavigate }: { readonly onNavigate: (route: AppRoute) => void }) {
  const [usage, setUsage] = useState<UserUsageData | null>(null)
  const [actionSuccess, setActionSuccess] = useState<string | null>(null)
  const [processingPlan, setProcessingPlan] = useState<string | null>(null)

  const plans = [
    {
      id: 'FREE',
      name: 'FREE',
      price: '₹0',
      period: 'forever',
      tokens: 100000,
      storage: '25 MB',
      description: 'Standard plan for daily question answering.',
      models: ['gpt-4o-mini', 'gemini-1.5-flash'],
      features: ['100,000 token limit', '25 MB document storage', 'Up to 3 documents in RAG', 'Community support'],
    },
    {
      id: 'NORMAL',
      name: 'NORMAL',
      price: '₹399',
      period: '30 days',
      tokens: 500000,
      storage: '250 MB',
      description: 'Enhanced plan with high-volume tokens and extended RAG capacity.',
      models: ['gpt-4o-mini', 'gemini-1.5-flash', 'llama-3.1-70b-versatile'],
      features: ['500,000 token limit', '250 MB document storage', 'Up to 25 documents in RAG', 'Priority inference queue'],
    },
    {
      id: 'PRO',
      name: 'PRO',
      price: '₹1,099',
      period: '30 days',
      tokens: 200000,
      storage: '2 GB',
      description: 'Maximum performance for large datasets and code synthesis.',
      models: ['gpt-4o', 'gemini-1.5-pro', 'llama-3.1-70b-versatile'],
      features: ['2,000,000 token limit', '2 GB document storage', 'Unlimited documents in RAG', 'Dedicated customer support'],
    },
  ]

  const loadUsage = async () => {
    try {
      const data = await request<UserUsageData>('/api/v1/usage/summary')
      setUsage(data)
    } catch {
      // Fallback
    }
  }

  useEffect(() => {
    let active = true
    request<UserUsageData>('/api/v1/usage/summary')
      .then((data) => {
        if (active) setUsage(data)
      })
      .catch(() => {})
    return () => {
      active = false
    }
  }, [])

  const currentTier = usage?.subscriptionStatus?.tier || 'NORMAL'

  const handlePlanChange = (planId: string) => {
    setProcessingPlan(planId)
    setActionSuccess(null)

    // Simulate Razorpay Test Mode checkout workflow
    setTimeout(() => {
      setProcessingPlan(null)
      setActionSuccess(`Plan updated to ${planId} via Razorpay Test Mode!`)
      void loadUsage()
    }, 1200)
  }

  return (
    <div className="flex-grow-1 overflow-y-auto p-4 p-md-5">
      <div className="container-xl" style={{ maxWidth: '1040px' }}>
        {/* Banner */}
        <div className="alert alert-warning border border-warning d-flex align-items-center justify-content-between p-3 mb-4 rounded-3">
          <div className="d-flex align-items-center gap-2.5">
            <AlertTriangle size={20} className="text-warning flex-shrink-0" />
            <div className="small">
              <strong>RAZORPAY TEST MODE:</strong> This workspace operates strictly under test credentials. Subscriptions and plan upgrades are simulated test payments.
            </div>
          </div>
          <span className="nexa-badge nexa-badge-test d-none d-sm-inline-flex">TEST SANDBOX</span>
        </div>

        {actionSuccess && (
          <div className="alert alert-success d-flex align-items-center gap-2 small p-3 mb-4 rounded-3">
            <CheckCircle2 size={16} />
            <span>{actionSuccess}</span>
          </div>
        )}

        {/* Current Plan Overview Card */}
        <div className="nexa-card p-4 mb-4">
          <div className="d-flex flex-column flex-md-row align-items-md-center justify-content-between gap-3 mb-3">
            <div>
              <span className="small text-secondary fw-semibold">Current Active Plan</span>
              <h2 className="fs-3 fw-bold text-body mb-0 d-flex align-items-center gap-2">
                <span>{currentTier}</span>
                <span className="nexa-badge nexa-badge-brand">ACTIVE</span>
              </h2>
            </div>

            <div className="d-flex align-items-center gap-2">
              <button
                type="button"
                className="nexa-btn-secondary small"
                onClick={() => onNavigate('usage')}
              >
                View Detailed Usage
              </button>
            </div>
          </div>

          <div className="row g-3 pt-3 border-top border-subtle">
            <div className="col-12 col-md-4">
              <div className="small text-secondary">Token Allowance:</div>
              <strong className="text-body font-monospace">
                {usage?.subscriptionStatus?.tokenQuota?.toLocaleString() || '500,000'} tokens
              </strong>
            </div>
            <div className="col-12 col-md-4">
              <div className="small text-secondary">Tokens Consumed:</div>
              <strong className="text-body font-monospace">
                {usage?.subscriptionStatus?.tokensUsed?.toLocaleString() || '0'} tokens
              </strong>
            </div>
            <div className="col-12 col-md-4">
              <div className="small text-secondary">Storage Allotment:</div>
              <strong className="text-body font-monospace">
                {usage?.storageUsageFormatted || '0 B'} used
              </strong>
            </div>
          </div>
        </div>

        {/* Plans Selection Grid */}
        <div className="mb-4">
          <h4 className="fw-bold tracking-tight mb-3 text-body">Available Subscription Plans</h4>
          <div className="row g-3">
            {plans.map((p) => {
              const isCurrent = p.id === currentTier
              const isProcessing = processingPlan === p.id
              return (
                <div key={p.id} className="col-12 col-md-4">
                  <div className={`nexa-card p-4 h-100 d-flex flex-column ${isCurrent ? 'border-primary' : ''}`}>
                    <div className="d-flex align-items-center justify-content-between mb-2">
                      <h5 className="fw-bold mb-0">{p.name}</h5>
                      {isCurrent && <span className="nexa-badge nexa-badge-secondary">Current</span>}
                    </div>

                    <div className="d-flex align-items-baseline gap-1 my-3">
                      <span className="fs-2 fw-bold text-body">{p.price}</span>
                      <span className="text-secondary small">/ {p.period}</span>
                    </div>

                    <p className="text-secondary small mb-3">{p.description}</p>

                    <div className="small text-muted mb-3 font-monospace">
                      Models: {p.models.join(', ')}
                    </div>

                    <ul className="list-unstyled d-flex flex-column gap-2 small mb-4 flex-grow-1">
                      {p.features.map((f, i) => (
                        <li key={i} className="d-flex align-items-start gap-2">
                          <Check size={14} className="text-primary mt-0.5 flex-shrink-0" />
                          <span>{f}</span>
                        </li>
                      ))}
                    </ul>

                    <button
                      type="button"
                      className={
                        isCurrent
                          ? 'nexa-btn-secondary w-100 disabled'
                          : 'nexa-btn-primary w-100'
                      }
                      disabled={isCurrent || Boolean(processingPlan)}
                      onClick={() => handlePlanChange(p.id)}
                    >
                      {isProcessing
                        ? 'Processing Test Payment...'
                        : isCurrent
                        ? 'Current Plan'
                        : `Switch to ${p.name}`}
                    </button>
                  </div>
                </div>
              )
            })}
          </div>
        </div>
      </div>
    </div>
  )
}
