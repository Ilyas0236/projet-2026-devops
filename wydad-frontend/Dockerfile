# ---- Étape 1 : build Angular ----
FROM node:20-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# ---- Étape 2 : serveur nginx ----
FROM nginx:1.27-alpine

RUN apk add --no-cache wget

# Configuration SPA : fallback index.html + cache assets long
COPY nginx.conf /etc/nginx/conf.d/default.conf

COPY --from=build /app/dist/wydad-frontend/browser /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=5s --retries=5 --start-period=10s \
  CMD wget -q -S -O /dev/null http://localhost:80/ 2>&1 | grep -q 'HTTP/'

CMD ["nginx", "-g", "daemon off;"]
