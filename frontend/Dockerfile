# Imagem do front-end (contexto de build: a pasta frontend)
#   docker build --build-arg API_URL=http://api:8080 -t chegaai-web frontend

# --- Etapa 1: build do Next.js no modo standalone ---
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# Endereço da API visto de dentro da rede do Docker Compose; o Next.js repassa /api para ele
ARG API_URL=http://api:8080
ENV API_URL=$API_URL NEXT_OUTPUT=standalone NEXT_TELEMETRY_DISABLED=1
RUN npm run build

# --- Etapa 2: só o servidor gerado e os arquivos estáticos ---
FROM node:22-alpine
WORKDIR /app
ENV NODE_ENV=production NEXT_TELEMETRY_DISABLED=1 PORT=3000 HOSTNAME=0.0.0.0
COPY --from=build --chown=node:node /app/.next/standalone ./
COPY --from=build --chown=node:node /app/.next/static ./.next/static
USER node
EXPOSE 3000
CMD ["node", "server.js"]
