# Stage 1: Build Frontend
FROM node:20-alpine AS frontend-builder
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# Stage 2: Build Backend
FROM gradle:8.5-jdk21-alpine AS backend-builder
WORKDIR /app/backend
COPY backend/ ./
RUN gradle shadowJar --no-daemon -x test

# Stage 3: Runtime
FROM eclipse-temurin:21-jre-alpine

# Create non-root user for security
RUN addgroup -g 1001 appgroup && adduser -u 1001 -G appgroup -D appuser

WORKDIR /app

# Copy artifacts
COPY --from=backend-builder /app/backend/build/libs/dbaccman-all.jar app.jar
COPY --from=frontend-builder /app/frontend/dist ./static

# Create logs directory and set permissions
RUN mkdir -p logs && chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

EXPOSE 12080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
    CMD wget -q --spider http://localhost:12080/health || exit 1

ENTRYPOINT ["java", "-Xmx512m", "-jar", "app.jar"]

# docker tag jiin724/dbaccman:latest jiin724/dbaccman:1.0.0
# docker push jiin724/dbaccman:latest
# docker push jiin724/dbaccman:1.0.0

#  docker build --no-cache -t jiin724/dbaccman:1.0.4 .
#  docker push jiin724/dbaccman:1.0.2