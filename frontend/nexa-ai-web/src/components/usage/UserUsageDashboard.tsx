import { useState, useEffect } from 'react'
import { Activity, FileText, Cpu, AlertCircle, RefreshCw } from 'lucide-react'
import type { UserUsageData } from '../../types/index.ts'

export function UserUsageDashboard() {
  const [usage, setUsage] = useState<UserUsageData | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const fetchUsage = async () => {
    setLoading(true)
    setError(null)
    try {
      const token = localStorage.getItem('nexa_access_token') || localStorage.getItem('access_token') || sessionStorage.getItem('access_token')
      const res = await fetch('/api/v1/usage/summary', {
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: `Bearer ${token}` } : {})
        }
      })
      if (!res.ok) {
        throw new Error(`Failed to load usage data: ${res.statusText}`)
      }
      const data: UserUsageData = await res.json()
      setUsage(data)
    } catch (err: any) {
      setError(err.message || 'Error fetching usage')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchUsage()
  }, [])

  if (loading) {
    return (
      <div className="d-flex flex-grow-1 align-items-center justify-content-center">
        <div className="spinner-border text-primary" role="status">
          <span className="visually-hidden">Loading usage data...</span>
        </div>
      </div>
    )
  }

  if (error || !usage) {
    return (
      <div className="container py-4">
        <div className="alert alert-danger d-flex align-items-center justify-content-between">
          <div className="d-flex align-items-center gap-2">
            <AlertCircle size={20} />
            <span>{error || 'Unable to load real persisted usage data.'}</span>
          </div>
          <button className="btn btn-sm btn-outline-danger" onClick={fetchUsage}>
            <RefreshCw size={14} className="me-1" /> Retry
          </button>
        </div>
      </div>
    )
  }

  const quotaPercent = usage.subscriptionStatus.tokenQuota > 0
    ? Math.min(100, Math.round((usage.subscriptionStatus.tokensUsed / usage.subscriptionStatus.tokenQuota) * 100))
    : 0

  return (
    <div className="container-fluid py-4 px-4 overflow-y-auto">
      <div className="d-flex justify-content-between align-items-center mb-4">
        <div className="d-flex align-items-center gap-3">
          <img
            src="/assets/bot-usage-insights.png"
            alt="Usage Insights"
            width={52}
            height={66}
            className="rounded-3 shadow-sm border border-subtle d-none d-sm-block flex-shrink-0"
            style={{ objectFit: 'contain', backgroundColor: 'var(--surface-hover)' }}
          />
          <div>
            <h4 className="fw-bold mb-1">Your Account Usage & Telemetry</h4>
            <p className="text-secondary small mb-0">Real persisted usage metrics, token consumption, and storage tracking.</p>
          </div>
        </div>
        <button className="btn btn-sm btn-outline-secondary d-flex align-items-center gap-1" onClick={fetchUsage}>
          <RefreshCw size={14} /> Refresh
        </button>
      </div>

      {/* KPI Cards */}
      <div className="row g-3 mb-4">
        <div className="col-md-3">
          <div className="card h-100 border shadow-sm">
            <div className="card-body">
              <div className="d-flex justify-content-between align-items-center mb-2">
                <span className="text-secondary small fw-semibold">AI REQUESTS</span>
                <div className="badge bg-primary-subtle text-primary p-2 rounded-2">
                  <Activity size={16} />
                </div>
              </div>
              <h3 className="fw-bold mb-1">{usage.aiRequests.toLocaleString()}</h3>
              <span className="text-secondary small">Total chat inferences</span>
            </div>
          </div>
        </div>

        <div className="col-md-3">
          <div className="card h-100 border shadow-sm">
            <div className="card-body">
              <div className="d-flex justify-content-between align-items-center mb-2">
                <span className="text-secondary small fw-semibold">TOTAL TOKENS</span>
                <div className="badge bg-success-subtle text-success p-2 rounded-2">
                  <Cpu size={16} />
                </div>
              </div>
              <h3 className="fw-bold mb-1">{usage.tokenUsage.totalTokens.toLocaleString()}</h3>
              <div className="text-secondary small d-flex gap-2">
                <span>Prompt: {usage.tokenUsage.promptTokens.toLocaleString()}</span>
                <span>•</span>
                <span>Compl: {usage.tokenUsage.completionTokens.toLocaleString()}</span>
              </div>
            </div>
          </div>
        </div>

        <div className="col-md-3">
          <div className="card h-100 border shadow-sm">
            <div className="card-body">
              <div className="d-flex justify-content-between align-items-center mb-2">
                <span className="text-secondary small fw-semibold">DOCUMENTS & STORAGE</span>
                <div className="badge bg-info-subtle text-info p-2 rounded-2">
                  <FileText size={16} />
                </div>
              </div>
              <h3 className="fw-bold mb-1">{usage.documentCount} docs</h3>
              <span className="text-secondary small">{usage.storageUsageFormatted} total stored</span>
            </div>
          </div>
        </div>

        <div className="col-md-3">
          <div className="card h-100 border shadow-sm">
            <div className="card-body">
              <div className="d-flex justify-content-between align-items-center mb-2">
                <span className="text-secondary small fw-semibold">SUBSCRIPTION & QUOTA</span>
                <span className="badge text-bg-primary">{usage.subscriptionStatus.tier}</span>
              </div>
              <h3 className="fw-bold mb-1">{usage.remainingQuota.toLocaleString()}</h3>
              <span className="text-secondary small">Remaining token quota</span>
              <div className="progress mt-2" style={{ height: '6px' }}>
                <div
                  className={`progress-bar ${quotaPercent > 85 ? 'bg-danger' : 'bg-primary'}`}
                  style={{ width: `${quotaPercent}%` }}
                />
              </div>
            </div>
          </div>
        </div>
      </div>

      <div className="row g-3 mb-4">
        {/* Model Usage Breakdown */}
        <div className="col-md-6">
          <div className="card border shadow-sm h-100">
            <div className="card-header bg-transparent py-3">
              <h6 className="fw-bold mb-0">Model Consumption Breakdown</h6>
            </div>
            <div className="card-body p-0">
              <div className="table-responsive">
                <table className="table table-hover align-middle mb-0">
                  <thead className="table-light small">
                    <tr>
                      <th>Model</th>
                      <th>Provider</th>
                      <th>Requests</th>
                      <th>Tokens</th>
                      <th>Est. Cost</th>
                    </tr>
                  </thead>
                  <tbody>
                    {usage.modelUsage.length === 0 ? (
                      <tr>
                        <td colSpan={5} className="text-center py-4 text-secondary">
                          No model requests recorded yet.
                        </td>
                      </tr>
                    ) : (
                      usage.modelUsage.map((m) => (
                        <tr key={m.model}>
                          <td className="fw-semibold">{m.model}</td>
                          <td><span className="badge text-bg-light border">{m.provider}</span></td>
                          <td>{m.requests.toLocaleString()}</td>
                          <td>{m.tokens.toLocaleString()}</td>
                          <td className="text-success fw-semibold">${Number(m.estimatedCostUsd).toFixed(4)}</td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </div>

        {/* Daily Usage History */}
        <div className="col-md-6">
          <div className="card border shadow-sm h-100">
            <div className="card-header bg-transparent py-3 d-flex justify-content-between align-items-center">
              <h6 className="fw-bold mb-0">Daily Usage History (Last 14 Days)</h6>
              <span className="badge bg-secondary-subtle text-secondary">
                Month: {usage.monthlyUsage.currentMonthRequests} reqs / {usage.monthlyUsage.currentMonthTokens.toLocaleString()} tokens
              </span>
            </div>
            <div className="card-body p-0">
              <div className="table-responsive" style={{ maxHeight: '280px' }}>
                <table className="table table-hover align-middle mb-0">
                  <thead className="table-light small sticky-top">
                    <tr>
                      <th>Date</th>
                      <th>Requests</th>
                      <th>Tokens</th>
                      <th>Est. Cost</th>
                    </tr>
                  </thead>
                  <tbody>
                    {usage.dailyUsage.map((d) => (
                      <tr key={d.date}>
                        <td>{d.date}</td>
                        <td>{d.requests}</td>
                        <td>{d.tokens.toLocaleString()}</td>
                        <td className="text-secondary">${Number(d.estimatedCostUsd).toFixed(4)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
