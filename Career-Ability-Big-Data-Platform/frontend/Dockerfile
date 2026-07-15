FROM node:20.20.2-alpine3.22 AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.29-alpine3.22
RUN apk upgrade --no-cache
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/nginx.conf
EXPOSE 80
HEALTHCHECK --interval=20s --timeout=5s --start-period=15s --retries=5 CMD wget -q -O /dev/null http://127.0.0.1/ || exit 1
