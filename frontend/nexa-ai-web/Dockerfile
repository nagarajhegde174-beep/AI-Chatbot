# syntax=docker/dockerfile:1
#
# NexaAI web client image: build the Vite bundle, then serve it from NGINX.

# ---------------------------------------------------------------- build ----
FROM node:22-alpine AS build
WORKDIR /build

# The lockfile is copied first so the dependency layer is cached independently of
# application source.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# VITE_* values are inlined into the bundle at build time, so only PUBLIC values
# may be passed here. No API key, signing secret or payment secret is ever
# supplied to this build, see docs/RULES.md section 6.
ARG VITE_API_BASE_URL=""
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL

RUN npm run build

# -------------------------------------------------------------- runtime ----
FROM nginx:1.27-alpine AS runtime

COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
