import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

/**
 * The frontend talks to the API Gateway and to nothing else. In development the gateway
 * runs on port 8080, so `/api` is proxied to it and the browser sees a single origin. This
 * keeps cookies, CORS and streaming identical between development and production.
 *
 * Server-Sent Events pass through this proxy unbuffered, so a streamed answer reaches the
 * browser as it is produced.
 */
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_')
  const gatewayTarget = env.VITE_DEV_GATEWAY_URL ?? 'http://localhost:8080'

  return {
    plugins: [react()],
    css: {
      preprocessorOptions: {
        scss: {
          // Bootstrap 5.3 still ships legacy Sass internally. These deprecations come from
          // node_modules, not from our code, so they are silenced here to keep real warnings
          // from our own stylesheets visible.
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
      sourcemap: mode !== 'production',
    },
  }
})
