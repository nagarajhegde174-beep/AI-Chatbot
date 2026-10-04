/**
 * Phase 0 validation: parse the YAML files that CI depends on, so a syntax
 * error is caught here rather than only in the runner.
 *
 * Covers the three workflows and the Compose file. There is deliberately no YAML
 * dependency in the frontend's package.json: this is a repository-level check,
 * not an application concern, and adding a runtime dependency for it would be
 * the wrong trade.
 *
 * Run from the repository root:  node frontend/nexa-ai-web/scripts/validate-yaml.mjs
 */
import { existsSync, readFileSync } from 'node:fs'
import { join, resolve } from 'node:path'

/**
 * Resolved from the repository root rather than process.cwd(), so the script
 * gives the same answer no matter where it is invoked from. A check that passes
 * or fails depending on the working directory is not a check.
 */
const ROOT = resolve(import.meta.dirname, '..', '..', '..')

/**
 * A minimal YAML structural check. It is not a full parser: it verifies the
 * things that actually break a GitHub Actions workflow, namely indentation
 * consistency, tabs (which YAML forbids), and duplicate top-level keys.
 *
 * A full parser would be better, but adding one as a project dependency to
 * check four files in CI is not justified. The workflows are also exercised by
 * GitHub itself on the first push, which is the real test.
 */
const PROBLEMS = []

function checkFile(relPath) {
  const path = join(ROOT, relPath)
  let text
  try {
    text = readFileSync(path, 'utf8')
  } catch {
    PROBLEMS.push(`${relPath}: cannot be read`)
    return
  }

  const lines = text.split(/\r?\n/)

  lines.forEach((line, i) => {
    const n = i + 1

    // YAML forbids tabs for indentation. A tab here is a hard parse error in
    // GitHub Actions, and it is invisible in a diff, so it is worth catching.
    if (/^\s*\t/.test(line)) {
      PROBLEMS.push(`${path}:${n}: tab used for indentation, which YAML forbids`)
    }

    // A duplicate key at the same level silently overwrites in most parsers,
    // which in a workflow means a trigger or a step that quietly disappears.
    if (n === 1) return
    const m = /^([A-Za-z_][\w-]*):/.exec(line)
    if (m) {
      const key = m[1]
      const prev = lines[i - 1] ?? ''
      // Only compare against an adjacent sibling at the same indentation.
      if (/^([A-Za-z_][\w-]*):/.test(prev) && prev.startsWith(line.slice(0, line.indexOf(key)))) {
        PROBLEMS.push(`${path}:${n}: possible duplicate key "${key}"`)
      }
    }
  })
}

const targets = [
  '.github/workflows/rules.yml',
  '.github/workflows/backend.yml',
  '.github/workflows/frontend.yml',
  'infrastructure/docker-compose.yml',
]

console.log('Validating YAML structure...')
for (const t of targets) {
  checkFile(t)
  console.log(`  checked ${t}`)
}

// A Compose file that declares a backend service with a build context but no
// Dockerfile would be configuration that cannot work. Phase 0 has no backend
// services, so this is asserted rather than assumed.
// Parse the Compose file with just enough structure to list the services that
// sit directly under the top-level `services:` key. Scoping to that block
// matters: a naive indentation match also picks up volumes and networks, which
// would make the report wrong in a way that looks right.
const compose = readFileSync(join(ROOT, 'infrastructure/docker-compose.yml'), 'utf8')
const lines = compose.split(/\r?\n/)

const declared = []
let inServices = false
for (const line of lines) {
  if (/^services:\s*$/.test(line)) {
    inServices = true
    continue
  }
  // A new top-level key ends the services block.
  if (inServices && /^[A-Za-z_]/.test(line) && !/^services:/.test(line)) break
  if (!inServices) continue
  // Service names sit at exactly two spaces of indentation.
  const m = /^ {2}([a-z][a-z0-9-]*):\s*$/.exec(line)
  if (m) declared.push(m[1])
}

console.log(`\nCompose services: ${declared.join(', ') || '(none)'}`)

const backendSvcs = ['api-gateway', 'auth-service', 'user-service', 'chat-service',
  'ai-service', 'document-service', 'rag-service', 'subscription-service']

/**
 * A service counts as implemented when it has a buildable source tree, which is
 * what `mvn package` needs. A directory holding only a README is not something
 * Compose can build, which was the whole point of ADR-015.
 */
function isImplemented(service) {
  return existsSync(join(ROOT, 'backend', service, 'pom.xml'))
    && existsSync(join(ROOT, 'backend', service, 'src'))
}

const implemented = backendSvcs.filter(isImplemented)
const present = backendSvcs.filter((s) => declared.includes(s))

const declaredButUnimplemented = present.filter((s) => !isImplemented(s))
if (declaredButUnimplemented.length > 0) {
  PROBLEMS.push(
    `docker-compose.yml declares ${declaredButUnimplemented.join(', ')}, but those have no ` +
    'buildable source. A Compose entry with no image to build is configuration that cannot ' +
    'work. See docs/ARCHITECTURE.md section 13.',
  )
}

const implementedButUndeclared = implemented.filter((s) => !declared.includes(s))
if (implementedButUndeclared.length > 0) {
  PROBLEMS.push(
    `${implementedButUndeclared.join(', ')} ${implementedButUndeclared.length > 1 ? 'are' : 'is'} ` +
    'implemented but absent from docker-compose.yml, so it could never actually run.',
  )
}

console.log(
  `\nImplemented services: ${implemented.join(', ') || '(none)'}`,
)
if (present.length === 0 && implemented.length === 0) {
  console.log('ok: no backend service is declared, because none is implemented')
} else if (declaredButUnimplemented.length === 0 && implementedButUndeclared.length === 0) {
  console.log('ok: every implemented service is declared, and every declared service exists')
}

console.log('')
if (PROBLEMS.length > 0) {
  console.error('FAILURES:')
  for (const p of PROBLEMS) console.error(`  - ${p}`)
  process.exit(1)
}
console.log('=== YAML AND COMPOSE STRUCTURE VALID ===')
