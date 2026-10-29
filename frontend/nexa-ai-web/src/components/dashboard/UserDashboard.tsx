import { useState, useEffect } from 'react'
import {
  MessageSquare, FileText, Sparkles, TrendingUp, RefreshCw, AlertCircle
} from 'lucide-react'
import { useAuth } from '../../context/AuthContext.tsx'
import { chatService } from '../../services/chatService.ts'
import { request } from '../../services/apiClient.ts'
import type { Conversation, UserUsageData, AppRoute } from '../../types/index.ts'

interface UserDashboardProps {
  readonly onNavigate: (route: AppRoute) => void
  readonly onSelectConversation?: (id: string) => void
}

export function UserDashboard({ onNavigate, onSelectConversation }: UserDashboardProps) {
  const { user } = useAuth()
  const [usage, setUsage] = useState<UserUsageData | null>(null)
  const [recentConversations, setRecentConversations] = useState<Conversation[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const quickStartPrompts = [
    {
      title: 'Analyze Document Architecture',
      prompt: 'Extract key microservices dependencies and API boundaries from our architecture specification.',
      category: 'RAG Analysis',
    },
    {
      title: 'Draft Technical RFC / ADR',
      prompt: 'Draft an architectural decision record comparing event-driven Kafka KRaft versus Redis Streams.',
      category: 'Design',
    },
    {
      title: 'Explain Complex Algorithms',
      prompt: 'Explain the HNSW index algorithm used in pgvector vector similarity search with code examples.',
      category: 'Code & Math',
    },
  ]

  const loadData = async () => {
    setLoading(true)
    setError(null)
    try {
      const [usageData, convList] = await Promise.all([
        request<UserUsageData>('/api/v1/usage/summary'),
        chatService.listConversations(false),
      ])
      setUsage(usageData)
      setRecentConversations(convList.slice(0, 5))
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to load usage statistics.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void loadData()
  }, [])

  const handleStartPrompt = async (promptText: string) => {
    try {
      const conv = await chatService.createConversation(promptText.slice(0, 30), 'gpt-4o-mini')
      if (onSelectConversation) {
        onSelectConversation(conv.id)
      }
      onNavigate('chat')
    } catch {
      onNavigate('chat')
    }
  }

  return (
    <div className="flex-grow-1 overflow-y-auto p-4 p-md-5">
      <div className="container-xl" style={{ maxWidth: '1080px' }}>
        {/* Welcome Back Header */}
        <div className="d-flex flex-column flex-md-row align-items-md-center justify-content-between gap-3 mb-4">
          <div className="d-flex align-items-center gap-3">
            <img
              src="/assets/bot-hi.png"
              alt="NexaAI Assistant"
              width={52}
              height={72}
              className="rounded-3 shadow-sm border border-subtle d-none d-sm-block flex-shrink-0"
              style={{ objectFit: 'contain', backgroundColor: 'var(--surface-hover)' }}
            />
            <div>
              <div className="d-inline-flex align-items-center gap-1.5 nexa-badge nexa-badge-brand mb-1.5">
                <Sparkles size={13} />
                <span>Workspace Dashboard</span>
              </div>
              <h1 className="fs-3 fw-bold tracking-tight text-body mb-1">
                Welcome back, {user?.displayName || 'User'}
              </h1>
              <p className="text-secondary small mb-0">
                Overview of your AI usage, documents, and active conversations.
              </p>
            </div>
          </div>

          <div className="d-flex align-items-center gap-2">
            <button
              type="button"
              className="nexa-btn-secondary py-1.5 px-3 small"
              onClick={loadData}
              disabled={loading}
              title="Refresh usage statistics"
            >
              <RefreshCw size={14} className={loading ? 'spin' : ''} />
              <span>Refresh</span>
            </button>
            <button
              type="button"
              className="nexa-btn-primary py-1.5 px-3 small"
              onClick={() => onNavigate('chat')}
            >
              <MessageSquare size={14} />
              <span>Open Chat</span>
            </button>
          </div>
        </div>

        {error && (
          <div className="alert alert-warning d-flex align-items-center justify-content-between small p-3 mb-4 rounded-3">
            <div className="d-flex align-items-center gap-2">
              <AlertCircle size={16} />
              <span>{error}</span>
            </div>
            <button type="button" className="btn btn-sm btn-link p-0 text-decoration-none" onClick={loadData}>
              Retry
            </button>
          </div>
        )}

        {/* 4 Metric Cards: Messages used, Token usage, Documents, Current plan */}
        <div className="row g-3 mb-4">
          {/* Messages / AI Requests */}
          <div className="col-6 col-lg-3">
            <div className="nexa-card p-3.5 h-100">
              <div className="d-flex align-items-center justify-content-between text-secondary mb-2">
                <span className="small fw-semibold">Messages Used</span>
                <MessageSquare size={16} style={{ color: 'var(--brand-primary)' }} />
              </div>
              <div className="fs-3 fw-bold text-body font-monospace mb-1">
                {loading ? '—' : usage?.aiRequests?.toLocaleString() || '0'}
              </div>
              <div className="text-muted small" style={{ fontSize: '0.75rem' }}>
                AI requests completed
              </div>
            </div>
          </div>

          {/* Token Usage */}
          <div className="col-6 col-lg-3">
            <div className="nexa-card p-3.5 h-100">
              <div className="d-flex align-items-center justify-content-between text-secondary mb-2">
                <span className="small fw-semibold">Token Usage</span>
                <TrendingUp size={16} style={{ color: 'var(--brand-secondary)' }} />
              </div>
              <div className="fs-3 fw-bold text-body font-monospace mb-1">
                {loading ? '—' : usage?.tokenUsage?.totalTokens?.toLocaleString() || '0'}
              </div>
              <div className="text-muted small" style={{ fontSize: '0.75rem' }}>
                {usage ? `${usage.tokenUsage.promptTokens.toLocaleString()} in / ${usage.tokenUsage.completionTokens.toLocaleString()} out` : 'Tokens'}
              </div>
            </div>
          </div>

          {/* Documents */}
          <div className="col-6 col-lg-3">
            <div className="nexa-card p-3.5 h-100">
              <div className="d-flex align-items-center justify-content-between text-secondary mb-2">
                <span className="small fw-semibold">Documents</span>
                <FileText size={16} style={{ color: '#10b981' }} />
              </div>
              <div className="fs-3 fw-bold text-body font-monospace mb-1">
                {loading ? '—' : usage?.documentCount || '0'}
              </div>
              <div className="text-muted small" style={{ fontSize: '0.75rem' }}>
                {usage?.storageUsageFormatted || '0 B storage'}
              </div>
            </div>
          </div>

          {/* Current Plan */}
          <div className="col-6 col-lg-3">
            <div className="nexa-card p-3.5 h-100">
              <div className="d-flex align-items-center justify-content-between text-secondary mb-2">
                <span className="small fw-semibold">Current Plan</span>
                <Sparkles size={16} style={{ color: 'var(--brand-primary)' }} />
              </div>
              <div className="fs-4 fw-bold text-body mb-1">
                {loading ? '—' : usage?.subscriptionStatus?.tier || 'NORMAL'}
              </div>
              <button
                type="button"
                className="btn btn-link p-0 text-decoration-none small text-brand"
                style={{ fontSize: '0.75rem', color: 'var(--brand-primary)' }}
                onClick={() => onNavigate('billing')}
              >
                Manage plan & quota →
              </button>
            </div>
          </div>
        </div>

        {/* Quota Progress Bar */}
        {usage?.subscriptionStatus && (
          <div className="nexa-card p-3.5 mb-4">
            <div className="d-flex align-items-center justify-content-between mb-2 small">
              <span className="fw-semibold text-body">Monthly Token Quota</span>
              <span className="text-secondary font-monospace">
                {usage.subscriptionStatus.tokensUsed?.toLocaleString()} / {usage.subscriptionStatus.tokenQuota?.toLocaleString()} tokens
              </span>
            </div>
            <div className="progress" style={{ height: '7px', backgroundColor: 'var(--surface-hover)' }}>
              <div
                className="progress-bar"
                style={{
                  width: `${Math.min(
                    100,
                    Math.round(
                      ((usage.subscriptionStatus.tokensUsed || 0) /
                        (usage.subscriptionStatus.tokenQuota || 1)) *
                        100
                    )
                  )}%`,
                  backgroundColor: 'var(--brand-primary)',
                }}
              />
            </div>
            <div className="d-flex justify-content-between text-muted small mt-1.5" style={{ fontSize: '0.75rem' }}>
              <span>Remaining: {usage.remainingQuota?.toLocaleString()} tokens</span>
              <span>Status: {usage.subscriptionStatus.status}</span>
            </div>
          </div>
        )}

        <div className="row g-4 mb-4">
          {/* Recent Conversations */}
          <div className="col-12 col-lg-7">
            <div className="nexa-card p-4 h-100">
              <div className="d-flex align-items-center justify-content-between mb-3">
                <h5 className="fs-6 fw-bold mb-0 text-body">Recent Conversations</h5>
                <button
                  type="button"
                  className="btn btn-link p-0 small text-decoration-none"
                  style={{ color: 'var(--brand-primary)' }}
                  onClick={() => onNavigate('chat')}
                >
                  View all ({recentConversations.length})
                </button>
              </div>

              {recentConversations.length === 0 ? (
                <div className="text-center py-4 text-muted small">
                  No conversations yet. Start a new chat to begin exploring!
                </div>
              ) : (
                <div className="d-flex flex-column gap-2">
                  {recentConversations.map((c) => (
                    <div
                      key={c.id}
                      className="p-2.5 rounded bg-surface border border-subtle d-flex align-items-center justify-content-between"
                      style={{ cursor: 'pointer' }}
                      onClick={() => {
                        if (onSelectConversation) onSelectConversation(c.id)
                        onNavigate('chat')
                      }}
                    >
                      <div className="d-flex align-items-center gap-2.5 text-truncate me-2">
                        <MessageSquare size={15} className="text-secondary flex-shrink-0" />
                        <span className="small fw-medium text-body text-truncate">{c.title}</span>
                      </div>
                      <div className="d-flex align-items-center gap-2 flex-shrink-0">
                        <span className="nexa-badge nexa-badge-subtle font-monospace" style={{ fontSize: '0.7rem' }}>
                          {c.model || 'gpt-4o-mini'}
                        </span>
                        <span className="text-muted small" style={{ fontSize: '0.7rem' }}>
                          {new Date(c.updatedAt).toLocaleDateString()}
                        </span>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>

          {/* Quick-Start Prompts */}
          <div className="col-12 col-lg-5">
            <div className="nexa-card p-4 h-100">
              <h5 className="fs-6 fw-bold mb-3 text-body">Quick-Start Prompts</h5>
              <div className="d-flex flex-column gap-2.5">
                {quickStartPrompts.map((q, i) => (
                  <button
                    key={i}
                    type="button"
                    className="btn text-start p-2.5 rounded bg-surface border border-subtle d-flex flex-column gap-1 transition"
                    onClick={() => handleStartPrompt(q.prompt)}
                  >
                    <div className="d-flex align-items-center justify-content-between">
                      <span className="small fw-semibold text-body">{q.title}</span>
                      <span className="nexa-badge nexa-badge-brand" style={{ fontSize: '0.68rem' }}>
                        {q.category}
                      </span>
                    </div>
                    <div className="text-secondary small text-truncate" style={{ fontSize: '0.78rem' }}>
                      {q.prompt}
                    </div>
                  </button>
                ))}
              </div>
            </div>
          </div>
        </div>

        {/* Model Usage Summary */}
        {usage?.modelUsage && usage.modelUsage.length > 0 && (
          <div className="nexa-card p-4">
            <h5 className="fs-6 fw-bold mb-3 text-body">Model Utilization Breakdown</h5>
            <div className="nexa-table-container">
              <table className="nexa-table">
                <thead>
                  <tr>
                    <th>Model</th>
                    <th>Provider</th>
                    <th className="text-end">Requests</th>
                    <th className="text-end">Tokens</th>
                    <th className="text-end">Est. Cost (USD)</th>
                  </tr>
                </thead>
                <tbody>
                  {usage.modelUsage.map((m) => (
                    <tr key={m.model}>
                      <td className="font-monospace fw-semibold">{m.model}</td>
                      <td><span className="nexa-badge nexa-badge-subtle">{m.provider}</span></td>
                      <td className="text-end font-monospace">{m.requests.toLocaleString()}</td>
                      <td className="text-end font-monospace">{m.tokens.toLocaleString()}</td>
                      <td className="text-end font-monospace text-secondary">${m.estimatedCostUsd.toFixed(4)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
