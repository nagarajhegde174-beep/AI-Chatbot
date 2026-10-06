/**
 * The react-markdown element overrides.
 *
 * ## Why this is shared rather than defined in each renderer
 *
 * There are two renderers — the ordinary one and the maths-capable one — and the code-block and
 * table components have to behave identically in both. Defining them twice is how two
 * implementations of one presentation quietly drift: a fix lands in one, and the other keeps the
 * old behaviour.
 *
 * ## Why they live in their own file
 *
 * `Markdown.tsx` is imported by the chat page on first paint, so anything it imports statically is
 * in the initial payload. The highlighter is deliberately NOT imported here — it is `import()`-ed
 * inside {@link CodeBlock} when a fenced block is first rendered.
 */

import { useCallback, useEffect, useState } from 'react'
import { Check, Copy } from 'lucide-react'

/**
 * Escapes text for `dangerouslySetInnerHTML`.
 *
 * <p>Only ever applied to code the highlighter has not processed yet, or declined to process, so
 * the output is escaped precisely once — by this — rather than relying on every caller having
 * done it. When highlight.js does run, it escapes its own output.
 *
 * <p>Not exported: nothing outside this module needs it, and exporting a plain function from a
 * component file costs the whole file its fast refresh.
 */
function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

/** The slice of the highlight.js API this component actually uses. */
interface Highlighter {
  getLanguage(name: string): unknown
  highlight(code: string, options: { language: string }): { value: string }
}

/**
 * Pulls the highlight.js instance out of a dynamically imported module.
 *
 * <p>Returns null rather than throwing if neither shape matches, so a bundler that changes how it
 * unwraps CommonJS degrades to unhighlighted — but perfectly readable — code blocks instead of
 * breaking every one of them.
 */
function highlightModule(module: unknown): Highlighter | null {
  const candidate = module as { default?: unknown }
  const resolved = (candidate?.default ?? module) as Partial<Highlighter> | null
  if (
    resolved !== null &&
    typeof resolved !== 'undefined' &&
    typeof resolved.highlight === 'function' &&
    typeof resolved.getLanguage === 'function'
  ) {
    return resolved as Highlighter
  }
  return null
}

/** A fenced code block, with a language label and a copy button. */
function CodeBlock({ code, language }: { readonly code: string; readonly language?: string }) {
  const [html, setHtml] = useState<string | null>(null)
  const [resolvedLanguage, setResolvedLanguage] = useState(language ?? 'text')
  const [copied, setCopied] = useState(false)

  useEffect(() => {
    let cancelled = false

    // Loaded here, on first use, rather than imported at module scope. This is the whole point:
    // highlight.js is the second-heaviest dependency here, and a user who never receives a code
    // block should never download it.
    void import('highlight.js/lib/common').then((module) => {
      if (cancelled) {
        return
      }

      // The dynamic form hands back a module namespace; the instance is the default export.
      // Highlight.js is typed as a CommonJS export, so TypeScript does not model that and the
      // cast is needed. Resolved at runtime with a fallback rather than assumed, because a
      // bundler that unwraps it differently would otherwise throw here and leave every code block
      // permanently unhighlighted.
      const hljs = highlightModule(module)

      if (hljs === null) {
        setHtml(escapeHtml(code))
        return
      }

      const resolved = language && hljs.getLanguage(language) ? language : 'plaintext'
      try {
        setHtml(resolved ? hljs.highlight(code, { language: resolved }).value : escapeHtml(code))
      } catch {
        // highlight.js throws on a grammar it cannot parse. A block that fails to highlight is
        // still perfectly readable — it needs to fall back to plain text, not take the message down.
        setHtml(escapeHtml(code))
      }
      setResolvedLanguage(resolved)
    })

    return () => {
      cancelled = true
    }
  }, [code, language])

  // Cleared on a timer rather than by an effect keyed on `copied`, which would reset it the
  // instant it was set.
  useEffect(() => {
    if (!copied) {
      return
    }
    const timer = window.setTimeout(() => setCopied(false), 1500)
    return () => window.clearTimeout(timer)
  }, [copied])

  const copy = useCallback(() => {
    void navigator.clipboard.writeText(code).then(
      () => setCopied(true),
      // Clipboard access can be denied by permissions policy. Failing silently here would look
      // like a broken button, so the label simply does not change.
      () => undefined,
    )
  }, [code])

  return (
    <div className="code-block">
      <div className="code-block__bar">
        <span className="code-block__lang">{resolvedLanguage}</span>
        <button
          type="button"
          className={`code-copy${copied ? ' is-copied' : ''}`}
          onClick={copy}
          aria-label={copied ? 'Copied' : 'Copy code'}
          title={copied ? 'Copied' : 'Copy code'}
        >
          {copied ? <Check size={14} aria-hidden /> : <Copy size={14} aria-hidden />}
          <span>{copied ? 'Copied' : 'Copy'}</span>
        </button>
      </div>
      <pre className="code-block__pre">
        <code
          className={`hljs language-${resolvedLanguage}`}
          dangerouslySetInnerHTML={{ __html: html ?? escapeHtml(code) }}
        />
      </pre>
    </div>
  )
}

/**
 * Inline code and fenced blocks.
 *
 * <p>The two share one react-markdown slot, distinguished by whether the parent is a paragraph.
 * Returning both from one function keeps them visually consistent instead of letting the two
 * drift apart.
 */
export function MarkdownCode({
  className,
  children,
  ...props
}: React.ComponentProps<'code'>) {
  const text = String(children ?? '').replace(/\n$/, '')
  const language = /language-(\w+)/.exec(className ?? '')?.[1]

  if (!text.includes('\n')) {
    return (
      <code className="inline-code" {...props}>
        {children}
      </code>
    )
  }

  return <CodeBlock code={text} language={language} />
}

/** Tables get a wrapper so they scroll instead of overflowing the message column. */
export function MarkdownTable({ children, ...props }: React.ComponentProps<'table'>) {
  return (
    <div className="table-scroll">
      <table {...props}>{children}</table>
    </div>
  )
}