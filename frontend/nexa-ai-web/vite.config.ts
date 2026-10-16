import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

/**
 * The frontend talks to the API Gateway and to nothing else. The browser never
 * calls a service port directly (docs/DESIGN.md §9.1), so in development the
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
    },
  }
})