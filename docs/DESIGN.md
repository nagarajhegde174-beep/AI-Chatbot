# UI / UX Design & Design System — NexaAI

## 1. Design Philosophy
NexaAI provides a modern, distraction-free, high-performance interface for multi-model AI conversations, visual workflow orchestration, and document intelligence. The interface is engineered with **Bootstrap 5 + SCSS** (Tailwind is prohibited).

---

## 2. Color Palette & Design Tokens

### 2.1 CSS Variables / Design Tokens
The design tokens are declared in `frontend/nexa-ai-web/src/styles/_theme.scss`:

```scss
:root {
  --nexa-primary: #6366f1;         /* Indigo accent */
  --nexa-primary-hover: #4f46e5;
  --nexa-accent: #8b5cf6;          /* Violet secondary */
  --nexa-success: #10b981;         /* Emerald */
  --nexa-warning: #f59e0b;         /* Amber */
  --nexa-danger: #ef4444;          /* Red */
  --nexa-info: #06b6d4;            /* Cyan */
  
  --nexa-font-sans: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  --nexa-font-mono: 'JetBrains Mono', 'Fira Code', Menlo, Monaco, Consolas, monospace;
}
```

### 2.2 Theme Modes: Light, Dark, and System
- **Dark Mode (Default)**: Deep obsidian backgrounds (`#0f172a`, `#1e293b`), crisp high-contrast typography (`#f8fafc`), reduced glare.
- **Light Mode**: Clean white/neutral canvas (`#ffffff`, `#f8fafc`), dark slate text (`#0f172a`), subtle card borders (`#e2e8f0`).
- **System Mode**: Automatically synchronizes with OS preference (`prefers-color-scheme`) via JavaScript listener and sets `data-bs-theme="light"` or `data-bs-theme="dark"`.

---

## 3. Responsive Breakpoints
- **Mobile (< 576px)**: Collapsible offcanvas sidebar, sticky bottom chat bar, full-screen workflow drawer, stacked card tables.
- **Tablet (576px - 992px)**: Mini icon-rail navigation, collapsible document drawer, two-column dashboard grids.
- **Desktop (>= 992px)**: Persistent multi-pane workspace: left conversation/navigation sidebar, central chat/workflow canvas, right context/citations inspector.

---

## 4. Key UI Components & Interactions

### 4.1 Chat & Streaming View
- **Model Selector Dropdown**: Grouped by provider (OpenAI, Gemini, Groq, Ollama) with latency badges and capability tags.
- **Message List**: Asymmetrical bubble layout (user right-aligned with accent border, assistant left-aligned with model avatar).
- **Markdown & Code Rendering**: Real-time markdown parsing with syntax highlighting, copy-to-clipboard button, and token streaming animation.
- **Citation Badges**: Interactive chips linking directly to retrieved document chunks with relevance confidence scores.

### 4.2 Visual Workflow Builder
- **Node-Based Canvas**: Visual DAG composed of rounded cards with distinct color-coded node headers:
  - *Trigger Nodes*: Cyan header (e.g., Manual, Webhook, Schedule).
  - *Action / Processing Nodes*: Indigo header (e.g., PDF Reader, AI Prompt, Summarizer, RAG Search).
  - *Condition Nodes*: Amber header (Branching logic).
  - *Output / Notification Nodes*: Emerald header (e.g., Save Result, Email Alert).
- **Node Configuration Drawer**: Slide-over panel for editing node parameters, tool permissions, and prompt templates.
- **Execution State Indicators**:
  - `IDLE`: Subtle gray badge.
  - `RUNNING`: Animated pulsing spinner.
  - `COMPLETED`: Vibrant emerald checkmark with duration badge.
  - `FAILED`: Red alert badge with expandable error stack trace.

### 4.3 Document Management & Ingestion Status
- **Drag-and-Drop Dropzone**: File validation with progress bars and MIME type validation.
- **Status Stepper**: Uploaded -> Extracting Text -> Generating Vectors -> Ready.

### 4.4 Admin Dashboard
- **Telemetry Charts**: Token consumption over time, active conversation volume, latency histograms.
- **User Management Table**: Searchable, paginated user list with role assignment (`USER` vs `ADMIN`) and account status toggles.

### 4.5 Component States
- **Loading**: Pulse skeletons (`.placeholder-glow`) and indeterminate progress bars.
- **Empty States**: Friendly illustrated SVG icons with clear Call-to-Action (CTA) buttons.
- **Error States**: Non-blocking toast notifications and contextual inline alert banners with retry buttons.
