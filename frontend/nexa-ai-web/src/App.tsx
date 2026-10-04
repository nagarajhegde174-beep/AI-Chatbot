/**
 * Phase 0 application shell.
 *
 * This is deliberately a shell, not a product screen. Phase 0 delivers
 * architecture, documentation and a buildable foundation; product screens are
 * built in their own phases (docs/ARCHITECTURE.md).
 *
 * What this component does prove is that the frontend toolchain is real: React,
 * TypeScript strict, Bootstrap and SCSS all compile and render together. That is
 * a genuine Phase 0 deliverable, because it is the first thing that would
 * otherwise fail silently.
 *
 * The service table below is transcribed from docs/ARCHITECTURE.md section 1.2.
 * It is a real check rather than decoration: if a service is renamed, given a
 * new port or given a database, this file and the documentation have to be
 * updated together, which is the drift the docs rules exist to prevent.
 */

import { Cpu, Database, FolderTree, Server } from 'lucide-react'

interface ServiceBoundary {
  readonly name: string
  readonly port: number
  readonly responsibility: string
  /** null means the service is stateless by design. */
  readonly database: string | null
  readonly phase: number
}

const SERVICES: readonly ServiceBoundary[] = [
  { name: 'api-gateway', port: 8080, responsibility: 'Public entry point, routing edge', database: null, phase: 3 },
  { name: 'auth-service', port: 8081, responsibility: 'Credentials, tokens, account status', database: 'nexa_auth', phase: 1 },
  { name: 'user-service', port: 8082, responsibility: 'Profile, preferences, administration', database: 'nexa_user', phase: 2 },
  { name: 'chat-service', port: 8083, responsibility: 'Conversations, memory, streamed relay', database: 'nexa_chat', phase: 5 },
  { name: 'ai-service', port: 8084, responsibility: 'Stateless multi-model inference', database: null, phase: 4 },
  { name: 'document-service', port: 8085, responsibility: 'Upload, extraction, chunking', database: 'nexa_document', phase: 6 },
  { name: 'rag-service', port: 8086, responsibility: 'Embeddings and pgvector retrieval', database: 'nexa_rag', phase: 7 },
  { name: 'subscription-service', port: 8087, responsibility: 'Plans, entitlements, test orders', database: 'nexa_subscription', phase: 9 },
]

const STACK: readonly string[] = [
  'React 19',
  'Vite',
  'TypeScript (strict)',
  'Bootstrap 5.3',
  'SCSS',
  'Lucide icons',
]

function App() {
  return (
    <main className="container py-5">
      <header className="mb-4">
        <h1 className="nexa-wordmark display-5 mb-1">NexaAI</h1>
        <p className="text-body-secondary mb-3">
          Multi-model AI chat, grounded in your own documents.
        </p>
        <ul className="d-flex flex-wrap gap-2 list-unstyled mb-0" aria-label="Technology stack">
          {STACK.map((item) => (
            <li key={item} className="badge text-bg-light border">
              {item}
            </li>
          ))}
        </ul>
      </header>

      <div className="alert alert-info" role="status">
        <strong>Phase 0 &mdash; foundation.</strong> Architecture, documentation, service
        boundaries and a buildable toolchain. No business feature is implemented yet, and the
        service directories under <code>backend/</code> are empty on purpose &mdash; see{' '}
        <code>docs/ARCHITECTURE.md</code> sections 13&ndash;17.
      </div>

      <section className="card nexa-surface-raised mb-4">
        <div className="card-body">
          <h2 className="h5 card-title d-flex align-items-center gap-2">
            <Server size={18} aria-hidden="true" />
            Eight independent services
          </h2>
          <p className="card-text text-body-secondary nexa-measure">
            Each is a separate Spring Boot application with its own <code>pom.xml</code>, port,
            configuration and database. No service reads another service&rsquo;s database, and
            there is deliberately no aggregator build.
          </p>
          <div className="table-responsive">
            <table className="table table-sm align-middle mb-0">
              <caption className="visually-hidden">
                The eight backend services, their ports, responsibilities and databases
              </caption>
              <thead>
                <tr>
                  <th scope="col">Service</th>
                  <th scope="col">Port</th>
                  <th scope="col">Responsibility</th>
                  <th scope="col">Database</th>
                  <th scope="col">Phase</th>
                </tr>
              </thead>
              <tbody>
                {SERVICES.map((service) => (
                  <tr key={service.name}>
                    <th scope="row" className="font-monospace fw-normal">
                      {service.name}
                    </th>
                    <td className="font-monospace">{service.port}</td>
                    <td className="text-body-secondary">{service.responsibility}</td>
                    <td className="font-monospace">
                      {service.database === null ? (
                        <span className="text-body-secondary fst-italic">none (stateless)</span>
                      ) : (
                        service.database
                      )}
                    </td>
                    <td className="font-monospace">{service.phase}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <section className="row g-3 mb-4">
        <div className="col-md-4">
          <div className="nexa-surface-raised p-3 h-100">
            <h3 className="h6 d-flex align-items-center gap-2">
              <Database size={16} aria-hidden="true" />
              One database per service
            </h3>
            <p className="small text-body-secondary mb-0 nexa-measure">
              PostgreSQL 17 with pgvector, one database and one role each. A cross-service query
              fails with a permission error rather than quietly succeeding.
            </p>
          </div>
        </div>

        <div className="col-md-4">
          <div className="nexa-surface-raised p-3 h-100">
            <h3 className="h6 d-flex align-items-center gap-2">
              <FolderTree size={16} aria-hidden="true" />
              REST and Kafka
            </h3>
            <p className="small text-body-secondary mb-0 nexa-measure">
              REST for request/response, Kafka for events and background work. KRaft mode only:
              there is no ZooKeeper anywhere in this project.
            </p>
          </div>
        </div>

        <div className="col-md-4">
          <div className="nexa-surface-raised p-3 h-100">
            <h3 className="h6 d-flex align-items-center gap-2">
              <Cpu size={16} aria-hidden="true" />
              Spring AI
            </h3>
            <p className="small text-body-secondary mb-0 nexa-measure">
              OpenAI, Google Gemini and Groq/LLaMA through one abstraction, called only by the
              AI Service. Provider keys are server-side and never reach this bundle.
            </p>
          </div>
        </div>
      </section>

      <footer className="text-body-secondary small nexa-measure">
        <p className="mb-1">
          All API traffic goes to the gateway at <code>/api/v1/**</code>. This bundle contains no
          API key, no signing secret and no payment credential &mdash; secrets are server-side
          environment variables only (<code>docs/RULES.md</code> section 6).
        </p>
        <p className="mb-0">
          Light and dark themes are driven by Bootstrap&rsquo;s <code>data-bs-theme</code>{' '}
          attribute. The runtime theme switcher arrives in Phase 3 with the auth screens.
        </p>
      </footer>
    </main>
  )
}

export default App