import { ArrowRight, Database, ShieldCheck, Check, Layers, Zap } from 'lucide-react'
import type { AppRoute } from '../../types/index.ts'

export function LandingPage({
  onNavigate,
  isAuthenticated = false,
}: {
  readonly onNavigate: (route: AppRoute) => void
  readonly isAuthenticated?: boolean
}) {
  const models = [
    {
      provider: 'OpenAI',
      name: 'gpt-4o-mini',
      context: '128k context',
      bestFor: 'High speed, daily reasoning & balanced cost',
      status: 'Active',
    },
    {
      provider: 'OpenAI',
      name: 'gpt-4o',
      context: '128k context',
      bestFor: 'Deep architectural logic & code synthesis',
      status: 'Active',
    },
    {
      provider: 'Gemini',
      name: 'gemini-1.5-flash',
      context: '1M context',
      bestFor: 'Massive document digestion & low latency',
      status: 'Active',
    },
    {
      provider: 'Groq / LLaMA',
      name: 'llama-3.1-70b-versatile',
      context: '128k context',
      bestFor: 'Ultra-fast inference & open-weights architecture',
      status: 'Active',
    },
  ]

  const pricingTiers = [
    {
      name: 'FREE',
      price: '₹0',
      period: 'forever',
      description: 'Ideal for getting started with core chat and model experimentation.',
      features: [
        'Access to gpt-4o-mini & gemini-1.5-flash',
        'Standard token quota (100k tokens)',
        'Up to 3 documents in RAG library',
        'Persistent memory across conversations',
        'Export conversations to Markdown & JSON',
      ],
      ctaText: isAuthenticated ? 'Current Free Tier' : 'Get Started Free',
      highlighted: false,
    },
    {
      name: 'NORMAL',
      price: '₹399',
      period: '30 days',
      description: 'Balanced power for daily knowledge work and active document queries.',
      features: [
        'Access to all standard & fast models',
        '500,000 monthly token quota',
        'Up to 25 documents in RAG library',
        'Semantic retrieval with citation highlights',
        'Detailed usage and analytics dashboard',
      ],
      ctaText: 'Upgrade to Normal',
      highlighted: false,
    },
    {
      name: 'PRO',
      price: '₹1,099',
      period: '30 days',
      description: 'Complete unconstrained workspace for demanding AI workflows.',
      features: [
        'High-context reasoning models (gpt-4o, llama-3.1-70b)',
        '2,000,000 monthly token quota',
        'Unlimited document storage & chunking',
        'Priority streaming queues',
        'Extended session duration & custom system prompts',
      ],
      ctaText: 'Upgrade to Pro',
      highlighted: true,
    },
  ]

  const faqs = [
    {
      q: 'How does document grounding (RAG) work in NexaAI?',
      a: 'When you upload documents (PDF, DOCX, TXT, CSV), NexaAI extracts text, produces semantic chunks, and generates vector embeddings stored in pgvector. In chat, semantic search retrieves the most relevant passages and grounds the model response with clear source citations.',
    },
    {
      q: 'Which AI providers and models can I use?',
      a: 'NexaAI supports real backend providers: OpenAI (gpt-4o, gpt-4o-mini), Google Gemini (gemini-1.5-flash, gemini-1.5-pro), and Groq (llama-3.1-70b). Models are dynamically loaded from our service registry.',
    },
    {
      q: 'Is my conversation data kept private?',
      a: 'Yes. Each user has strict tenant isolation. In administrative query monitoring, prompt contents are never exposed; only aggregated query metadata (token count, latency, timestamps) is processed for operational health.',
    },
    {
      q: 'How are payments processed?',
      a: 'All payment interactions run in Razorpay Test Mode with test keys. We never present test payments as live billing, and AI provider inference cost estimates are strictly separate from payment transactions.',
    },
  ]

  return (
    <div className="d-flex flex-column gap-5 pb-5">
      {/* Hero Section */}
      <section className="container-xl px-3 pt-5 pb-4 text-center">
        <div className="d-inline-flex align-items-center gap-2 px-3 py-1 mb-4 nexa-badge nexa-badge-brand">
          <img src="/assets/nexa-avatar.png" alt="NexaAI" width={18} height={18} className="rounded-circle" />
          <span>Intelligent multi-model workspace with document RAG</span>
        </div>

        <h1
          className="fw-bold tracking-tight mb-3"
          style={{
            fontSize: 'clamp(2.25rem, 5vw, 3.75rem)',
            letterSpacing: '-0.03em',
            lineHeight: 1.15,
          }}
        >
          One intelligent workspace
          <br />
          <span style={{ color: 'var(--brand-primary)' }}>for your AI conversations.</span>
        </h1>

        <p
          className="text-secondary mx-auto mb-4"
          style={{ maxWidth: '640px', fontSize: '1.125rem', lineHeight: 1.6 }}
        >
          Fluid AI chat, persistent memory, multi-model selection (OpenAI, Gemini, Groq), and grounded document retrieval—designed with clean, content-first simplicity.
        </p>

        <div className="d-flex flex-wrap align-items-center justify-content-center gap-3">
          <button
            type="button"
            className="nexa-btn-primary px-4 py-2.5"
            style={{ fontSize: '1rem' }}
            onClick={() => onNavigate(isAuthenticated ? 'chat' : 'register')}
          >
            <span>Start chatting</span>
            <ArrowRight size={17} />
          </button>
          <button
            type="button"
            className="nexa-btn-secondary px-4 py-2.5"
            style={{ fontSize: '1rem' }}
            onClick={() => {
              const el = document.getElementById('features')
              el?.scrollIntoView({ behavior: 'smooth' })
            }}
          >
            Explore features
          </button>
        </div>

        {/* Hero Visual Mockup */}
        <div className="mt-5 mx-auto nexa-card-elevated overflow-hidden text-start p-3 p-md-4" style={{ maxWidth: '900px' }}>
          <div className="d-flex align-items-center justify-content-between border-bottom border-subtle pb-2.5 mb-3">
            <div className="d-flex align-items-center gap-2">
              <span className="nexa-badge nexa-badge-brand">gpt-4o-mini</span>
              <span className="nexa-badge nexa-badge-subtle">RAG: 2 docs attached</span>
            </div>
            <div className="small text-muted font-monospace">latency: 420ms</div>
          </div>
          <div className="d-flex flex-column gap-3 font-monospace small">
            <div className="p-2.5 rounded bg-surface border border-subtle">
              <strong className="text-secondary d-block mb-1">User:</strong>
              Can you extract our current infrastructure deployment strategy from the Q3 Architecture spec?
            </div>
            <div className="p-2.5 rounded bg-surface-hover border border-subtle">
              <strong className="d-block mb-1" style={{ color: 'var(--brand-primary)' }}>NexaAI:</strong>
              According to <span className="text-decoration-underline">Q3_Arch_Spec.pdf (Page 4)</span>, the deployment employs Docker containers orchestrated with Spring Cloud Gateway and Kafka KRaft event distribution across independent PostgreSQL databases.
            </div>
          </div>
        </div>
      </section>

      {/* Features Section */}
      <section id="features" className="container-xl px-3 py-4">
        <div className="text-center mb-5">
          <h2 className="fw-bold tracking-tight mb-2">Engineered for Clarity & Depth</h2>
          <p className="text-secondary small mb-0">Everything you need for serious AI productivity, without distracting fluff.</p>
        </div>

        <div className="row g-4">
          <div className="col-12 col-md-6 col-lg-3">
            <div className="nexa-card p-4 h-100 d-flex flex-column">
              <div className="d-flex align-items-center justify-content-between mb-3">
                <div style={{ color: 'var(--brand-primary)' }}>
                  <Zap size={22} />
                </div>
                <img
                  src="/assets/bot-multimodel.png"
                  alt="Multi-Model AI"
                  width={46}
                  height={62}
                  className="rounded shadow-sm border border-subtle"
                  style={{ objectFit: 'contain', backgroundColor: 'var(--surface-hover)' }}
                />
              </div>
              <h5 className="fw-semibold mb-2">Multi-Model Selection</h5>
              <p className="text-secondary small mb-0">
                Switch between OpenAI GPT-4o, Google Gemini, and Groq LLaMA models seamlessly based on context size and speed needs.
              </p>
            </div>
          </div>

          <div className="col-12 col-md-6 col-lg-3">
            <div className="nexa-card p-4 h-100 d-flex flex-column">
              <div className="d-flex align-items-center justify-content-between mb-3">
                <div style={{ color: 'var(--brand-secondary)' }}>
                  <Database size={22} />
                </div>
                <img
                  src="/assets/bot-analyze-docs.png"
                  alt="Document Grounding"
                  width={46}
                  height={62}
                  className="rounded shadow-sm border border-subtle"
                  style={{ objectFit: 'contain', backgroundColor: 'var(--surface-hover)' }}
                />
              </div>
              <h5 className="fw-semibold mb-2">Document Grounding (RAG)</h5>
              <p className="text-secondary small mb-0">
                Upload PDFs, DOCX, TXT, and CSV documents. Retrieve accurate semantic chunks with precise source citations in chat.
              </p>
            </div>
          </div>

          <div className="col-12 col-md-6 col-lg-3">
            <div className="nexa-card p-4 h-100 d-flex flex-column">
              <div className="d-flex align-items-center justify-content-between mb-3">
                <div style={{ color: 'var(--brand-primary)' }}>
                  <Layers size={22} />
                </div>
                <img
                  src="/assets/bot-chat-smarter.png"
                  alt="Persistent Memory"
                  width={46}
                  height={62}
                  className="rounded shadow-sm border border-subtle"
                  style={{ objectFit: 'contain', backgroundColor: 'var(--surface-hover)' }}
                />
              </div>
              <h5 className="fw-semibold mb-2">Real Persistent Memory</h5>
              <p className="text-secondary small mb-0">
                Conversations persist in your workspace. Search history, rename, archive, or export threads anytime with zero data loss.
              </p>
            </div>
          </div>

          <div className="col-12 col-md-6 col-lg-3">
            <div className="nexa-card p-4 h-100 d-flex flex-column">
              <div className="d-flex align-items-center justify-content-between mb-3">
                <div style={{ color: '#10b981' }}>
                  <ShieldCheck size={22} />
                </div>
                <img
                  src="/assets/bot-security-shield.png"
                  alt="Security & Quotas"
                  width={46}
                  height={62}
                  className="rounded shadow-sm border border-subtle"
                  style={{ objectFit: 'contain', backgroundColor: 'var(--surface-hover)' }}
                />
              </div>
              <h5 className="fw-semibold mb-2">Privacy & Transparent Quotas</h5>
              <p className="text-secondary small mb-0">
                Real persisted token counts, storage metrics, and remaining quota tracking. Never wonder what happened to your allowance.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* Model Showcase */}
      <section className="container-xl px-3 py-4">
        <div className="border border-subtle rounded-4 p-4 p-md-5 bg-elevated">
          <div className="d-flex flex-column flex-md-row align-items-md-center justify-content-between gap-3 mb-4">
            <div>
              <span className="nexa-badge nexa-badge-brand mb-2">Model Showcase</span>
              <h3 className="fw-bold tracking-tight mb-1">Data-Driven Model Availability</h3>
              <p className="text-secondary small mb-0">Connected live to production inference providers.</p>
            </div>
            <button
              type="button"
              className="nexa-btn-secondary"
              onClick={() => onNavigate(isAuthenticated ? 'chat' : 'login')}
            >
              Test Models in Chat
            </button>
          </div>

          <div className="row g-3">
            {models.map((m) => (
              <div key={m.name} className="col-12 col-md-6">
                <div className="nexa-card p-3.5 d-flex align-items-start justify-content-between gap-3">
                  <div>
                    <div className="d-flex align-items-center gap-2 mb-1">
                      <strong className="text-body">{m.name}</strong>
                      <span className="nexa-badge nexa-badge-subtle font-monospace">{m.provider}</span>
                    </div>
                    <div className="text-secondary small mb-1">{m.bestFor}</div>
                    <div className="text-muted small font-monospace">{m.context}</div>
                  </div>
                  <span className="nexa-badge nexa-badge-secondary">{m.status}</span>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* RAG Showcase */}
      <section className="container-xl px-3 py-4">
        <div className="row align-items-center g-4">
          <div className="col-12 col-lg-5">
            <span className="nexa-badge nexa-badge-secondary mb-2">Document Ingestion Pipeline</span>
            <h2 className="fw-bold tracking-tight mb-3">Ground Conversations in Ground Truth</h2>
            <p className="text-secondary mb-4">
              Stop hallucination with structured RAG. Upload files, let our distributed workers extract and index embeddings, and query them directly in conversational context.
            </p>
            <div className="d-flex flex-column gap-2.5">
              <div className="d-flex align-items-center gap-2.5 small text-body">
                <Check size={16} className="text-success" />
                <span>Multi-format support: PDF, DOCX, TXT, Markdown, CSV</span>
              </div>
              <div className="d-flex align-items-center gap-2.5 small text-body">
                <Check size={16} className="text-success" />
                <span>High-performance vector retrieval with cosine similarity</span>
              </div>
              <div className="d-flex align-items-center gap-2.5 small text-body">
                <Check size={16} className="text-success" />
                <span>Inline source citations with passage highlights</span>
              </div>
            </div>
          </div>
          <div className="col-12 col-lg-7">
            <div className="nexa-card p-4 overflow-hidden position-relative">
              <div className="d-flex align-items-center justify-content-between border-bottom border-subtle pb-2.5 mb-3">
                <span className="small fw-semibold text-secondary">Document Vector Embeddings Pipeline</span>
                <span className="nexa-badge nexa-badge-secondary">pgvector Powered</span>
              </div>
              <div className="row g-3 align-items-center">
                <div className="col-12 col-sm-6">
                  <img
                    src="/assets/rag-document-analysis.png"
                    alt="RAG Document Analysis Pipeline"
                    className="w-100 rounded-3 shadow-sm border border-subtle"
                    style={{ maxHeight: '200px', objectFit: 'cover' }}
                  />
                </div>
                <div className="col-12 col-sm-6 d-flex flex-column gap-2">
                  <div className="p-2 rounded bg-surface border border-subtle small">
                    <div className="d-flex justify-content-between align-items-center mb-1">
                      <strong className="text-body text-truncate">2026_Annual_Report.pdf</strong>
                      <span className="nexa-badge nexa-badge-secondary">READY</span>
                    </div>
                    <span className="text-muted font-monospace" style={{ fontSize: '0.7rem' }}>48 chunks • 1536 dim embeddings</span>
                  </div>
                  <div className="p-2 rounded bg-surface border border-subtle small">
                    <div className="d-flex justify-content-between align-items-center mb-1">
                      <strong className="text-body text-truncate">Architecture_Spec.docx</strong>
                      <span className="nexa-badge nexa-badge-secondary">READY</span>
                    </div>
                    <span className="text-muted font-monospace" style={{ fontSize: '0.7rem' }}>16 chunks • Cosine similarity 0.89</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Pricing Overview Section */}
      <section className="container-xl px-3 py-4">
        <div className="text-center mb-4">
          <div className="d-inline-flex align-items-center gap-2 px-3 py-1 mb-2 nexa-badge nexa-badge-test">
            RAZORPAY TEST MODE
          </div>
          <h2 className="fw-bold tracking-tight mb-2">Transparent Subscription Plans</h2>
          <p className="text-secondary small mb-0">Clear monthly limits. Never any surprise billing.</p>
        </div>

        <div className="row g-4 justify-content-center">
          {pricingTiers.map((p) => (
            <div key={p.name} className="col-12 col-md-4">
              <div
                className={`nexa-card p-4 h-100 d-flex flex-column ${
                  p.highlighted ? 'border-primary shadow-sm' : ''
                }`}
                style={p.highlighted ? { borderColor: 'var(--brand-primary)' } : undefined}
              >
                <div className="d-flex align-items-center justify-content-between mb-2">
                  <h5 className="fw-bold mb-0">{p.name}</h5>
                  {p.highlighted && <span className="nexa-badge nexa-badge-brand">Popular</span>}
                </div>
                <div className="d-flex align-items-baseline gap-1 my-3">
                  <span className="fs-2 fw-bold text-body">{p.price}</span>
                  <span className="text-secondary small">/ {p.period}</span>
                </div>
                <p className="text-secondary small mb-4">{p.description}</p>
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
                  className={p.highlighted ? 'nexa-btn-primary w-100' : 'nexa-btn-secondary w-100'}
                  onClick={() => onNavigate('pricing')}
                >
                  {p.ctaText}
                </button>
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* FAQ Preview */}
      <section className="container-xl px-3 py-4">
        <div className="text-center mb-4">
          <h2 className="fw-bold tracking-tight mb-2">Frequently Asked Questions</h2>
          <p className="text-secondary small">Everything you need to know about NexaAI.</p>
        </div>

        <div className="mx-auto d-flex flex-column gap-3" style={{ maxWidth: '780px' }}>
          {faqs.map((faq, i) => (
            <div key={i} className="nexa-card p-4">
              <h6 className="fw-semibold text-body mb-2">{faq.q}</h6>
              <p className="text-secondary small mb-0" style={{ lineHeight: 1.6 }}>{faq.a}</p>
            </div>
          ))}
        </div>
      </section>

      {/* CTA Section */}
      <section className="container-xl px-3 pt-3">
        <div className="border border-subtle rounded-4 p-5 text-center bg-elevated position-relative overflow-hidden">
          <h2 className="fw-bold tracking-tight mb-2">Ready to power your conversations?</h2>
          <p className="text-secondary mx-auto mb-4 small" style={{ maxWidth: '520px' }}>
            Join NexaAI to combine the best LLMs with your private documents in a distraction-free workspace.
          </p>
          <button
            type="button"
            className="nexa-btn-primary px-4 py-2.5 mx-auto"
            onClick={() => onNavigate(isAuthenticated ? 'chat' : 'register')}
          >
            <span>{isAuthenticated ? 'Open Workspace' : 'Start chatting for free'}</span>
            <ArrowRight size={16} />
          </button>
        </div>
      </section>
    </div>
  )
}
