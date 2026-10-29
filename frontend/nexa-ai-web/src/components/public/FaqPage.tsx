import { useState } from 'react'
import { ChevronDown, ChevronUp, ArrowRight } from 'lucide-react'
import type { AppRoute } from '../../types/index.ts'

export function FaqPage({ onNavigate }: { readonly onNavigate: (route: AppRoute) => void }) {
  const [openIndex, setOpenIndex] = useState<number | null>(0)

  const faqs = [
    {
      category: 'General & Architecture',
      items: [
        {
          q: 'What makes NexaAI different from generic chat wrappers?',
          a: 'NexaAI is an event-driven AI platform built with microservices architecture (Spring Boot, Kafka KRaft, PostgreSQL, pgvector, Redis). It separates conversational state, inference orchestration, RAG embeddings, and payment accounting with strict domain boundaries and multi-tenant isolation.',
        },
        {
          q: 'Can I switch between light, dark, and system themes?',
          a: 'Yes. NexaAI natively supports Light, Dark, and System themes, persisted in your local storage. The system theme automatically adapts to your operating system color scheme in real time.',
        },
      ],
    },
    {
      category: 'Models & Inference',
      items: [
        {
          q: 'Which LLM providers and models are supported?',
          a: 'NexaAI supports real backend providers: OpenAI (gpt-4o, gpt-4o-mini), Google Gemini (gemini-1.5-flash, gemini-1.5-pro), and Groq (llama-3.1-70b-versatile). The availability is completely data-driven by our backend model registry.',
        },
        {
          q: 'Does streaming chat support real-time token delivery?',
          a: 'Yes. Chat responses use Server-Sent Events (SSE) with token-by-token streaming, a subtle caret effect, and a breathing three-dot thinking indicator while awaiting the initial token.',
        },
      ],
    },
    {
      category: 'Documents & RAG',
      items: [
        {
          q: 'What file formats can I upload for RAG?',
          a: 'You can upload PDF, DOCX, TXT, Markdown, and CSV files up to your plan quota limit. Uploaded files are chunked into semantic units and embedded into pgvector for similarity retrieval.',
        },
        {
          q: 'How are source citations presented?',
          a: 'When RAG is active, relevant passages retrieved from your documents are cited directly beneath or within the assistant answer with filename and chunk metadata.',
        },
      ],
    },
    {
      category: 'Security & Billing',
      items: [
        {
          q: 'Is my data protected from administrative exposure?',
          a: 'Yes. NexaAI enforces role-based access control (USER and ADMIN). In administrative conversation query monitoring, user prompt contents are never stored or exposed; only operational metadata (timestamps, token counts, latency) is processed.',
        },
        {
          q: 'What does "Razorpay Test Mode" mean?',
          a: 'Payments are configured exclusively in sandbox mode with test credentials. Simulated cards and UPI test flows allow you to test subscription upgrade and downgrade workflows without real currency transactions.',
        },
      ],
    },
  ]

  const toggleAccordion = (idx: number) => {
    setOpenIndex(openIndex === idx ? null : idx)
  }

  return (
    <div className="container-xl px-3 py-5">
      <div className="text-center mb-5">
        <span className="nexa-badge nexa-badge-brand mb-2">Knowledge Base</span>
        <h1 className="fw-bold tracking-tight mb-3">Frequently Asked Questions</h1>
        <p className="text-secondary mx-auto mb-0" style={{ maxWidth: '580px' }}>
          Find answers to common questions about NexaAI models, document retrieval, tenant privacy, and subscription tiers.
        </p>
      </div>

      <div className="mx-auto" style={{ maxWidth: '820px' }}>
        {faqs.map((group, groupIdx) => (
          <div key={groupIdx} className="mb-4">
            <h5 className="fw-bold text-body mb-3">{group.category}</h5>
            <div className="d-flex flex-column gap-2.5">
              {group.items.map((item, itemIdx) => {
                const globalIdx = groupIdx * 10 + itemIdx
                const isOpen = openIndex === globalIdx
                return (
                  <div key={itemIdx} className="nexa-card overflow-hidden">
                    <button
                      type="button"
                      className="btn w-100 text-start p-3.5 d-flex align-items-center justify-content-between gap-3 bg-transparent text-body"
                      onClick={() => toggleAccordion(globalIdx)}
                      aria-expanded={isOpen}
                    >
                      <span className="fw-semibold small">{item.q}</span>
                      {isOpen ? (
                        <ChevronUp size={16} className="text-secondary flex-shrink-0" />
                      ) : (
                        <ChevronDown size={16} className="text-secondary flex-shrink-0" />
                      )}
                    </button>
                    {isOpen && (
                      <div className="px-3.5 pb-3.5 pt-0 text-secondary small border-top border-subtle" style={{ lineHeight: 1.6 }}>
                        <div className="pt-3">{item.a}</div>
                      </div>
                    )}
                  </div>
                )
              })}
            </div>
          </div>
        ))}

        <div className="mt-5 p-4 rounded-3 border border-subtle bg-elevated text-center">
          <h5 className="fw-semibold mb-2">Have a question not listed here?</h5>
          <p className="text-secondary small mb-3">Explore our interactive workspace or sign in to test the platform.</p>
          <button
            type="button"
            className="nexa-btn-primary mx-auto"
            onClick={() => onNavigate('chat')}
          >
            <span>Launch AI Chat</span>
            <ArrowRight size={15} />
          </button>
        </div>
      </div>
    </div>
  )
}
