import { useEffect, useState } from 'react'
import { Cpu, Check, AlertTriangle, ShieldCheck, ChevronDown } from 'lucide-react'
import { fetchModelCatalog, type ModelCatalog } from '../../services/aiService.ts'

interface ModelSelectorProps {
  readonly currentModel: string
  readonly onSelectModel: (modelId: string) => void
  readonly disabled?: boolean
}

// Built-in fallback catalog in case the AI service catalog endpoint is unreachable
const DEFAULT_CATALOG: ModelCatalog = {
  providers: [
    {
      provider: 'OPENAI',
      available: true,
      models: [
        {
          id: 'gpt-4o-mini',
          displayName: 'GPT-4o Mini',
          maxContextTokens: 128000,
          maxOutputTokens: 16384,
          supportsStreaming: true,
          supportsTemperature: true,
          tags: { provider: 'OPENAI', tier: 'fast', recommended: 'true' },
        },
        {
          id: 'gpt-4o',
          displayName: 'GPT-4o',
          maxContextTokens: 128000,
          maxOutputTokens: 4096,
          supportsStreaming: true,
          supportsTemperature: true,
          tags: { provider: 'OPENAI', tier: 'flagship' },
        },
      ],
    },
    {
      provider: 'GEMINI',
      available: true,
      models: [
        {
          id: 'gemini-2.0-flash',
          displayName: 'Gemini 2.0 Flash',
          maxContextTokens: 1048576,
          maxOutputTokens: 8192,
          supportsStreaming: true,
          supportsTemperature: true,
          tags: { provider: 'GEMINI', tier: 'fast' },
        },
        {
          id: 'gemini-1.5-pro',
          displayName: 'Gemini 1.5 Pro',
          maxContextTokens: 2097152,
          maxOutputTokens: 8192,
          supportsStreaming: true,
          supportsTemperature: true,
          tags: { provider: 'GEMINI', tier: 'reasoning' },
        },
      ],
    },
    {
      provider: 'GROQ',
      available: true,
      models: [
        {
          id: 'llama-3.3-70b-versatile',
          displayName: 'LLaMA 3.3 70B (Groq)',
          maxContextTokens: 128000,
          maxOutputTokens: 32768,
          supportsStreaming: true,
          supportsTemperature: true,
          tags: { provider: 'GROQ', speed: 'ultra-fast' },
        },
      ],
    },
  ],
}

export function ModelSelector({ currentModel, onSelectModel, disabled = false }: ModelSelectorProps) {
  const [catalog, setCatalog] = useState<ModelCatalog>(DEFAULT_CATALOG)
  const [isOpen, setIsOpen] = useState(false)

  useEffect(() => {
    let mounted = true
    fetchModelCatalog()
      .then((cat) => {
        if (mounted && cat?.providers?.length > 0) {
          setCatalog(cat)
        }
      })
      .catch(() => {
        // Fall back to default catalog gracefully
      })
    return () => {
      mounted = false
    }
  }, [])

  // Find displayName of currently selected model
  let activeName = currentModel
  for (const p of catalog.providers) {
    const found = p.models.find((m) => m.id === currentModel)
    if (found) {
      activeName = found.displayName
      break
    }
  }

  return (
    <div className="dropdown position-relative">
      <button
        type="button"
        className="btn btn-sm btn-outline-secondary d-flex align-items-center gap-2 py-1 px-2 border rounded shadow-none"
        disabled={disabled}
        onClick={() => setIsOpen((prev) => !prev)}
        aria-expanded={isOpen}
        title="Select AI Model"
      >
        <Cpu size={14} className="text-primary" />
        <span className="fw-semibold small text-truncate" style={{ maxWidth: '140px' }}>
          {activeName}
        </span>
        <ChevronDown size={12} className="text-secondary" />
      </button>

      {isOpen && (
        <>
          <div
            className="position-fixed top-0 start-0 w-100 h-100 z-2"
            onClick={() => setIsOpen(false)}
          />
          <div
            className="dropdown-menu show position-absolute shadow-lg py-2 z-3"
            style={{ width: '280px', top: '100%', left: 0 }}
          >
            <div className="px-3 pb-2 mb-1 border-bottom d-flex justify-content-between align-items-center">
              <span className="fw-bold small text-body">Choose AI Model</span>
              <span className="badge text-bg-success-subtle text-success border border-success-subtle d-flex align-items-center gap-1" style={{ fontSize: '0.65rem' }}>
                <ShieldCheck size={10} /> Model Router
              </span>
            </div>

            {catalog.providers.map((p) => (
              <div key={p.provider} className="mb-2">
                <div className="px-3 py-1 d-flex justify-content-between align-items-center bg-body-tertiary">
                  <span className="fw-semibold small text-secondary text-uppercase" style={{ fontSize: '0.7rem' }}>
                    {p.provider}
                  </span>
                  {p.available ? (
                    <span className="badge text-bg-success py-0 px-1" style={{ fontSize: '0.6rem' }}>
                      Online
                    </span>
                  ) : (
                    <span className="badge text-bg-warning py-0 px-1 d-flex align-items-center gap-1" style={{ fontSize: '0.6rem' }}>
                      <AlertTriangle size={8} /> Circuit open
                    </span>
                  )}
                </div>

                {p.models.map((m) => {
                  const isSelected = m.id === currentModel
                  return (
                    <button
                      key={m.id}
                      type="button"
                      className={`dropdown-item d-flex align-items-center justify-content-between py-2 px-3 ${
                        isSelected ? 'active bg-primary-subtle text-primary fw-semibold' : ''
                      }`}
                      onClick={() => {
                        onSelectModel(m.id)
                        setIsOpen(false)
                      }}
                    >
                      <div>
                        <div className="small mb-0">{m.displayName}</div>
                        <div className="text-secondary opacity-75" style={{ fontSize: '0.68rem' }}>
                          Context: {Math.round(m.maxContextTokens / 1000)}k • Stream: {m.supportsStreaming ? 'Yes' : 'No'}
                        </div>
                      </div>
                      {isSelected && <Check size={14} className="text-primary flex-shrink-0 ms-2" />}
                    </button>
                  )
                })}
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  )
}
