FROM node:24-bookworm-slim

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN node scripts/run-framework.mjs build

ENV NODE_ENV=production \
    MARKET_ATLAS_HOST=0.0.0.0 \
    MARKET_ATLAS_PORT=5174 \
    MARKET_ATLAS_STATE_DIR=/app/.wrangler/state

EXPOSE 5174

CMD ["node", "scripts/start-server.mjs"]
