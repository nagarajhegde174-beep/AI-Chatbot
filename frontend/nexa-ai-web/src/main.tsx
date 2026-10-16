/**
 * Application entry point.
 *
 * Bootstrap's JavaScript is imported once, here, rather than per component.
 * Components drive it through the data-bs-* API, so React owns state and
 * Bootstrap owns behaviour, and the two do not fight over the DOM.
 *
 * Only the plugins actually used are imported. The full bundle imports every
 * Bootstrap plugin including ones this project will never use.
 */

import 'bootstrap/js/dist/collapse'
import 'bootstrap/js/dist/dropdown'
import 'bootstrap/js/dist/modal'
import 'bootstrap/js/dist/offcanvas'
import 'bootstrap/js/dist/tooltip'

import './styles/main.scss'

import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'

import App from './App'

const container = document.getElementById('root')

if (!container) {
  // Failing loudly beats rendering into null and leaving the developer with an
  // unexplained blank page.
  throw new Error('Root container #root is missing from index.html')
}

createRoot(container).render(
  <StrictMode>
    <App />
  </StrictMode>,
)