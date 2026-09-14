# Stage 1: Build Angular application
FROM localhost:8091/library/node:20-alpine AS build
WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build -- --configuration production

# Stage 2: Serve application with Nginx
FROM localhost:8091/library/nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY certs/ /etc/nginx/certs/

# Copies the browser build artifacts regardless of project name in dist
COPY --from=build /app/dist/*/browser /usr/share/nginx/html/

EXPOSE 80 443
CMD ["nginx", "-g", "daemon off;"]