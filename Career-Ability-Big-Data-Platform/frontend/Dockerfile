FROM node:20.18.1-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27.3-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/nginx.conf
EXPOSE 80
HEALTHCHECK --interval=20s --timeout=5s --start-period=15s --retries=5 CMD wget -q -O /dev/null http://127.0.0.1/ || exit 1
