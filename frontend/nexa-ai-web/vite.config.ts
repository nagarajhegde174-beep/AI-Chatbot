import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

/**
 * The frontend talks to the API Gateway and to nothing else. The browser never
 * calls a service port directly (docs/ARCHITECTURE.md 9.1), so in development the
 * `/api` prefix is proxied to the gateway and the browser sees a single origin.
 *
 * That keeps cookies, CORS and streaming identical between development and
 * production, and it means the same unbuffered SSE path is exercised locally.
 */
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_')
  const gatewayTarget = env.VITE_DEV_GATEWAY_URL ?? 'http://localhost:8080'

  return {
    plugins: [react()],
    css: {
      preprocessorOptions: {
        scss: {
          // Bootstrap 5.3 ships legacy Sass internally. These deprecations come
          // from node_modules, not from our stylesheets, so they are silenced here
          // to keep real warnings from our own SCSS visible.
          silenceDeprecations: ['import', 'if-function', 'global-builtin', 'color-functions'],
        },
      },
    },
    server: {
      port: 5173,
      proxy: {
        '/api': { target: gatewayTarget, changeOrigin: true },
      },
    },
    build: {
      outDir: 'dist',
      // Source maps only outside production: useful in development, and a
      // needless information disclosure in a deployed bundle.
      sourcemap: mode !== 'production',

      // Rendering a model answer pulls in react-markdown, KaTeX and
      // highlight.js, which together are several times the size of the
      // application itself. Bundled into one file they push the main chunk past
      // the warning threshold and — the part that actually matters — they
      // invalidate the entire application chunk whenever a markdown or
      // highlighting dependency is upgraded.
      //
      // Split into named chunks, the app chunk stays small and each of these is
      // cached independently. This is a cache- and threshold-driven split, not a
      // claim that the total got smaller: the bytes are all still downloaded for
      // a page containing a code block.
      rolldownOptions: {
        output: {
          advancedChunks: {
            groups: [
              {
                name: 'math',
                test: /node_modules[\\/](katex|rehype-katex)/,
              },
              {
                name: 'highlight',
                test: /node_modules[\\/]highlight\.js/,
              },
              {
                name: 'markdown',
                test: /node_modules[\\/](react-markdown|remark-|rehype-|unified|micromark|mdast|hast|unist-|vfile|parse5|property-information|space-separated-tokens|comma-separated-tokens|zwitch|html-url-attributes|trim-lines|devlop|ccount|escape-string-regexp|markdown-table|bail|trough|longest-streak)/,
              },
              {
                name: 'react',
                test: /node_modules[\\/](react|react-dom|scheduler|react-router)/,
              },
            ],
          },
        },
      },
    },
  }
})