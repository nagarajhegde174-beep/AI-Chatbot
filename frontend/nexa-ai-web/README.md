# nexa-ai-web

The NexaAI web client. React + Vite + TypeScript + Bootstrap 5 + SCSS, Lucide icons.

**Phase 0: this is a toolchain shell, not a product.** It exists to prove that the frontend
stack compiles, type-checks, lints and builds. Product screens arrive in Phase 10
([`../../docs/TASKS.md`](../../docs/TASKS.md)), with the auth screens in Phase 3.

See [`../../docs/DESIGN.md`](../../docs/DESIGN.md) for the frontend architecture and visual
system.

---

## Stack

| Concern | Choice | Why |
|---|---|---|
| Framework | React 19 | Component model suits a stateful chat UI |
| Build | Vite 8 | Fast dev server, native ESM, first-class TS |
| Language | TypeScript, `strict` | Plus `noUncheckedIndexedAccess`, `noUnusedLocals`, `verbatimModuleSyntax` |
| UI | Bootstrap 5.3 | The design language stays in one themed stylesheet |
| Styling | SCSS | Tokens, nesting, and themeable CSS custom properties |
| Icons | `lucide-react` | Consistent grid, tree-shakeable, no icon font |
| Lint | `oxlint` | Fast, minimal config |

**Not used, and prohibited:** Tailwind, Next.js, Vue, Angular
([`../../docs/RULES.md`](../../docs/RULES.md) §5).

---

## Commands

```bash
npm install
npm run dev          # http://localhost:5173, /api proxied to the gateway on :8080
npm run lint         # oxlint
npm run typecheck    # tsc -b
npm run build        # tsc -b && vite build  ->  dist/
npm run preview      # serve the production build locally
```

`npm run build` runs `tsc -b` **first**, so a type error fails the build rather than shipping.

---

## Layout

```
src/
├── main.tsx           entry point; imports Bootstrap JS plugins once
├── App.tsx            shell; the Phase 0 status screen
└── styles/
    ├── _tokens.scss   design tokens: type, space, radius, motion, brand
    ├── _theme.scss    light / dark / system; remaps Bootstrap's variables
    ├── _app.scss      component styles
    └── main.scss      entry: Bootstrap, then tokens, theme, app
```

`api/`, `features/`, `components/`, `hooks/`, `types/` and `utils/` appear as their phases
arrive. They are **not** created empty now: a directory of empty folders is not a structure,
it is clutter ([`../../docs/DECISIONS.md`](../../docs/DECISIONS.md) ADR-015).

---

## Rules that apply here

1. **No secrets, ever.** Vite inlines every `VITE_*` variable into the bundle, so anything
   prefixed `VITE_` is public. AI provider keys, the Razorpay key secret and the JWT signing
   key are **server-side only** ([`../../docs/RULES.md`](../../docs/RULES.md) §6).
2. **The gateway is the only API entry point.** The browser never calls a service port. In
   development, `vite.config.ts` proxies `/api` so requests are same-origin.
3. **Bootstrap classes, not utility soup.** Visual changes go in `_theme.scss` and
   `_app.scss`, not into markup.
4. **Streaming must not be buffered.** Every proxy hop needs `proxy_buffering off`
   ([`../../docs/DECISIONS.md`](../../docs/DECISIONS.md) ADR-021).
5. **Accessibility is part of done.** Semantic elements, visible focus, labelled controls, AA
   contrast in both themes ([`../../docs/DESIGN.md`](../../docs/DESIGN.md) §3.4).

---

## Environment

```bash
cp .env.example .env.local
```

`.env.local` is gitignored and must never be committed. It may only contain public values —
see the warning at the top of `.env.example`.

In development `VITE_API_BASE_URL` stays empty, because the dev server proxies `/api` to
`VITE_DEV_GATEWAY_URL`.

---

## Verified in Phase 0

| Check | Result |
|---|---|
| `npm install` | 39 packages, no errors |
| `npm run lint` | 0 warnings, 0 errors |
| `npm run build` (`tsc -b` + `vite build`) | Succeeds |
| Bundle size | JS 92.6 kB gzip, CSS 31.5 kB gzip — within the 200 kB budget |
| Credential scan of `dist/` | Clean |

**The Docker image is not verified.** The build needs no Docker daemon, so
`Dockerfile` and `nginx.conf` have not been executed
([`../../docs/MEMORY.md`](../../docs/MEMORY.md) §6.1).