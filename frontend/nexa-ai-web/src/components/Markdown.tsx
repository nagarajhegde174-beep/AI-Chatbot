/**
 * Renders an assistant message as Markdown.
 *
 * ## Why a real renderer and not pre-formatted text
 *
 * Models emit Markdown, and prose that arrives as a wall of unrendered `**bold**` and fenced
 * backticks is unreadable. Rendering it properly also means the common structures — code,
 * tables, maths — get the treatment they were written for instead of being approximated with
 * whitespace.
 *
 * ## Why rendering is disabled while tokens are arriving
 *
 * Partially-rendered Markdown is re-parsed on every chunk, and a half-written fence or a
 * half-written `$…$` makes the parser do the wrong thing *and* throws away the reader's scroll
 * position and text selection. So during a stream the raw text is shown; on `done` it is rendered
 * once. The transition is jarring enough to be worth it, and the alternative is worse.
 *
 * ## Untrusted input
 *
 * Everything here renders model output, which is influenced by whatever the user asked and by
 * whatever was in the retrieved context. `react-markdown` does not execute HTML by default and
 * `remark-gfm` does not add any, so there is no path from a model's output to script execution.
 * That default is the reason this component does not reach for `rehype-raw`.
 */

import { useCallback, useMemo, useState } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import remarkMath from 'remark-math'
import rehypeKatex from 'rehype-katex'
import { Check, Copy } from 'lucide-react'
import hljs from 'highlight.js/lib/common'

/**
 * A narrow highlight.js bundle.
 *
 * `highlight.js/lib/common` rather than the full library: the full build is roughly ten times the
 * size and carries every language a chat assistant will never emit. `common` covers the languages
 * that appear in practice, and an unrecognised language degrades to plain text rather than
 * failing.
 */
const HIGHLIGHT_LANGUAGES = 'javascript typescript python java go rust c cpp csharp php ruby swift kotlin sql bash json yaml xml html css markdown'

/**
 * Highlights source for display.
 *
 * <p>Never throws. `highlight.js` throws on a grammar it cannot parse, and a code block that
 * fails to highlight is still perfectly readable — it just needs to fall back to plain text rather
 * than take the whole message down with it.
 */
function highlight(code: string, language: string | undefined): { html: string; language: string } {
  const resolved =
    language && hljs.getLanguage(language) ? language : hljs.getLanguage('plaintext') ? 'plaintext' : ''
  try {
    const result = resolved ? hljs.highlight(code, { language: resolved }) : null
    return {
      html: result?.value ?? escapeHtml(code),
      language: resolved || 'text',
    }
  } catch {
    return { html: escapeHtml(code), language: resolved || 'text' }
  }
}

/**
 * Escapes text for `dangerouslySetInnerHTML`.
 *
 * <p>Only ever applied to code that highlight.js declined to highlight, so the output is escaped
 * precisely once — by this — rather than relying on every caller having done it.
 */
function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

/** A copy button that reports success for long enough to be noticed. */
function CopyButton({ text }: { readonly text: string }) {
  const [copied, setCopied] = useState(false)

  const copy = useCallback(() => {
    void navigator.clipboard.writeText(text).then(
      () => {
        setCopied(true)
        // Cleared on a timer rather than on the next render: an effect keyed on `copied` would
        // reset it the instant it was set.
        window.setTimeout(() => setCopied(false), 1500)
      },
      () => {
        // Clipboard access can be denied by permissions policy. Failing silently here would look
        // like a broken button; the label simply does not change.
      },
    )
  }, [text])

  return (
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
  )
}

/** A fenced code block, with a language label, highlighting and a copy button. */
function CodeBlock({
  code,
  language,
}: {
  readonly code: string
  readonly language?: string
}) {
  const { html, language: resolved } = useMemo(
    () => highlight(code, language),
    [code, language],
  )

  return (
    <div className="code-block">
      <div className="code-block__bar">
        <span className="code-block__lang">{resolved}</span>
        <CopyButton text={code} />
      </div>
      <pre className="code-block__pre">
        <code
          className={`hljs language-${resolved}`}
          // Safe because `escapeHtml` runs on exactly the paths that did not come from
          // highlight.js, and highlight.js escapes its own output.
          dangerouslySetInnerHTML={{ __html: html }}
        />
      </pre>
    </div>
  )
}

/**
 * The component code fences and inline code.
 *
 * <p>Inline code and a fenced block share a ReactMarkdown slot, distinguished by whether the
 * parent is a paragraph. Returning the right element from one function keeps them visually
 * consistent instead of letting the two drift apart.
 */
function code({ className, children, ...props }: React.ComponentProps<'code'>) {
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

/** Tables get a wrapper so they can scroll instead of overflowing the message column. */
function table({ children, ...props }: React.ComponentProps<'table'>) {
  return (
    <div className="table-scroll">
      <table {...props}>{children}</table>
    </div>
  )
}

export interface MarkdownProps {
  readonly content: string
  /**
   * True while tokens are still arriving.
   *
   * <p>Switches to a plain, streaming-safe rendering. See the note at the top of this file.
   */
  readonly streaming?: boolean
}

export function Markdown({ content, streaming = false }: MarkdownProps) {
  if (streaming) {
    return (
      <div className="markdown markdown--streaming">
        <pre className="markdown__raw">{content}</pre>
      </div>
    )
  }

  return (
    <div className="markdown">
      <ReactMarkdown
        remarkPlugins={[remarkGfm, remarkMath]}
        rehypePlugins={[rehypeKatex]}
        components={{ code, table }}
      >
        {content}
      </ReactMarkdown>
    </div>
  )
}

export { HIGHLIGHT_LANGUAGES }