/**
 * Application root.
 *
 * <p>Provider order matters and is not arbitrary:
 *
 * <ol>
 *   <li><strong>Theme</strong> first, so the very first paint already carries the right
 *       `data-bs-theme`. Wrapping it in anything that suspends would let a frame render in the
 *       wrong theme, which is the flash users notice.</li>
 *   <li><strong>Auth</strong> second, because the router reads it.</li>
 *   <li><strong>Router</strong> last, innermost, so routes can consume both.</li>
 * </ol>
 *
 * <p>This replaces the Phase 0 service-table placeholder. That placeholder was a genuine Phase 0
 * deliverable — it proved the toolchain compiled and rendered — and its job is now done.
 */

import { BrowserRouter } from 'react-router-dom'

import { AppRoutes } from './AppRoutes'
import { AuthProvider } from './auth/AuthContext'
import { ThemeProvider } from './theme/ThemeContext'

export default function App(): React.JSX.Element {
  return (
    <ThemeProvider>
      <AuthProvider>
        <BrowserRouter>
          <AppRoutes />
        </BrowserRouter>
      </AuthProvider>
    </ThemeProvider>
  )
}
