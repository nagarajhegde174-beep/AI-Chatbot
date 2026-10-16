# =====================================================================
# nexa-ai-web image.
#
# Built from THIS directory only. The build context is frontend/nexa-ai-web,
# so nothing outside it — including the backend services and the infrastructure
# tier — can be reached from this build. That is deliberate: a frontend image
# has no business compiling a Spring Boot service.
#
# Two stages: build with Node, serve with NGINX. The runtime image contains no
# Node, no npm and no node_modules.
#
# NO SECRETS IN THIS IMAGE. The only build argument is a public API base URL.
# Vite inlines VITE_* variables into the bundle, so any credential passed here
# would be readable by every user in a browser. See docs/RULES.md section 6.
# =====================================================================

# ---------------------------------------------------------------- build ----
FROM node:22-alpine AS build

WORKDIR /app

# Copy only the manifests first so `npm ci` is cached until dependencies
# actually change, rather than on every source edit.
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .

# The single build argument. Public by definition: a URL.
ARG VITE_API_BASE_URL=""
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}

# `npm run build` runs `tsc -b` before `vite build`, so a type error fails the
# image build rather than shipping.
RUN npm run build

# ----------------------------------------------------------------- serve ----
FROM nginx:1.27-alpine AS runtime

# Run as a non-root user. A bug that writes into the web root should not become
# code execution as root. See docs/SECURITY.md section 12.
RUN addgroup -S nexa && adduser -S -G nexa nexa

COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

RUN chown -R nexa:nexa /usr/share/nginx/html \
 && chown -R nexa:nexa /var/cache/nginx \
 && chown -R nexa:nexa /var/log/nginx \
 && touch /var/run/nginx.pid \
 && chown -R nexa:nexa /var/run/nginx.pid

USER nexa

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost/ || exit 1

CMD ["nginx", "-g", "daemon off;"]