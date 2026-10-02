# NGINX configuration for NexaAI

| File | Used by | Purpose |
|---|---|---|
| `nexaai.conf` | the `edge` Compose service | The full edge: static assets, rate-limit zones, security headers, SSE, `/api` proxy to the gateway |
| `../../frontend/nexa-ai-web/nginx.conf` | the frontend image | Serves the built bundle and proxies `/api` to the gateway |

There are two files because they are **different images with different build contexts**:
`nexaai.conf` belongs to the infrastructure tier, and the frontend config ships inside the image
built from `frontend/nexa-ai-web`. Duplicating the API proxying rules is unfortunate, and it is
still the lesser evil: sharing one file across two build contexts would mean one of the images
depends on a file outside its own directory, which is exactly the coupling this project avoids
([`../../../docs/DECISIONS.md`](../../../docs/DECISIONS.md) ADR-012).

## The blocks that matter most

**`~ ^/api/v1/chat/.*/messages$`** — Server-Sent Events. `proxy_buffering off` is what makes
streaming work. With buffering on, NGINX holds every token until the answer completes, and the
user sees nothing and then everything
([`../../../docs/DECISIONS.md`](../../../docs/DECISIONS.md) ADR-021). A regex location takes
priority over a prefix location, so this matches before `/api/`.

**`/api/v1/auth/`** — a stricter rate limit on credential endpoints. NGINX picks the longest
matching prefix, so this wins over `/api/` for auth paths. That is the intent, not a
duplication.

**`~ ^/(internal|actuator)/` — `deny all`** — internal endpoints are unreachable from outside.
The gateway refuses these too; two independent locks, because one configuration mistake should
not expose internals ([`../../../docs/SECURITY.md`](../../../docs/SECURITY.md) §10).

## No Content-Security-Policy yet

Deliberately. The real policy depends on how Phase 10 builds the frontend, and a wrong CSP in
Phase 0 would break the application in a way that looks like a bug. Tracked in Phase 10, not
forgotten ([`../../../docs/SECURITY.md`](../../../docs/SECURITY.md) §13).

## STATUS: not yet loaded

Neither file has been parsed by NGINX. There was no Docker daemon available when Phase 0 was
written. See [`../../../docs/MEMORY.md`](../../../docs/MEMORY.md) §6.1.

`nexaai.conf` also cannot start until Phase 3, because `upstream api-gateway` does not resolve
while the gateway does not exist. That is why the `edge` service sits behind its own Compose
profile.