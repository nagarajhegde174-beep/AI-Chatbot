# DESIGN.md — Frontend Architecture and Visual System

How the NexaAI client is organised and how it looks. Architecture: [`ARCHITECTURE.md`](ARCHITECTURE.md) §9.
Prohibitions: [`RULES.md`](RULES.md) §5.

Phase 0 establishes the toolchain, the token layer and the application shell. Product screens
arrive in their own phases ([`TASKS.md`](TASKS.md)).

---

## 1. Stack

| Concern | Choice | Why this and not the alternative |
|---|---|---|
| Framework | React 19 | Required. Component model fits a chat UI with a lot of local state. |
| Build tool | Vite | Fast dev server, native ESM, TS support without a separate compiler chain. |
| Language | TypeScript, `strict` | `strict`, `noUncheckedIndexedAccess` and `noUnusedLocals` on. A chat UI is full of optional streaming fields and array indexing, which is exactly where loose typing hides bugs. |
| UI kit | Bootstrap 5.3 | Required over Tailwind: the visual language stays in one themed stylesheet instead of being scattered through markup. |
| Styling | SCSS | Required. Gives tokens, nesting and theming that CSS custom properties alone do not express cleanly. |
| Icons | Lucide React | Consistent 24px grid, tree-shakeable, no icon font. |
| Lint | oxlint | Fast, no config sprawl. |

**Not used, and not to be introduced:** Tailwind, Next.js, Vue, Angular
([`RULES.md`](RULES.md) §5).

### 1.1 Why no Next.js

NexaAI's rendering is entirely client-driven behind an authenticated API Gateway. SSR would add
a server runtime that owns no data and cannot render anything useful without the browser's
session anyway.

---

## 2. Source layout

```
frontend/nexa-ai-web/
├── index.html                  data-bs-theme is set here; no inline logic
├── vite.config.ts              dev proxy for /api, SCSS deprecation silencing
├── tsconfig.app.json           strict app compilation
├── tsconfig.node.json          strict config-file compilation
├── .oxlintrc.json
├── .env.example                public values only
└── src/
    ├── main.tsx                entry point; imports Bootstrap JS once
    ├── App.tsx                 shell, routing, layout
    ├── api/
    │   ├── client.ts           the ONLY place fetch is called
    │   ├── auth.ts
    │   ├── chat.ts
    │   ├── documents.ts
    │   ├── rag.ts
    │   ├── subscription.ts
    │   └── admin.ts
    ├── features/               one folder per bounded area
    │   ├── auth/
    │   ├── chat/
    │   ├── documents/
    │   ├── subscription/
    │   └── admin/
    ├── components/             shared presentational components
    ├── hooks/                  reusable behaviour: useSse, useAuth, useTheme
    ├── types/                  shared TypeScript types
    ├── utils/                  pure helpers, no side effects
    └── styles/
        ├── _tokens.scss        colour, type, space, radius, elevation
        ├── _theme.scss         light / dark / system
        ├── _app.scss           component styles
        └── main.scss           entry: Bootstrap, then tokens, theme, app
```

### 2.1 Layer rules

Dependencies point in one direction only:

```
components  ──▶  hooks  ──▶  api  ──▶  fetch
     │                        │
     └──────▶  types    ◀──────┘
                 ▲
              utils
```

- `components/` and `features/` never import each other directly across features. Shared
  behaviour moves to `components/` or `hooks/`.
- `api/` knows about HTTP and nothing about React.
- `utils/` is pure and has no imports from the rest of the app, which makes it trivially
  testable.
- No module reaches around `api/` to call `fetch` directly. This is the rule that keeps auth
  headers, error mapping and correlation ids in one place.

---

## 3. Visual system

### 3.1 Principles

1. **Calm, not loud.** The user is reading generated text. The interface must not compete with it.
2. **One accent.** Colour is reserved for action and state, never decoration.
3. **Readable at length.** 16px body, 1.6 line height, measure capped at ~72 characters.
4. **Dark mode is a first-class theme,** not an inverted afterthought.
5. **Consistent spacing,** from one scale. No magic numbers in components.

### 3.2 Tokens

Defined once in `_tokens.scss` as SCSS variables, then exposed as CSS custom properties so
Bootstrap components inherit them.

| Token group | Examples |
|---|---|
| Colour | `--nexa-bg`, `--nexa-surface`, `--nexa-text`, `--nexa-muted`, `--nexa-accent`, `--nexa-success`, `--nexa-warning`, `--nexa-danger` |
| Type | font families, `--nexa-font-size-*`, line heights, letter spacing |
| Space | 4px base scale: `--nexa-space-1` … `--nexa-space-9` |
| Radius | `--nexa-radius-sm/md/lg/pill` |
| Elevation | `--nexa-shadow-sm/md/lg` |
| Motion | `--nexa-transition-fast/base`, `--nexa-easing` |

Bootstrap's own variables (`--bs-body-bg`, `--bs-body-color`, `--bs-border-color`, and the
`primary`/`success`/`warning`/`danger` ramps) are reassigned from these tokens, so
`btn-primary` and `alert-danger` follow the theme without any component-level overrides. That
is why the UI uses Bootstrap classes directly rather than hand-rolled CSS.

### 3.3 Theming

Bootstrap's `data-bs-theme` attribute on `<html>` is the single switch:

- `light` — explicit
- `dark` — explicit
- *(attribute absent)* — follow the OS via `prefers-color-scheme`

`_theme.scss` defines each token set once per theme. Adding a theme means adding one block, not
touching components.

Rules:
- `color-scheme` is declared in CSS so native scrollbars and form controls match.
- The theme is applied before first paint to avoid a flash of the wrong theme.
- Theme choice is a user preference and belongs in `localStorage` (a UI preference, not a
  secret) — see [`ARCHITECTURE.md`](ARCHITECTURE.md) §11 and [`SECURITY.md`](SECURITY.md) §3.

### 3.4 Accessibility

Part of the definition of done, not an afterthought:

- Semantic elements: `<main>`, `<nav>`, `<button>`, `<form>`, headings in order.
- Visible focus on every interactive element; never `outline: none` without a replacement.
- Contrast meeting WCAG AA in **both** themes.
- Icons are decorative and marked `aria-hidden`; an icon-only button carries an accessible name.
- Errors use `role="alert"`; status uses `role="status"`, so a screen reader announces them.
- Streaming output goes into a `role="log"` live region.
- All interactive targets at least 44×44px on touch.

---

## 4. Layout

### 4.1 Breakpoints

Bootstrap's standard set. The layout is mobile-first and usable at 320px.

### 4.2 Public

```
┌──────────────────────────────────────┐
│  navbar: wordmark · sign in · join   │
├──────────────────────────────────────┤
│  value proposition                   │
│  how it works                        │
│  sample                             │
│  plans                              │
│  footer                             │
└──────────────────────────────────────┘
```

### 4.3 Authenticated application

```
┌────────────┬─────────────────────────────────┐
│  sidebar   │  header: conversation title,   │
│  new chat  │           model selector       │
│  list of   ├─────────────────────────────────┤
│  chats     │                                 │
│  documents │   message thread (scrolls)     │
│  usage     │                                 │
│  settings  │   ┌───────────────────────────┐ │
│            │   │ composer + send           │ │
│            │   └───────────────────────────┘ │
└────────────┴─────────────────────────────────┘
```

Sidebar collapses to an off-canvas drawer below `lg`. The composer stays reachable without the
page scrolling.

### 4.4 Admin

A separate shell with its own navigation: overview, users, models, plans, analytics, audit log.
Distinct because the information architecture is different, not merely to signal importance.

---

## 5. The chat interface

The most important screen, so it gets the most specific requirements.

### 5.1 Message thread

- User messages right-aligned, assistant messages left-aligned.
- Assistant messages render **markdown**, sanitised. Unsanitised markdown from a model is an
  XSS vector.
- A grounded answer shows citations as numbered chips linking to the source chunk. An answer with
  retrieved context and no citation is a defect ([`PRD.md`](PRD.md) §7.3).
- Timestamps on hover or focus, not permanently — they compete with the text.
- Regenerate, copy, and thumbs feedback on assistant messages.
- Streaming is visibly in progress: a caret while tokens arrive, and the message is marked
  incomplete if the stream was cut.

### 5.2 Streaming

```ts
const source = new EventSource(`/api/v1/chat/${id}/messages`, { /* ... */ })
source.addEventListener('token', (e) => appendToken(e.data))
source.addEventListener('done', () => { source.close(); markComplete() })
source.addEventListener('error', () => { source.close(); markInterrupted() })
```

Server-Sent Events, not WebSocket: the transport is one-directional
([`ARCHITECTURE.md`](ARCHITECTURE.md) §9.2). Every proxy hop must disable buffering, or the
stream arrives all at once at the end.

### 5.3 Composer

- Auto-growing textarea, Enter to send, Shift+Enter for a newline.
- Disabled while a generation is in flight, unless the user explicitly stops it.
- Character and document-context indicators.
- No "send" button without a label; the icon is decorative.

### 5.4 Model selector

Shows model name, provider and a rough relative cost. Changing the model resets the memory
window, so the UI warns first ([`PRD.md`](PRD.md) §4.2).

---

## 6. State

Four kinds, kept separate on purpose:

| Kind | Where | Examples |
|---|---|---|
| Server state | API client + small cache | conversations, documents, plans |
| Session state | React context | current user, entitlements |
| Ephemeral UI | local component state | sidebar open, draft text |
| Preferences | `localStorage` | theme, sidebar width |

**Rules.**
- No global store library in Phase 0. It is added only if a real need appears, because a store
  added speculatively becomes a second source of truth.
- Server state is never duplicated into a global store; that is how stale data bugs start.
- `localStorage` holds UI preferences only. **Never a secret** — and access tokens are kept in
  memory, with refresh on load ([`SECURITY.md`](SECURITY.md) §3).

---

## 7. Error and empty states

Every list has three states, designed rather than defaulted:

1. **Loading** — a skeleton that matches the final layout, so nothing shifts when data arrives.
2. **Empty** — says what will appear here and how to make it appear.
3. **Error** — states what failed in plain language, and offers the one action that helps.

An error message never shows a stack trace or a raw server message. It may show the correlation
id, because that is how a user can report a real problem.

---

## 8. Performance budget

| Metric | Target |
|---|---|
| Initial JS (gzip) | < 200 kB |
| LCP | < 2.0 s on a throttled 4G profile |
| CLS | < 0.1 |
| Time to first streamed token | < 1.5 s for a short answer |
| Route change | < 200 ms |

Streaming latency is a product feature, not just a metric ([`PRD.md`](PRD.md) §4.3).

---

## 9. Frontend security

The frontend holds **no secret** ([`RULES.md`](RULES.md) §6). Vite inlines every `VITE_*`
variable, so anything prefixed `VITE_` is public.

| Concern | Rule |
|---|---|
| AI provider keys | **Never in the frontend.** Only the AI Service has them. |
| Payment key secret | **Never.** Only the public test key id is ever needed client-side. |
| Authorisation | Never trusted from the client. The UI hides things; the server enforces them. |
| Markdown | Sanitised before rendering. Model output is untrusted input. |
| HTML injection | No `dangerouslySetInnerHTML` on unsanitised content. |
| Tokens | In memory, refreshed on load. Never in a URL, never in a log. |
| CSP | Set at the edge, tuned once the Phase 10 frontend work settles the real policy. A wrong CSP in Phase 0 would break the app in a way that looks like a bug. |

CI greps both the source and the built bundle for credential-shaped strings
([`RULES.md`](RULES.md) §6).