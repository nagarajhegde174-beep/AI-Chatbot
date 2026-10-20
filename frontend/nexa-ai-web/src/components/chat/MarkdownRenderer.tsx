import { useState } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import remarkMath from 'remark-math'
import rehypeKatex from 'rehype-katex'
import rehypeHighlight from 'rehype-highlight'
import { Check, Copy } from 'lucide-react'

interface MarkdownRendererProps {
  readonly content: string
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
      // ignore
    }
  }

  if (!match) {
    return (
      <code className="bg-body-secondary px-1 py-0.5 rounded text-primary-emphasis font-monospace small" {...props}>
        {children}
      </code>
    )
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
        <code className={className} {...props}>
          {children}
        </code>
      </div>
    </div>
  )
}

export function MarkdownRenderer({ content }: MarkdownRendererProps) {
  return (
    <div className="markdown-content">
      <ReactMarkdown
        remarkPlugins={[remarkGfm, remarkMath]}
        rehypePlugins={[rehypeKatex, rehypeHighlight]}
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
        {content}
      </ReactMarkdown>
    </div>
  )
}
