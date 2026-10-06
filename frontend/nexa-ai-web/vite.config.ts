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

      // Emitted so the bundle budget check can tell which chunks the browser
      // downloads on first paint, as opposed to every chunk that exists. Without
      // it the check could only sum the whole `dist`, which penalises exactly
      // the code-splitting the budget is meant to encourage.
      manifest: true,

      // Grouping is left to the bundler rather than written out by hand here.
      //
      // Hand-written group patterns are a trap: the first version of this file grouped
      // everything matching /remark-|rehype-/ into a "math" chunk, which quietly swallowed
      // `remark-gfm` — a STATIC dependency of the plain Markdown renderer. The plain chunk then
      // imported the maths chunk, and the maths code was back on the critical path despite being
      // dynamically imported. The manifests made that visible; the sizes alone would not have.
      //
      // The real split comes from `React.lazy` and `import()` in the source, which is where it
      // belongs: a chunk boundary that a dependency rename can silently break is not a boundary.
    },
  }
})