# PayFlow frontend — multi-stage static SPA + Nginx reverse proxy (simulation UI only).
# Build from frontend/:  docker build -t payflow-frontend:local .

# ---------- build ----------
FROM node:24.21.0-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY index.html vite.config.ts tsconfig.json tsconfig.app.json tsconfig.node.json ./
COPY public ./public
COPY src ./src
COPY eslint.config.js ./

# Relative API base URL (empty) — nginx proxies /api/v1 to the backend.
# Do not pass VITE_* secrets; none are required for production build.
RUN npm run build

# ---------- runtime ----------
FROM nginxinc/nginx-unprivileged:1.28.0-alpine AS runtime

LABEL org.opencontainers.image.title="payflow-frontend" \
	org.opencontainers.image.description="PayFlow merchant dashboard static UI" \
	org.opencontainers.image.source="https://github.com/local/payflow"

# Configurable backend upstream for Compose / future deploy (envsubst filter = PAYFLOW_*)
ENV PAYFLOW_BACKEND_HOST=payflow-backend \
	PAYFLOW_BACKEND_PORT=8080 \
	NGINX_ENVSUBST_FILTER=PAYFLOW_

# Replace default site with PayFlow template (rendered at container start)
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html

# Image already USER 101 (nginx) and listens on 8080
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
	CMD wget -qO- http://127.0.0.1:8080/ || exit 1

# Official image ENTRYPOINT runs envsubst then: nginx -g 'daemon off;'
CMD ["nginx", "-g", "daemon off;"]
