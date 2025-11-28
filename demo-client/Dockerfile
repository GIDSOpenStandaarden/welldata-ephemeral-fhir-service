# Build stage
FROM node:20-alpine AS builder

WORKDIR /app

# Copy package files
COPY package*.json ./

# Install dependencies
RUN npm ci

# Copy source files
COPY public/ ./public/
COPY src/ ./src/

# Build the production app
RUN npm run build

# Production stage with nginx
FROM nginx:alpine

# Copy custom nginx config as template
COPY nginx.conf /etc/nginx/templates/default.conf.template

# Copy built assets from builder
COPY --from=builder /app/build /usr/share/nginx/html

# Set default environment variables
ENV FHIR_BACKEND_URL=http://localhost:8080
ENV DEFAULT_SOLID_PROVIDER=http://localhost:3000

# Copy entrypoint script
COPY docker-entrypoint.sh /docker-entrypoint.d/40-envsubst-fhir-backend.sh
RUN chmod +x /docker-entrypoint.d/40-envsubst-fhir-backend.sh

# Expose port
EXPOSE 3000

# Start nginx
CMD ["nginx", "-g", "daemon off;"]
