/**
 * Markdown with maths.
 *
 * ## Why this is a separate file
 *
 * KaTeX plus `rehype-katex` plus `remark-math` is the single heaviest dependency in the
 * application, and the overwhelming majority of answers contain no formula at all. If they were
 * imported here and this module were imported by the renderer, every user would download them on
 * every page load.
 *
 * So this file is a separate **chunk**, fetched only when {@link Markdown} decides a message
 * actually contains maths. Nothing else in the application imports it, which is what makes the
 * split work.
 *
 * ## Why the maths plugins are static imports here
 *
 * They cannot be lazy, and the reason is specific: unified does not await plugins. Passing a
 * promise in `rehypePlugins` is silently ignored, so a "lazy" maths plugin would render maths as
 * literal `$x$` with no error anywhere. Making the whole renderer a lazy component is the only
 * version of this that actually works.
 */

import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import remarkMath from 'remark-math'
import rehypeKatex from 'rehype-katex'

import { MarkdownCode, MarkdownTable } from './markdownRenderers'

export default function MarkdownMath({ content }: { readonly content: string }) {
  return (
    <div className="markdown">
      <ReactMarkdown
        remarkPlugins={[remarkGfm, remarkMath]}
        rehypePlugins={[rehypeKatex]}
        components={{ code: MarkdownCode, table: MarkdownTable }}
      >
        {content}
      </ReactMarkdown>
    </div>
  )
}