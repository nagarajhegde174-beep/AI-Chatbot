import { useState, useEffect } from 'react'
import {
  RefreshCw, AlertTriangle, ShieldCheck,
  ToggleLeft, ToggleRight, Trash2, RotateCw, Search, BarChart3
} from 'lucide-react'
import type { AdminOverviewData } from '../../types/index.ts'

type AdminTab = 'overview' | 'users' | 'conversations' | 'models' | 'rag' | 'analytics'

export function AdminPortal() {
  const [activeTab, setActiveTab] = useState<AdminTab>('overview')
  const [overview, setOverview] = useState<AdminOverviewData | null>(null)
  const [users, setUsers] = useState<any[]>([])
  const [userSearch, setUserSearch] = useState('')
  const [conversations, setConversations] = useState<any | null>(null)
  const [models, setModels] = useState<any[]>([])
  const [ragData, setRagData] = useState<any | null>(null)
  const [analyticsData, setAnalyticsData] = useState<any | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const getHeaders = () => {
    const token = localStorage.getItem('access_token') || sessionStorage.getItem('access_token')
    return {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    }
  }

  const fetchOverview = async () => {
    try {
      const res = await fetch('/api/v1/admin/overview', { headers: getHeaders() })
      if (res.ok) setOverview(await res.json())
    } catch (e: any) {
      setError(e.message)
    }
  }

  const fetchUsers = async () => {
    try {
      const q = userSearch ? `?query=${encodeURIComponent(userSearch)}` : ''
      const res = await fetch(`/api/v1/admin/users${q}`, { headers: getHeaders() })
      if (res.ok) setUsers(await res.json())
    } catch (e: any) {
      setError(e.message)
    }
  }

  const fetchConversations = async () => {
    try {
      const res = await fetch('/api/v1/admin/conversations/monitoring', { headers: getHeaders() })
      if (res.ok) setConversations(await res.json())
    } catch (e: any) {
      setError(e.message)
    }
  }

  const fetchModels = async () => {
    try {
      const res = await fetch('/api/v1/admin/models', { headers: getHeaders() })
      if (res.ok) setModels(await res.json())
    } catch (e: any) {
      setError(e.message)
    }
  }

  const fetchRag = async () => {
    try {
      const res = await fetch('/api/v1/admin/rag/monitoring', { headers: getHeaders() })
      if (res.ok) setRagData(await res.json())
    } catch (e: any) {
      setError(e.message)
    }
  }

  const fetchAnalytics = async () => {
    try {
      const res = await fetch('/api/v1/admin/analytics', { headers: getHeaders() })
      if (res.ok) setAnalyticsData(await res.json())
    } catch (e: any) {
      setError(e.message)
    }
  }

  const loadTabData = (tab: AdminTab) => {
    setLoading(true)
    setError(null)
    const p = (tab === 'overview') ? fetchOverview()
      : (tab === 'users') ? fetchUsers()
      : (tab === 'conversations') ? fetchConversations()
      : (tab === 'models') ? fetchModels()
      : (tab === 'rag') ? fetchRag()
      : fetchAnalytics()

    p.finally(() => setLoading(false))
  }

  useEffect(() => {
    loadTabData(activeTab)
  }, [activeTab])

  const handleUpdateUserStatus = async (userId: string, newStatus: string) => {
    try {
      const res = await fetch(`/api/v1/admin/users/${userId}/status`, {
        method: 'POST',
        headers: getHeaders(),
        body: JSON.stringify({ status: newStatus })
      })
      if (res.ok) fetchUsers()
    } catch (e: any) {
      setError(e.message)
    }
  }

  const handleToggleModel = async (modelName: string, currentEnabled: boolean) => {
    try {
      const res = await fetch(`/api/v1/admin/models/${encodeURIComponent(modelName)}/toggle`, {
        method: 'POST',
        headers: getHeaders(),
        body: JSON.stringify({ enabled: !currentEnabled })
      })
      if (res.ok) fetchModels()
    } catch (e: any) {
      setError(e.message)
    }
  }

  const handleSetDefaultModel = async (modelName: string) => {
    try {
      const res = await fetch(`/api/v1/admin/models/${encodeURIComponent(modelName)}/set-default`, {
        method: 'POST',
        headers: getHeaders()
      })
      if (res.ok) fetchModels()
    } catch (e: any) {
      setError(e.message)
    }
  }

  const handleReprocessDoc = async (docId: string) => {
    try {
      const res = await fetch(`/api/v1/admin/rag/documents/${docId}/reprocess`, {
        method: 'POST',
        headers: getHeaders()
      })
      if (res.ok) fetchRag()
    } catch (e: any) {
      setError(e.message)
    }
  }

  const handleDeleteDoc = async (docId: string) => {
    if (!window.confirm('Are you sure you want to delete this document read model?')) return
    try {
      const res = await fetch(`/api/v1/admin/rag/documents/${docId}`, {
        method: 'DELETE',
        headers: getHeaders()
      })
      if (res.ok) fetchRag()
    } catch (e: any) {
      setError(e.message)
    }
  }

  return (
    <div className="container-fluid py-4 px-4 overflow-y-auto">
      <div className="d-flex justify-content-between align-items-center mb-4">
        <div>
          <h4 className="fw-bold mb-1 d-flex align-items-center gap-2">
            <ShieldCheck className="text-warning" size={24} /> Admin & Analytics Command Center
          </h4>
          <p className="text-secondary small mb-0">Phase 9 real persisted metrics, user controls, conversation audits, and cost analytics.</p>
        </div>
        <button className="btn btn-sm btn-outline-secondary d-flex align-items-center gap-1" onClick={() => loadTabData(activeTab)}>
          <RefreshCw size={14} /> Refresh
        </button>
      </div>

      {error && (
        <div className="alert alert-danger py-2 small mb-3 d-flex align-items-center gap-2">
          <AlertTriangle size={16} />
          <span>{error}</span>
        </div>
      )}

      {/* Admin Tabs Navigation */}
      <ul className="nav nav-tabs mb-4">
        <li className="nav-item">
          <button
            className={`nav-link ${activeTab === 'overview' ? 'active fw-bold' : ''}`}
            onClick={() => setActiveTab('overview')}
          >
            1. Overview
          </button>
        </li>
        <li className="nav-item">
          <button
            className={`nav-link ${activeTab === 'users' ? 'active fw-bold' : ''}`}
            onClick={() => setActiveTab('users')}
          >
            2. Users
          </button>
        </li>
        <li className="nav-item">
          <button
            className={`nav-link ${activeTab === 'conversations' ? 'active fw-bold' : ''}`}
            onClick={() => setActiveTab('conversations')}
          >
            3. Conversations
          </button>
        </li>
        <li className="nav-item">
          <button
            className={`nav-link ${activeTab === 'models' ? 'active fw-bold' : ''}`}
            onClick={() => setActiveTab('models')}
          >
            4. Models
          </button>
        </li>
        <li className="nav-item">
          <button
            className={`nav-link ${activeTab === 'rag' ? 'active fw-bold' : ''}`}
            onClick={() => setActiveTab('rag')}
          >
            5. RAG Pipeline
          </button>
        </li>
        <li className="nav-item">
          <button
            className={`nav-link ${activeTab === 'analytics' ? 'active fw-bold' : ''}`}
            onClick={() => setActiveTab('analytics')}
          >
            6. Cost & Funnel
          </button>
        </li>
      </ul>

      {loading && (
        <div className="py-5 text-center text-secondary">
          <div className="spinner-border spinner-border-sm me-2" role="status" />
          Loading persisted data...
        </div>
      )}

      {/* 1. OVERVIEW TAB */}
      {!loading && activeTab === 'overview' && overview && (
        <div>
          <div className="row g-3 mb-4">
            <div className="col-md-3">
              <div className="card shadow-sm border p-3">
                <span className="text-secondary small fw-semibold">USERS (TOTAL / ACTIVE)</span>
                <h3 className="fw-bold my-1">{overview.totalUsers} / <span className="text-success">{overview.activeUsers}</span></h3>
                <span className="text-secondary small">Active in last 7 days</span>
              </div>
            </div>
            <div className="col-md-3">
              <div className="card shadow-sm border p-3">
                <span className="text-secondary small fw-semibold">TOTAL AI REQUESTS</span>
                <h3 className="fw-bold my-1">{overview.totalAiRequests.toLocaleString()}</h3>
                <span className="text-secondary small">{overview.successfulRequests} success • {overview.failedRequests} errors</span>
              </div>
            </div>
            <div className="col-md-3">
              <div className="card shadow-sm border p-3">
                <span className="text-secondary small fw-semibold">TOTAL TOKENS</span>
                <h3 className="fw-bold my-1">{overview.totalTokens.toLocaleString()}</h3>
                <span className="text-secondary small">Error Rate: {overview.errorRatePercent}%</span>
              </div>
            </div>
            <div className="col-md-3">
              <div className="card shadow-sm border p-3">
                <span className="text-secondary small fw-semibold">ESTIMATED AI COST</span>
                <h3 className="fw-bold my-1 text-success">${Number(overview.totalEstimatedAiCostUsd).toFixed(4)}</h3>
                <span className="text-secondary small">Provider execution cost (not Razorpay)</span>
              </div>
            </div>
          </div>

          <div className="card border shadow-sm p-4">
            <h6 className="fw-bold mb-3">Subscription Distribution</h6>
            <div className="row g-3">
              {Object.entries(overview.subscriptionStatistics).map(([tier, count]) => (
                <div key={tier} className="col-md-4">
                  <div className="p-3 bg-body-tertiary rounded border">
                    <span className="fw-bold">{tier}</span>
                    <h4 className="fw-bold my-1">{count} users</h4>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* 2. USER MANAGEMENT TAB */}
      {!loading && activeTab === 'users' && (
        <div className="card border shadow-sm">
          <div className="card-header bg-transparent py-3 d-flex justify-content-between align-items-center">
            <h6 className="fw-bold mb-0">Registered Users & Activity</h6>
            <div className="d-flex gap-2">
              <input
                type="text"
                className="form-control form-control-sm"
                placeholder="Search email..."
                value={userSearch}
                onChange={(e) => setUserSearch(e.target.value)}
              />
              <button className="btn btn-sm btn-outline-primary" onClick={fetchUsers}>
                <Search size={14} />
              </button>
            </div>
          </div>
          <div className="table-responsive">
            <table className="table table-hover align-middle mb-0">
              <thead className="table-light small">
                <tr>
                  <th>User ID / Email</th>
                  <th>Status</th>
                  <th>Tier</th>
                  <th>Tokens Used</th>
                  <th>Requests</th>
                  <th>Last Login</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {users.map((u) => (
                  <tr key={u.userId}>
                    <td>
                      <div className="fw-semibold">{u.email || 'Anonymous'}</div>
                      <span className="text-secondary small font-monospace">{u.userId}</span>
                    </td>
                    <td>
                      <span className={`badge ${u.status === 'ACTIVE' ? 'bg-success' : u.status === 'SUSPENDED' ? 'bg-warning text-dark' : 'bg-danger'}`}>
                        {u.status}
                      </span>
                    </td>
                    <td><span className="badge text-bg-light border">{u.subscriptionTier}</span></td>
                    <td>{Number(u.tokensUsed).toLocaleString()}</td>
                    <td>{u.totalRequests}</td>
                    <td className="small text-secondary">{u.lastLoginAt ? new Date(u.lastLoginAt).toLocaleString() : 'Never'}</td>
                    <td>
                      <div className="btn-group btn-group-sm">
                        {u.status !== 'ACTIVE' && (
                          <button className="btn btn-outline-success" onClick={() => handleUpdateUserStatus(u.userId, 'ACTIVE')}>
                            Activate
                          </button>
                        )}
                        {u.status !== 'SUSPENDED' && (
                          <button className="btn btn-outline-warning" onClick={() => handleUpdateUserStatus(u.userId, 'SUSPENDED')}>
                            Suspend
                          </button>
                        )}
                        {u.status !== 'DEACTIVATED' && (
                          <button className="btn btn-outline-danger" onClick={() => handleUpdateUserStatus(u.userId, 'DEACTIVATED')}>
                            Deactivate
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* 3. CONVERSATION MONITORING TAB */}
      {!loading && activeTab === 'conversations' && conversations && (
        <div>
          <div className="row g-3 mb-4">
            <div className="col-md-3">
              <div className="card shadow-sm border p-3">
                <span className="text-secondary small fw-semibold">TODAY'S QUERIES</span>
                <h3 className="fw-bold my-1 text-primary">{conversations.todayQueries}</h3>
              </div>
            </div>
            <div className="col-md-3">
              <div className="card shadow-sm border p-3">
                <span className="text-secondary small fw-semibold">YESTERDAY'S QUERIES</span>
                <h3 className="fw-bold my-1">{conversations.yesterdayQueries}</h3>
              </div>
            </div>
            <div className="col-md-3">
              <div className="card shadow-sm border p-3">
                <span className="text-secondary small fw-semibold">OLDER QUERIES</span>
                <h3 className="fw-bold my-1 text-secondary">{conversations.olderQueries}</h3>
              </div>
            </div>
            <div className="col-md-3">
              <div className="card shadow-sm border p-3">
                <span className="text-secondary small fw-semibold">TOTAL QUERIES</span>
                <h3 className="fw-bold my-1">{conversations.totalQueries}</h3>
              </div>
            </div>
          </div>

          <div className="card border shadow-sm">
            <div className="card-header bg-transparent py-3">
              <h6 className="fw-bold mb-0">User-Wise Query Monitoring (Privacy Respected: Aggregated Counts & Metrics Only)</h6>
            </div>
            <div className="table-responsive">
              <table className="table table-hover align-middle mb-0">
                <thead className="table-light small">
                  <tr>
                    <th>User ID</th>
                    <th>Query Count</th>
                    <th>Total Tokens</th>
                    <th>Avg Latency</th>
                    <th>Last Active</th>
                  </tr>
                </thead>
                <tbody>
                  {conversations.userWiseActivity.map((ua: any) => (
                    <tr key={ua.userId}>
                      <td className="font-monospace small">{ua.userId}</td>
                      <td className="fw-semibold">{ua.queryCount}</td>
                      <td>{Number(ua.totalTokens).toLocaleString()}</td>
                      <td>{ua.avgLatencyMs} ms</td>
                      <td className="small text-secondary">{new Date(ua.lastQueryAt).toLocaleString()}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* 4. MODEL MANAGEMENT TAB */}
      {!loading && activeTab === 'models' && (
        <div className="card border shadow-sm">
          <div className="card-header bg-transparent py-3">
            <h6 className="fw-bold mb-0">Model Registry & Provider Status</h6>
          </div>
          <div className="table-responsive">
            <table className="table table-hover align-middle mb-0">
              <thead className="table-light small">
                <tr>
                  <th>Model</th>
                  <th>Provider</th>
                  <th>Status</th>
                  <th>Default</th>
                  <th>Requests</th>
                  <th>Tokens</th>
                  <th>Errors</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {models.map((m) => (
                  <tr key={m.modelName}>
                    <td className="fw-semibold">{m.modelName}</td>
                    <td><span className="badge text-bg-light border">{m.provider}</span></td>
                    <td>
                      <span className={`badge ${m.status === 'HEALTHY' ? 'bg-success' : m.status === 'DEGRADED' ? 'bg-warning text-dark' : 'bg-secondary'}`}>
                        {m.status}
                      </span>
                    </td>
                    <td>
                      {m.isDefault ? (
                        <span className="badge bg-primary">DEFAULT</span>
                      ) : (
                        <button className="btn btn-sm btn-link p-0 text-decoration-none" onClick={() => handleSetDefaultModel(m.modelName)}>
                          Make Default
                        </button>
                      )}
                    </td>
                    <td>{Number(m.totalRequests).toLocaleString()}</td>
                    <td>{Number(m.totalTokens).toLocaleString()}</td>
                    <td className={m.totalErrors > 0 ? 'text-danger fw-bold' : ''}>{m.totalErrors}</td>
                    <td>
                      <button
                        className={`btn btn-sm ${m.enabled ? 'btn-outline-danger' : 'btn-outline-success'} d-flex align-items-center gap-1`}
                        onClick={() => handleToggleModel(m.modelName, m.enabled)}
                      >
                        {m.enabled ? <ToggleRight size={16} /> : <ToggleLeft size={16} />}
                        {m.enabled ? 'Disable' : 'Enable'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* 5. RAG MONITORING TAB */}
      {!loading && activeTab === 'rag' && ragData && (
        <div>
          <div className="row g-3 mb-4">
            <div className="col-md-3">
              <div className="card shadow-sm border p-3">
                <span className="text-secondary small fw-semibold">TOTAL DOCUMENTS</span>
                <h3 className="fw-bold my-1">{ragData.summary.totalDocuments}</h3>
                <span className="text-secondary small">{ragData.summary.activeDocuments} indexed</span>
              </div>
            </div>
            <div className="col-md-3">
              <div className="card shadow-sm border p-3">
                <span className="text-secondary small fw-semibold">TOTAL CHUNKS</span>
                <h3 className="fw-bold my-1">{ragData.summary.totalChunks.toLocaleString()}</h3>
              </div>
            </div>
            <div className="col-md-3">
              <div className="card shadow-sm border p-3">
                <span className="text-secondary small fw-semibold">TOTAL EMBEDDINGS</span>
                <h3 className="fw-bold my-1">{ragData.summary.totalEmbeddings.toLocaleString()}</h3>
              </div>
            </div>
            <div className="col-md-3">
              <div className="card shadow-sm border p-3">
                <span className="text-secondary small fw-semibold">FAILED DOCUMENTS</span>
                <h3 className={`fw-bold my-1 ${ragData.summary.failureCount > 0 ? 'text-danger' : 'text-success'}`}>
                  {ragData.summary.failureCount}
                </h3>
              </div>
            </div>
          </div>

          <div className="card border shadow-sm">
            <div className="card-header bg-transparent py-3">
              <h6 className="fw-bold mb-0">Documents & Vector Indexing Pipeline</h6>
            </div>
            <div className="table-responsive">
              <table className="table table-hover align-middle mb-0">
                <thead className="table-light small">
                  <tr>
                    <th>Title / Document ID</th>
                    <th>Owner</th>
                    <th>Status</th>
                    <th>Chunks</th>
                    <th>Embeddings</th>
                    <th>Size</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {ragData.documents.map((d: any) => (
                    <tr key={d.documentId}>
                      <td>
                        <div className="fw-semibold">{d.title}</div>
                        <span className="text-secondary small font-monospace">{d.documentId}</span>
                      </td>
                      <td className="font-monospace small text-secondary">{d.userId}</td>
                      <td>
                        <span className={`badge ${d.status === 'INDEXED' ? 'bg-success' : d.status === 'FAILED' ? 'bg-danger' : 'bg-primary'}`}>
                          {d.status}
                        </span>
                      </td>
                      <td>{d.chunkCount}</td>
                      <td>{d.embeddingCount}</td>
                      <td className="small text-secondary">{(d.fileSizeBytes / 1024).toFixed(1)} KB</td>
                      <td>
                        <div className="btn-group btn-group-sm">
                          <button className="btn btn-outline-secondary" title="Reprocess" onClick={() => handleReprocessDoc(d.documentId)}>
                            <RotateCw size={14} />
                          </button>
                          <button className="btn btn-outline-danger" title="Delete" onClick={() => handleDeleteDoc(d.documentId)}>
                            <Trash2 size={14} />
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* 6. ANALYTICS FUNNEL & COST TAB */}
      {!loading && activeTab === 'analytics' && analyticsData && (
        <div>
          <div className="card border shadow-sm p-4 mb-4">
            <h6 className="fw-bold mb-3 d-flex align-items-center gap-2">
              <BarChart3 size={18} /> Request Execution Funnel
            </h6>
            <div className="d-flex align-items-center justify-content-between p-3 bg-body-tertiary rounded border text-center flex-wrap gap-3">
              <div>
                <span className="text-secondary small">1. REQUESTS</span>
                <h5 className="fw-bold mb-0">{analyticsData.totalRequests}</h5>
              </div>
              <span className="text-secondary">→</span>
              <div>
                <span className="text-secondary small">2. PROVIDERS</span>
                <h5 className="fw-bold mb-0">{Object.keys(analyticsData.providerUsage).length}</h5>
              </div>
              <span className="text-secondary">→</span>
              <div>
                <span className="text-secondary small">3. MODELS</span>
                <h5 className="fw-bold mb-0">{Object.keys(analyticsData.modelUsage).length}</h5>
              </div>
              <span className="text-secondary">→</span>
              <div>
                <span className="text-secondary small">4. TOKENS</span>
                <h5 className="fw-bold mb-0">{analyticsData.totalTokens.toLocaleString()}</h5>
              </div>
              <span className="text-secondary">→</span>
              <div>
                <span className="text-secondary small">5. LATENCY</span>
                <h5 className="fw-bold mb-0">{analyticsData.avgLatencyMs} ms</h5>
              </div>
              <span className="text-secondary">→</span>
              <div>
                <span className="text-secondary small">6. EST. COST</span>
                <h5 className="fw-bold mb-0 text-success">${Number(analyticsData.totalEstimatedAiCostUsd).toFixed(4)}</h5>
              </div>
            </div>
          </div>

          <div className="card border shadow-sm">
            <div className="card-header bg-transparent py-3">
              <h6 className="fw-bold mb-0">Recent Request Traces</h6>
            </div>
            <div className="table-responsive">
              <table className="table table-hover align-middle mb-0">
                <thead className="table-light small">
                  <tr>
                    <th>Trace ID</th>
                    <th>Model</th>
                    <th>Tokens</th>
                    <th>Latency</th>
                    <th>Est. Cost</th>
                    <th>Status</th>
                    <th>Timestamp</th>
                  </tr>
                </thead>
                <tbody>
                  {analyticsData.recentTraces.map((t: any) => (
                    <tr key={t.requestId}>
                      <td className="font-monospace small">{t.requestId.substring(0, 8)}...</td>
                      <td><span className="badge text-bg-light border">{t.model}</span></td>
                      <td>{t.totalTokens.toLocaleString()}</td>
                      <td>{t.latencyMs} ms</td>
                      <td className="text-success fw-semibold">${Number(t.estimatedCostUsd).toFixed(5)}</td>
                      <td>
                        <span className={`badge ${t.status === 'SUCCESS' ? 'bg-success' : 'bg-danger'}`}>
                          {t.status}
                        </span>
                      </td>
                      <td className="small text-secondary">{new Date(t.createdAt).toLocaleTimeString()}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
