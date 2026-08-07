FROM node:20-alpine AS base

# ---- Dependencies ----
FROM base AS deps
WORKDIR /app
COPY package*.json ./
RUN npm ci

# ---- Build ----
FROM base AS build
WORKDIR /app
ARG NEXT_PUBLIC_API_URL=https://api.careflow-demo.online
ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN npm run build

# ---- Production ----
FROM base AS runner
WORKDIR /app
ENV NODE_ENV=production

# Tạo user non-root cho bảo mật
RUN addgroup --system --gid 1001 nodejs
RUN adduser --system --uid 1001 nextjs

# Copy build output
COPY --from=build /app/public ./public
COPY --from=build --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=build --chown=nextjs:nodejs /app/.next/static ./.next/static

USER nextjs
EXPOSE 3000
ENV PORT=3000
ENV HOSTNAME="0.0.0.0"

CMD ["node", "server.js"]
