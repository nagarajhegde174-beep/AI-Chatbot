import { useState, useMemo } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { Check, Copy } from 'lucide-react'
import hljs from 'highlight.js/lib/core'
import javascript from 'highlight.js/lib/languages/javascript'
import typescript from 'highlight.js/lib/languages/typescript'
import python from 'highlight.js/lib/languages/python'
import java from 'highlight.js/lib/languages/java'
import json from 'highlight.js/lib/languages/json'
import bash from 'highlight.js/lib/languages/bash'
import sql from 'highlight.js/lib/languages/sql'
import xml from 'highlight.js/lib/languages/xml'
import css from 'highlight.js/lib/languages/css'

// Register common languages into highlight.js core
hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('js', javascript)
hljs.registerLanguage('typescript', typescript)
hljs.registerLanguage('ts', typescript)
hljs.registerLanguage('python', python)
hljs.registerLanguage('py', python)
hljs.registerLanguage('java', java)
hljs.registerLanguage('json', json)
hljs.registerLanguage('bash', bash)
hljs.registerLanguage('sh', bash)
hljs.registerLanguage('sql', sql)
hljs.registerLanguage('html', xml)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('css', css)

interface MarkdownRendererProps {
  readonly content: string
}

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
}

/**
 * Basic LaTeX to styled HTML translator.
 * Handles fractions, square roots, super/subscripts, Greek letters, and math symbols.
 */
function formatBasicLatex(latex: string): string {
  let s = latex.trim()

  // Greek letters
  s = s.replace(/\\alpha\b/g, 'α')
    .replace(/\\beta\b/g, 'β')
    .replace(/\\gamma\b/g, 'γ')
    .replace(/\\delta\b/g, 'δ')
    .replace(/\\epsilon\b/g, 'ε')
    .replace(/\\theta\b/g, 'θ')
    .replace(/\\lambda\b/g, 'λ')
    .replace(/\\mu\b/g, 'μ')
    .replace(/\\pi\b/g, 'π')
    .replace(/\\sigma\b/g, 'σ')
    .replace(/\\phi\b/g, 'φ')
    .replace(/\\omega\b/g, 'ω')
    .replace(/\\Delta\b/g, 'Δ')
    .replace(/\\Sigma\b/g, 'Σ')
    .replace(/\\Omega\b/g, 'Ω')

  // Math operators and symbols
  s = s.replace(/\\times\b/g, '×')
    .replace(/\\cdot\b/g, '·')
    .replace(/\\div\b/g, '÷')
    .replace(/\\pm\b/g, '±')
    .replace(/\\leq\b/g, '≤')
    .replace(/\\geq\b/g, '≥')
    .replace(/\\neq\b/g, '≠')
    .replace(/\\approx\b/g, '≈')
    .replace(/\\infty\b/g, '∞')
    .replace(/\\int\b/g, '∫')
    .replace(/\\sum\b/g, '∑')
    .replace(/\\to\b/g, '→')
    .replace(/\\in\b/g, '∈')
    .replace(/\\subset\b/g, '⊂')

  // Fractions: \frac{num}{den}
  s = s.replace(
    /\\frac\{([^{}]+)\}\{([^{}]+)\}/g,
    '<span class="d-inline-flex flex-column align-items-center align-middle mx-1" style="font-size:0.9em; vertical-align:middle; line-height:1.1"><span class="border-bottom border-secondary border-opacity-50 px-1">$1</span><span class="px-1">$2</span></span>',
  )

  // Square roots: \sqrt{x}
  s = s.replace(
    /\\sqrt\{([^{}]+)\}/g,
    '<span class="align-middle mx-1">&radic;<span style="border-top:1px solid currentColor; padding-left:1px;">$1</span></span>',
  )

  // Superscripts and subscripts
  s = s.replace(/\^{([^{}]+)}/g, '<sup>$1</sup>')
    .replace(/\^([0-9a-zA-Z+-])/g, '<sup>$1</sup>')
    .replace(/_{([^{}]+)}/g, '<sub>$1</sub>')
    .replace(/_([0-9a-zA-Z+-])/g, '<sub>$1</sub>')

  // \text{...}
  s = s.replace(/\\text\{([^{}]+)\}/g, '<span class="font-sans">$1</span>')

  return s
}

function CodeBlock({
  className,
  children,
  ...props
}: React.HTMLAttributes<HTMLElement>) {
  const [copied, setCopied] = useState(false)
  const match = /language-(\w+)/.exec(className || '')
  const language = match ? match[1] : ''
  const codeText = String(children).replace(/\n$/, '')

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(codeText)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      // ignore clipboard failures
    }
  }

  // Inline code
  if (!match) {
    return (
      <code className="bg-body-secondary px-1 py-0.5 rounded text-primary-emphasis font-monospace small" {...props}>
        {children}
      </code>
    )
  }

  // Syntax highlighting
  let highlightedHtml = ''
  if (language && hljs.getLanguage(language)) {
    try {
      highlightedHtml = hljs.highlight(codeText, { language, ignoreIllegals: true }).value
    } catch {
      highlightedHtml = escapeHtml(codeText)
    }
  } else {
    highlightedHtml = escapeHtml(codeText)
  }

  return (
    <div className="position-relative my-2 rounded border overflow-hidden bg-dark text-light">
      <div className="d-flex align-items-center justify-content-between px-3 py-1 bg-secondary bg-opacity-25 border-bottom border-secondary border-opacity-25 text-secondary small">
        <span className="font-monospace text-uppercase" style={{ fontSize: '0.72rem' }}>
          {language}
        </span>
        <button
          type="button"
          className="btn btn-sm btn-link text-secondary p-0 text-decoration-none d-flex align-items-center gap-1"
          onClick={handleCopy}
          title="Copy code"
        >
          {copied ? <Check size={14} className="text-success" /> : <Copy size={14} />}
          <span style={{ fontSize: '0.72rem' }}>{copied ? 'Copied' : 'Copy'}</span>
        </button>
      </div>
      <div className="p-3 overflow-x-auto font-monospace small mb-0">
        <pre className="mb-0">
          <code
            className={`hljs ${className || ''}`}
            dangerouslySetInnerHTML={{ __html: highlightedHtml }}
          />
        </pre>
      </div>
    </div>
  )
}

/**
 * Pre-processes LaTeX math expressions into HTML tokens before ReactMarkdown renders.
 * Handles block math ($$...$$) and inline math ($...$).
 */
function processMathExpressions(markdown: string): string {
  // 1. Block math: $$ ... $$
  let text = markdown.replace(/\$\$([\s\S]+?)\$\$/g, (_, math: string) => {
    const formatted = formatBasicLatex(math)
    return `\n\n<div class="my-2 p-2 text-center bg-body-tertiary rounded font-monospace small border">${formatted}</div>\n\n`
  })

  // 2. Inline math: $ ... $ (avoid matching single dollar signs like $50 or empty $$)
  text = text.replace(/(^|[^\\])\$([^\s$](?:[^$]*[^\s$])?)\$/g, (_, prefix: string, math: string) => {
    const formatted = formatBasicLatex(math)
    return `${prefix}<span class="px-1 py-0.5 bg-body-tertiary rounded font-monospace small text-primary-emphasis">${formatted}</span>`
  })

  return text
}

export function MarkdownRenderer({ content }: MarkdownRendererProps) {
  const processedContent = useMemo(() => processMathExpressions(content), [content])

  return (
    <div className="markdown-content">
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          code: CodeBlock,
          table: ({ children }) => (
            <div className="table-responsive my-2">
              <table className="table table-sm table-bordered mb-0">{children}</table>
            </div>
          ),
          a: ({ href, children }) => (
            <a href={href} target="_blank" rel="noopener noreferrer" className="text-decoration-underline text-primary">
              {children}
            </a>
          ),
        }}
      >
        {processedContent}
      </ReactMarkdown>
    </div>
  )
}
