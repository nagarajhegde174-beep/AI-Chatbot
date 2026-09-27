import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'

// Bootstrap JS is imported once, at the entry point. Components drive it through the
// data-bs-* API so React owns state and Bootstrap owns behaviour.
import 'bootstrap/js/dist/collapse'
import 'bootstrap/js/dist/dropdown'
import 'bootstrap/js/dist/modal'
import 'bootstrap/js/dist/offcanvas'
import 'bootstrap/js/dist/tooltip'

import './styles/main.scss'
import App from './App'

const container = document.getElementById('root')

if (!container) {
  throw new Error('Root container #root is missing from index.html')
}

createRoot(container).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
