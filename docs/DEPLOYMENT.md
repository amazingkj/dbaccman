# DB Account Manager - Deployment Guide

This guide covers deploying DB Account Manager to various environments.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Local Development](#local-development)
3. [Docker Deployment](#docker-deployment)
4. [Production Deployment](#production-deployment)
5. [Configuration](#configuration)
6. [Monitoring](#monitoring)
7. [Troubleshooting](#troubleshooting)

---

## Prerequisites

### Required Software

- **JDK 21** or later
- **Node.js 18** or later
- **Docker** (for containerized deployment)
- **Git**

### Supported Databases

- MySQL 8.0+
- PostgreSQL 14+
- Oracle 19c+ (including CDB/PDB)

---

## Local Development

### Backend Setup

```bash
cd backend

# Build the project
./gradlew build -x test

# Run the application
./gradlew run
```

The backend will start on port `12080` by default.

### Frontend Setup

```bash
cd frontend

# Install dependencies
npm install

# Start development server
npm run dev
```

The frontend will start on port `5173` with hot reload.

### Running Tests

```bash
# Backend tests
cd backend
./gradlew test

# Frontend unit tests
cd frontend
npm test

# Frontend E2E tests
npm run test:e2e
```

---

## Docker Deployment

### Build Docker Image

```bash
# Build the image
docker build -t dbaccman:latest .

# Or use the pre-built image
docker pull jiin724/dbaccman:latest
```

### Run with Docker

```bash
docker run -d \
  --name dbaccman \
  -p 12081:12081 \
  -e JWT_SECRET=your-secure-secret-key \
  -v ./logs:/app/logs \
  dbaccman:latest
```

### Docker Compose

Create a `docker-compose.yml`:

```yaml
version: '3.8'

services:
  dbaccman:
    image: jiin724/dbaccman:latest
    ports:
      - "12081:12081"
    environment:
      - JWT_SECRET=${JWT_SECRET}
      - JWT_EXPIRATION_HOURS=8
    volumes:
      - ./logs:/app/logs
      - ./audit:/app/logs/audit
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:12081/health"]
      interval: 30s
      timeout: 10s
      retries: 3
```

Run with:

```bash
docker-compose up -d
```

---

## Production Deployment

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `JWT_SECRET` | Secret key for JWT signing | (required) |
| `JWT_EXPIRATION_HOURS` | Token expiration time | 8 |
| `PORT` | HTTP server port | 12081 |
| `LOG_LEVEL` | Logging level (DEBUG, INFO, WARN, ERROR) | INFO |

### Security Considerations

1. **JWT Secret**: Use a strong, unique secret (at least 256 bits)
   ```bash
   openssl rand -base64 32
   ```

2. **HTTPS**: Always use HTTPS in production. Configure a reverse proxy (nginx, Caddy) with TLS.

3. **CORS**: Update allowed origins in `Application.kt` for production domains.

4. **Rate Limiting**: Built-in rate limiting is configured:
   - Login: 5 attempts per minute
   - API: 100 requests per minute
   - Query: 30 requests per minute

### Nginx Reverse Proxy

```nginx
server {
    listen 443 ssl http2;
    server_name dbaccman.example.com;

    ssl_certificate /etc/ssl/certs/your-cert.pem;
    ssl_certificate_key /etc/ssl/private/your-key.pem;

    location / {
        proxy_pass http://localhost:12081;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # WebSocket support
    location /api/ws {
        proxy_pass http://localhost:12081;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 86400;
    }
}
```

### Kubernetes Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: dbaccman
spec:
  replicas: 2
  selector:
    matchLabels:
      app: dbaccman
  template:
    metadata:
      labels:
        app: dbaccman
    spec:
      containers:
      - name: dbaccman
        image: jiin724/dbaccman:latest
        ports:
        - containerPort: 12081
        env:
        - name: JWT_SECRET
          valueFrom:
            secretKeyRef:
              name: dbaccman-secrets
              key: jwt-secret
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /health
            port: 12081
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /health
            port: 12081
          initialDelaySeconds: 5
          periodSeconds: 5
---
apiVersion: v1
kind: Service
metadata:
  name: dbaccman
spec:
  selector:
    app: dbaccman
  ports:
  - port: 80
    targetPort: 12081
  type: ClusterIP
```

---

## Configuration

### Application Configuration

Edit `backend/src/main/resources/application.conf`:

```hocon
ktor {
    deployment {
        port = 12081
        port = ${?PORT}
    }
    application {
        modules = [ com.dbaccman.ApplicationKt.module ]
    }
}

jwt {
    secret = ${JWT_SECRET}
    expirationHours = 8
    expirationHours = ${?JWT_EXPIRATION_HOURS}
}
```

### Logging Configuration

Edit `backend/src/main/resources/logback.xml` to adjust logging:

```xml
<root level="INFO">
    <appender-ref ref="CONSOLE"/>
    <appender-ref ref="JSON_FILE"/>
</root>
```

---

## Monitoring

### Health Check Endpoints

- `GET /health` - Basic health check
- `GET /metrics` - Prometheus metrics
- `GET /api/monitoring/health` - Detailed system health
- `GET /api/monitoring/details` - Full monitoring data
- `GET /api/monitoring/pools` - Connection pool statistics

### Prometheus Integration

Add to your `prometheus.yml`:

```yaml
scrape_configs:
  - job_name: 'dbaccman'
    static_configs:
      - targets: ['localhost:12081']
    metrics_path: '/metrics'
```

### Key Metrics

- JVM memory usage
- Garbage collection statistics
- Active database connections
- Request latency
- Circuit breaker status

### Grafana Dashboard

Import the provided Grafana dashboard from `docs/grafana-dashboard.json` for visualization.

---

## Troubleshooting

### Common Issues

#### Connection Timeout

```
Error: Connection timeout
```

**Solution**: Check database connectivity and firewall rules. Verify the database server is running and accessible.

#### Circuit Breaker Open

```
Error: Circuit breaker is OPEN
```

**Solution**: The circuit breaker has tripped due to repeated failures. Wait for the timeout period (30s default) or manually reset via:
```bash
curl -X POST http://localhost:12081/api/monitoring/circuit-breakers/reset-all
```

#### Out of Memory

```
Error: java.lang.OutOfMemoryError
```

**Solution**: Increase JVM heap size:
```bash
JAVA_OPTS="-Xmx1g -Xms512m" java -jar dbaccman-all.jar
```

#### Session Expired

```
Error: Session not found or expired
```

**Solution**: Sessions expire after 35 minutes of inactivity. Re-authenticate to create a new session.

### Log Locations

- Application logs: `logs/dbaccman.log`
- Audit logs: `logs/audit/audit-YYYY-MM-DD.jsonl`
- JSON logs: `logs/dbaccman.json`

### Debug Mode

Enable debug logging:

```bash
LOG_LEVEL=DEBUG java -jar dbaccman-all.jar
```

---

## Backup and Recovery

### Audit Logs

Audit logs are stored in JSONL format and rotated daily. Back up the `logs/audit` directory regularly.

### Session Data

Session data is stored in memory and will be lost on restart. This is by design for security. Users will need to re-authenticate after a server restart.

---

## Updating

### Docker Update

```bash
docker pull jiin724/dbaccman:latest
docker-compose down
docker-compose up -d
```

### Manual Update

1. Stop the application
2. Back up configuration files
3. Replace the JAR file
4. Start the application

---

## Support

For issues and questions:
- GitHub Issues: https://github.com/your-repo/dbaccman/issues
- Documentation: https://github.com/your-repo/dbaccman/wiki
