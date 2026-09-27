/**
 * Phase 0 application shell.
 *
 * This is deliberately a placeholder, not a product screen. Phase 0 delivers architecture,
 * documentation and a buildable skeleton; business screens are built in their own phases
 * (docs/TASKS.md). What this component does prove is that the frontend toolchain is real:
 * React + TypeScript + Bootstrap + SCSS all compile and render together.
 *
 * The service table below is a copy of the topology in docs/ARCHITECTURE.md, so the running
 * frontend and the documentation cannot silently drift apart.
 */

const SERVICES = [
  { name: 'api-gateway', port: 8080, role: 'Public entry point and routing edge', database: '—' },
  { name: 'auth-service', port: 8081, role: 'Credentials, tokens, account status', database: 'nexa_auth' },
  { name: 'user-service', port: 8082, role: 'Profile, preferences, user administration', database: 'nexa_user' },
  { name: 'chat-service', port: 8083, role: 'Conversations, memory, streamed answer relay', database: 'nexa_chat' },
  { name: 'ai-service', port: 8084, role: 'Stateless multi-model LLM inference', database: '—' },
  { name: 'document-service', port: 8085, role: 'Upload, text extraction, chunking', database: 'nexa_document' },
  { name: 'rag-service', port: 8086, role: 'Embeddings and pgvector retrieval', database: 'nexa_rag' },
  { name: 'subscription-service', port: 8087, role: 'Plans, entitlements, Razorpay test orders', database: 'nexa_subscription' },
] as const

const STACK = [
  'React 19',
  'Vite',
  'TypeScript (strict)',
  'Bootstrap 5.3',
  'SCSS',
] as const

function App() {
  return (
    <main className="container py-5">
      <header className="mb-4">
        <h1 className="nexa-wordmark display-5 mb-1">NexaAI</h1>
        <p className="text-body-secondary mb-3">
          Multi-model AI chat, grounded in your own documents.
        </p>
        <div className="d-flex flex-wrap gap-2">
          {STACK.map((item) => (
            <span key={item} className="badge text-bg-light border">
              {item}
            </span>
          ))}
        </div>
      </header>

      <div className="alert alert-info" role="status">
        <strong>Phase 0 — foundation.</strong> Architecture, documentation, service boundaries
        and a buildable skeleton. No business feature is implemented yet; screens arrive in
        their own phases. See <code>docs/TASKS.md</code>.
      </div>

      <section className="card nexa-surface-raised mb-4">
        <div className="card-body">
          <h2 className="h5 card-title">Backend services</h2>
          <p className="card-text text-body-secondary">
            Eight independent Spring Boot applications. Each owns its own port, its own source
            tree and its own database. No service reads another service&apos;s database.
          </p>
          <div className="table-responsive">
            <table className="table table-sm align-middle mb-0">
              <thead>
                <tr>
                  <th scope="col">Service</th>
                  <th scope="col">Port</th>
                  <th scope="col">Responsibility</th>
                  <th scope="col">Database</th>
                </tr>
              </thead>
              <tbody>
                {SERVICES.map((service) => (
                  <tr key={service.name}>
                    <th scope="row" className="font-monospace fw-normal">
                      {service.name}
                    </th>
                    <td>{service.port}</td>
                    <td className="text-body-secondary">{service.role}</td>
                    <td className="font-monospace">{service.database}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <footer className="text-body-secondary small">
        <p className="mb-1">
          All API traffic goes to the gateway at <code>/api/v1/**</code>. This bundle contains no
          API key, no signing secret and no database credential — secrets live in server-side
          environment variables only.
        </p>
        <p className="mb-0">
          Light, dark and system themes are driven by Bootstrap&apos;s{' '}
          <code>data-bs-theme</code> attribute. The runtime theme switcher is Phase 1 work.
        </p>
      </footer>
    </main>
  )
}

export default App
