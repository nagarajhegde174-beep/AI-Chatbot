# nexa-ai-web

NexaAI web client. React + Vite + TypeScript + Bootstrap + SCSS.

Tailwind is prohibited by `docs/RULES.md` §5. The design system is in `docs/DESIGN.md`.

---

## Commands

```bash
npm install        # install dependencies
npm run dev        # dev server on http://localhost:5173, /api proxied to the gateway
npm run build      # type-check with tsc -b, then build to dist/
npm run lint       # oxlint
npm run preview    # serve the production build locally
```

`npm run build` runs the TypeScript compiler before Vite, so a type error fails the build
rather than shipping. `strict`, plus `noUncheckedIndexedAccess` and `noImplicitOverride`, are
enabled in `tsconfig.app.json`.

---

## Configuration

Only **public** values belong in a `VITE_*` variable, because Vite inlines them into the
JavaScript bundle where every user can read them.

| Variable | Purpose | Default |
|---|---|---|
| `VITE_API_BASE_URL` | Gateway base URL when the API is on another host | empty (same-origin) |
| `VITE_DEV_GATEWAY_URL` | Gateway URL used by the dev-server `/api` proxy | `http://localhost:8080` |

Copy `.env.example` to `.env.local` for local work. `.env.local` is gitignored.

**Never** put an OpenAI, Gemini, Groq or Razorpay key secret, a JWT signing key or a database
password in this project. Provider keys live in the AI Service's environment and never reach
the browser. See `docs/RULES.md` §6.

---

## API access

The frontend calls the **API Gateway** and nothing else — never a service port directly. That
keeps the browser on one origin, so cookies, CORS and streaming behave the same in development
and production, and it means the gateway is the single place where edge policy lives.

In development, `vite.config.ts` proxies `/api` to the gateway on port `8080`.

**Streaming.** Chat answers arrive as Server-Sent Events. `EventSource` cannot issue a `POST`
and cannot send an `Authorization` header, so answers are read with `fetch` plus
`ReadableStream` and parsed as SSE. That logic lives in one `useSse` hook so every streaming
surface behaves identically. The event contract is `docs/SERVICE_CONTRACTS.md` §4.3.

---

## Source layout

Target structure, from `docs/ARCHITECTURE.md` §11. Directories appear as the phase that needs
them arrives; empty speculative folders are not created.

```
src/
├── api/          typed HTTP and SSE clients, one module per service
├── app/          app shell, router, providers
├── components/   reusable presentational components
├── features/     auth · chat · documents · subscriptions · admin
├── hooks/        reusable behaviour (useSse, useTheme, usePagination)
├── layouts/      AuthLayout, ChatLayout, AdminLayout
├── pages/        route-level components
├── routes/       route table, protected and admin route wrappers
├── services/     business operations layered on top of api/
├── state/        auth context, theme context
├── styles/       SCSS: tokens, theme, application rules
├── types/        types mirroring docs/SERVICE_CONTRACTS.md
└── utils/        pure helpers
```

### Phase 0 contents

| File | Purpose |
|---|---|
| `src/main.tsx` | Entry point. Imports Bootstrap JS once and the SCSS entry stylesheet |
| `src/App.tsx` | Placeholder shell that proves React + TS + Bootstrap + SCSS render together |
| `src/styles/_tokens.scss` | Design token custom properties for light and dark |
| `src/styles/_theme.scss` | Bootstrap variable overrides, imported **before** Bootstrap |
| `src/styles/_app.scss` | The few application rules Bootstrap cannot express |
| `src/styles/main.scss` | Entry stylesheet; `@use` order is tokens → theme → app |

### Theming

Dark mode uses Bootstrap 5.3's native `data-bs-theme` attribute on `<html>`, so Bootstrap's
own components switch without a second build and no component needs a theme conditional.
`index.html` ships `data-bs-theme="light"`; the runtime switch between `light`, `dark` and
`system`, with live `prefers-color-scheme` following, is Phase 1 work.

The runtime switch must not re-mount the tree, so open conversations, scroll position and
in-flight streaming survive a theme change.

---

## Responsive behaviour

Bootstrap breakpoints only — `sm 576`, `md 768`, `lg 992`, `xl 1200`, `xxl 1400`. The chat
surface is one column on mobile with an off-canvas conversation drawer, a persistent rail from
`md`, and a third inspector column from `xl`. The chat column caps its prose at `820px` so line
length stays readable. The full specification is `docs/DESIGN.md` §3.

---

## Phase status

Phase 0 only: the toolchain, the Bootstrap/SCSS override pipeline, the dev proxy, and a
placeholder shell. No product screen, no router, no API call, no theme switcher. Those belong
to their phases in `docs/TASKS.md` and are not built early.
