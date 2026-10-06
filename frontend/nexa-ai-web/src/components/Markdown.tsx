/**
 * Renders an assistant message as Markdown.
 *
 * ## Why a real renderer and not pre-formatted text
 *
 * Models emit Markdown, and prose that arrives as a wall of unrendered `**bold**` and fenced
 * backticks is unreadable. Rendering it properly also means the common structures — code, tables,
 * maths — get the treatment they were written for instead of being approximated with whitespace.
 *
 * ## Why rendering is disabled while tokens are arriving
 *
 * Partially-rendered Markdown is re-parsed on every chunk, and a half-written fence or a
 * half-written `$…$` makes the parser do the wrong thing *and* throws away the reader's scroll
 * position and text selection. So during a stream the raw text is shown; on `done` it is rendered
 * once. The transition is jarring enough to be worth it, and the alternative is worse.
 *
 * ## Why maths is loaded on demand
 *
 * KaTeX, `rehype-katex` and `remark-math` together are the heaviest dependencies in the
 * application, and the majority of answers contain no formula at all. Shipping them in the initial
 * payload makes every user pay for every message's worst case.
 *
 * So {@link MarkdownMath} is a `React.lazy` chunk, fetched only when the text actually contains a
 * maths delimiter. Until it arrives the message renders through the ordinary path, which is
 * complete except for maths — so a reader gets a fully formatted message a moment before the
 * maths appears, rather than a spinner.
 *
 * The same reasoning applies to syntax highlighting, which is `import()`-ed inside
 * {@link MarkdownCode} on first use. See `markdownRenderers.tsx`.
 *
 * ## Untrusted input
 *
 * Everything here renders model output, which is influenced by whatever the user asked and by
 * whatever was in the retrieved context. `react-markdown` does not execute HTML by default and
 * `remark-gfm` does not add any, so there is no path from a model's output to script execution.
 * That default is the reason this component does not reach for `rehype-raw`.
 */

import { Suspense, lazy } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'

import { MarkdownCode, MarkdownTable } from './markdownRenderers'

/**
 * The maths-capable renderer.
 *
 * `React.lazy` rather than a dynamic `rehypePlugins` entry: unified does not await plugins, so a
 * promised plugin is silently ignored and maths renders as literal `$x$` with nothing logged.
 * A separate component is the only version of this that actually works.
 */
const MarkdownMath = lazy(() => import('./MarkdownMath'))

/**
 * Detects a maths delimiter.
 *
 * <p>Deliberately a cheap scan rather than a parse. `remark-math` is most of what the maths
 * feature costs, so deciding whether to load it cannot itself require it. A false positive loads
 * KaTeX for a message with no maths in it — cheap and harmless. A false negative would render
 * `$x$` as literal text, which is not.
 *
 * <p>The escaped forms matter: a model answering a question about the dollar sign writes `\$`,
 * and treating that as maths produces visibly broken output.
 */
const MATH_PATTERN =
  /(\$\$[\s\S]+?\$\$|\\\[[\s\S]+?\\\]|\\\([\s\S]+?\\\)|(^|[^\\$])\$(?!\s*$)[^$\n]+\$)/m

/**
 * Whether this message needs the maths renderer at all.
 *
 * <p>Not exported. Nothing outside this module needs it, and a component file that also exports a
 * plain function loses fast refresh for the whole file.
 */
function containsMath(content: string): boolean {
  return MATH_PATTERN.test(content)
}

const components = { code: MarkdownCode, table: MarkdownTable }

/** The ordinary renderer: Markdown, GFM tables, code blocks. No maths. */
function Plain({ content }: { readonly content: string }) {
  return (
    <div className="markdown">
      <ReactMarkdown remarkPlugins={[remarkGfm]} components={components}>
        {content}
      </ReactMarkdown>
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

  if (containsMath(content)) {
    // The fallback is the ordinary renderer rather than a spinner: it renders everything except
    // the maths, so the message is complete and readable a moment before the maths appears.
    return (
      <Suspense fallback={<Plain content={content} />}>
        <MarkdownMath content={content} />
      </Suspense>
    )
  }

  return <Plain content={content} />
}