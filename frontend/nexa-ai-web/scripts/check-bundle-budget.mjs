/**
 * The bundle budget check.
 *
 * ## What it measures, and why two numbers
 *
 * `ARCHITECTURE.md` §9.2 budgets **initial** JavaScript at 200 kB gzipped. That is the number a
 * user experiences: what the browser must download before the page is usable. It is not the same
 * as the total size of `dist`, and the difference is the whole point of code-splitting.
 *
 * This script therefore reports two numbers and enforces both:
 *
 * - **initial** — every chunk in the static import closure of the HTML entry point. This is what
 *   the 200 kB budget applies to.
 * - **total** — every JavaScript chunk emitted, including ones only reachable on demand. Capped
 *   separately, so splitting a lazy feature is free for first paint but not free forever.
 *
 * Both caps are enforced. Neither one alone would be honest: an initial-only cap lets the codebase
 * grow without limit, and a total-only cap punishes deferring a heavy dependency that most users
 * never need — which is the opposite of what the budget is for.
 *
 * ## Why the previous check was wrong, not merely strict
 *
 * It summed every `.js` file in `dist/assets` and compared the result to the 200 kB initial budget.
 * That measured a different quantity than the one the budget describes, and it made splitting
 * strictly worse: moving code into a chunk changed nothing for the check while making the real
 * user-facing number better. A check that penalises the technique it is meant to encourage gets
 * satisfied by deleting the feature instead.
 *
 * Run: `node scripts/check-bundle-budget.mjs`. Exits non-zero on either cap.
 */

import { readFileSync, existsSync } from 'node:fs'
import { gzipSync } from 'node:zlib'
import { join } from 'node:path'

/** `ARCHITECTURE.md` §9.2. Initial payload, gzipped. */
const INITIAL_BUDGET_KB = 200

/**
 * Total across all chunks, gzipped.
 *
 * A ceiling rather than a target. It exists so that deferring a dependency does not become a
 * licence to add unlimited ones: every kilobyte a user never downloads still has to be downloaded
 * by someone. Set with headroom above the measured total, and lowered whenever the total falls.
 */
const TOTAL_BUDGET_KB = 320

const DIST = 'dist'
const MANIFEST = join(DIST, '.vite', 'manifest.json')

function fail(message) {
  console.error(`::error::${message}`)
  process.exit(1)
}

if (!existsSync(MANIFEST)) {
  fail(
    `No build manifest at ${MANIFEST}. The bundle budget needs it to tell initial from total. ` +
      `Check that build.manifest is still enabled in vite.config.ts.`,
  )
}

const manifest = JSON.parse(readFileSync(MANIFEST, 'utf8'))

/** Bytes of a chunk, gzipped. Missing files are a build problem, not a size problem. */
function gzippedSize(file) {
  const path = join(DIST, file)
  if (!existsSync(path)) {
    fail(`Manifest references ${file}, which is not in ${DIST}. The build output is inconsistent.`)
  }
  return gzipSync(readFileSync(path)).length
}

/**
 * The static import closure of an entry chunk.
 *
 * <p>Follows `imports` only. `dynamicImports` are deliberately excluded: they are fetched on
 * demand, which is exactly what the initial budget is not measuring.
 *
 * <p>Traversal is by manifest KEY, not by output file path. Vite's manifest keys chunks by name
 * (`_math-abc123.js`) while `file` is the emitted path (`assets/math-abc123.js`), and the two
 * differ — so resolving on `file` silently finds nothing and reports a near-zero initial payload.
 * That failure mode is dangerous precisely because it makes the check pass.
 */
function staticClosure(entryKey, manifest, seen = new Set()) {
  if (seen.has(entryKey)) {
    return seen
  }
  seen.add(entryKey)

  for (const imported of manifest[entryKey]?.imports ?? []) {
    staticClosure(imported, manifest, seen)
  }
  return seen
}

// The HTML entry points. Falling back would let a manifest shape change turn this into a
// zero-byte check that always passes, so an empty set is a hard failure instead.
const entries = Object.keys(manifest).filter((key) => manifest[key].isEntry)

if (entries.length === 0) {
  fail('The manifest declares no entry chunk. The budget check cannot measure anything.')
}

const initialKeys = new Set()
for (const entry of entries) {
  for (const key of staticClosure(entry, manifest)) {
    initialKeys.add(key)
  }
}

const initialBytes = [...initialKeys].reduce(
  (total, key) => total + gzippedSize(manifest[key].file),
  0,
)

const jsKeys = Object.keys(manifest).filter((key) => manifest[key].file.endsWith('.js'))

const totalBytes = jsKeys.reduce((total, key) => total + gzippedSize(manifest[key].file), 0)

const kb = (bytes) => (bytes / 1024).toFixed(1)

console.log(
  `JavaScript, initial load, gzipped: ${kb(initialBytes)} kB (${initialKeys.size} chunks)`,
)
console.log(`JavaScript, total, gzipped:       ${kb(totalBytes)} kB (${jsKeys.length} chunks)`)

const deferred = jsKeys.filter((key) => !initialKeys.has(key))
if (deferred.length > 0) {
  const deferredBytes = deferred.reduce((total, key) => total + gzippedSize(manifest[key].file), 0)
  console.log(`  of which loaded on demand:      ${kb(deferredBytes)} kB`)
  for (const key of deferred) {
    console.log(`    - ${manifest[key].file}  ${kb(gzippedSize(manifest[key].file))} kB`)
  }
}

if (initialBytes > INITIAL_BUDGET_KB * 1024) {
  fail(
    `Initial JavaScript is ${kb(initialBytes)} kB gzipped, over the ${INITIAL_BUDGET_KB} kB budget. ` +
      `See docs/ARCHITECTURE.md section 9. Deferring the dependency is the fix, not raising the budget.`,
  )
}

if (totalBytes > TOTAL_BUDGET_KB * 1024) {
  fail(
    `Total JavaScript is ${kb(totalBytes)} kB gzipped, over the ${TOTAL_BUDGET_KB} kB total ceiling. ` +
      `A deferred chunk still has to be downloaded by someone.`,
  )
}

console.log(`ok: initial within ${INITIAL_BUDGET_KB} kB and total within ${TOTAL_BUDGET_KB} kB`)