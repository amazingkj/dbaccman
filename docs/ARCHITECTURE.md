# DB Account Manager - Architecture Documentation

## Overview

DB Account Manager is a web-based tool for managing database user accounts across MySQL, PostgreSQL, and Oracle databases. It provides a unified interface for account creation, permission management, session monitoring, and query execution.

## System Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Client Layer                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    React Frontend (SPA)                              │   │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐   │   │
│  │  │  Dashboard  │ │  Accounts   │ │ Permissions │ │  Sessions   │   │   │
│  │  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘   │   │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐   │   │
│  │  │   Tables    │ │ Tablespaces │ │   Query     │ │   Roles     │   │   │
│  │  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘   │   │
│  │                                                                      │   │
│  │  ┌──────────────────────────────────────────────────────────────┐   │   │
│  │  │  State Management (Zustand) │ API Client │ WebSocket Hook    │   │   │
│  │  └──────────────────────────────────────────────────────────────┘   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ HTTP/WebSocket
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Backend Layer (Ktor)                               │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                        Security Layer                                │   │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐   │   │
│  │  │ JWT Auth    │ │ CSRF Token  │ │ Rate Limit  │ │ Input Valid │   │   │
│  │  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                         API Routes                                   │   │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐   │   │
│  │  │ /auth/*     │ │ /accounts/* │ │ /perms/*    │ │ /sessions/* │   │   │
│  │  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘   │   │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐   │   │
│  │  │ /tables/*   │ │ /spaces/*   │ │ /query      │ │ /ws/*       │   │   │
│  │  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                       Service Layer                                  │   │
│  │  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐        │   │
│  │  │ AccountService  │ │ PermissionSvc   │ │ SessionService  │        │   │
│  │  └─────────────────┘ └─────────────────┘ └─────────────────┘        │   │
│  │  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐        │   │
│  │  │  TableService   │ │ TablespaceSvc   │ │  QueryService   │        │   │
│  │  └─────────────────┘ └─────────────────┘ └─────────────────┘        │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                       Infrastructure                                 │   │
│  │  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐        │   │
│  │  │ Session Manager │ │ Circuit Breaker │ │ Event Broadcast │        │   │
│  │  │  (HikariCP)     │ │    Registry     │ │   (WebSocket)   │        │   │
│  │  └─────────────────┘ └─────────────────┘ └─────────────────┘        │   │
│  │  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐        │   │
│  │  │  Audit Logger   │ │  Rate Limiter   │ │ Hikari Monitor  │        │   │
│  │  └─────────────────┘ └─────────────────┘ └─────────────────┘        │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                      Dialect Layer                                   │   │
│  │  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐        │   │
│  │  │  MySQLDialect   │ │ PostgreDialect  │ │  OracleDialect  │        │   │
│  │  └─────────────────┘ └─────────────────┘ └─────────────────┘        │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ JDBC
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Database Layer                                     │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────────────────┐   │
│  │     MySQL       │ │   PostgreSQL    │ │    Oracle (CDB/PDB)         │   │
│  │    (8.0+)       │ │     (14+)       │ │       (19c+)                │   │
│  └─────────────────┘ └─────────────────┘ └─────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Component Details

### Frontend Architecture

```
frontend/
├── src/
│   ├── api/
│   │   └── client.ts           # Axios HTTP client with interceptors
│   │
│   ├── components/
│   │   ├── common/             # Reusable components
│   │   │   ├── StatCard.tsx    # Statistics display card
│   │   │   ├── SearchFilter.tsx
│   │   │   └── ErrorBoundary.tsx
│   │   └── layout/             # Layout components
│   │
│   ├── hooks/
│   │   ├── useWebSocket.ts     # WebSocket connection management
│   │   ├── useDebounce.ts      # Input debouncing
│   │   └── useAsync.ts         # Async data fetching
│   │
│   ├── pages/                  # Page components
│   │   ├── Dashboard.tsx
│   │   ├── Accounts.tsx
│   │   ├── Sessions.tsx
│   │   ├── Permissions.tsx
│   │   ├── Tables.tsx
│   │   ├── Tablespaces.tsx
│   │   └── Query.tsx
│   │
│   ├── store/
│   │   └── authStore.ts        # Zustand state management
│   │
│   ├── utils/
│   │   ├── cache.ts            # API response caching
│   │   └── errors.ts           # Error handling utilities
│   │
│   └── types/
│       └── index.ts            # TypeScript type definitions
│
└── e2e/                        # Playwright E2E tests
```

### Backend Architecture

```
backend/
├── src/main/kotlin/com/dbaccman/
│   ├── Application.kt          # Application entry point
│   │
│   ├── config/
│   │   ├── JwtConfig.kt        # JWT authentication configuration
│   │   └── SessionConnectionManager.kt  # Connection pool management
│   │
│   ├── dialect/
│   │   ├── DatabaseDialect.kt  # Dialect interface
│   │   ├── MySQLDialect.kt     # MySQL-specific SQL
│   │   ├── PostgreSQLDialect.kt
│   │   └── OracleDialect.kt
│   │
│   ├── routes/
│   │   ├── AuthRoutes.kt       # Authentication endpoints
│   │   ├── AccountRoutes.kt    # Account management
│   │   ├── PermissionRoutes.kt
│   │   ├── SessionRoutes.kt
│   │   ├── WebSocketRoutes.kt  # Real-time event streaming
│   │   └── MonitoringRoutes.kt # Health & metrics
│   │
│   ├── service/
│   │   ├── AccountService.kt
│   │   ├── PermissionService.kt
│   │   ├── SessionService.kt
│   │   ├── QueryService.kt
│   │   └── TableService.kt
│   │
│   ├── util/
│   │   ├── CircuitBreaker.kt   # Fault tolerance
│   │   ├── RateLimiter.kt      # Request throttling
│   │   ├── AuditLogger.kt      # Security audit logging
│   │   ├── InputValidator.kt   # Input validation
│   │   └── HikariMonitor.kt    # Connection pool monitoring
│   │
│   ├── websocket/
│   │   └── EventBroadcaster.kt # WebSocket event distribution
│   │
│   └── exception/
│       ├── AppExceptions.kt    # Custom exception types
│       └── ErrorHandler.kt     # Centralized error handling
```

## Data Flow

### Authentication Flow

```
┌──────────┐     ┌──────────────┐     ┌─────────────┐     ┌──────────┐
│  Client  │────▶│  Rate Limit  │────▶│  Auth Route │────▶│ Database │
└──────────┘     └──────────────┘     └─────────────┘     └──────────┘
                                             │
                                             ▼
                                      ┌─────────────┐
                                      │ JWT Token + │
                                      │ httpOnly    │
                                      │ Cookie      │
                                      └─────────────┘
```

### Request Processing Flow

```
┌──────────┐
│ Request  │
└────┬─────┘
     │
     ▼
┌────────────────────┐
│   CORS Filter      │
└────────┬───────────┘
         │
         ▼
┌────────────────────┐
│   CSRF Validation  │
└────────┬───────────┘
         │
         ▼
┌────────────────────┐
│   Rate Limiting    │
└────────┬───────────┘
         │
         ▼
┌────────────────────┐
│  JWT Verification  │
└────────┬───────────┘
         │
         ▼
┌────────────────────┐
│  Input Validation  │
└────────┬───────────┘
         │
         ▼
┌────────────────────┐
│  Circuit Breaker   │
└────────┬───────────┘
         │
         ▼
┌────────────────────┐
│   Service Layer    │
└────────┬───────────┘
         │
         ▼
┌────────────────────┐
│   Dialect Layer    │
└────────┬───────────┘
         │
         ▼
┌────────────────────┐
│  Database (JDBC)   │
└────────────────────┘
```

### WebSocket Event Flow

```
┌──────────────────────────────────────────────────────────────────┐
│                      Event Sources                                │
├──────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐              │
│  │ Account Ops │  │ Permission  │  │ Session Ops │              │
│  │  Service    │  │   Service   │  │   Service   │              │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘              │
└─────────┼────────────────┼────────────────┼─────────────────────┘
          │                │                │
          └────────────────┼────────────────┘
                           │
                           ▼
                ┌──────────────────────┐
                │   Event Broadcaster  │
                │     (Singleton)      │
                └──────────┬───────────┘
                           │
          ┌────────────────┼────────────────┐
          │                │                │
          ▼                ▼                ▼
    ┌──────────┐    ┌──────────┐    ┌──────────┐
    │ Client 1 │    │ Client 2 │    │ Client N │
    │   (WS)   │    │   (WS)   │    │   (WS)   │
    └──────────┘    └──────────┘    └──────────┘
```

## Key Design Patterns

### 1. Dialect Pattern

The dialect pattern abstracts database-specific SQL generation:

```kotlin
interface DatabaseDialect {
    fun getCreateUserSql(username: String, host: String, password: String): String
    fun getAllAccountsQuery(): String
    // ... other methods
}

class MySQLDialect : DatabaseDialect {
    override fun getCreateUserSql(...) = "CREATE USER ..."
}

class OracleDialect : DatabaseDialect {
    override fun getCreateUserSql(...) = "CREATE USER ... IDENTIFIED BY ..."
}
```

### 2. Circuit Breaker Pattern

Prevents cascading failures when a database is unavailable:

```
        ┌─────────┐
        │ CLOSED  │──────────────────┐
        │ (Normal)│                  │
        └────┬────┘                  │
             │ failure threshold     │
             ▼ exceeded              │
        ┌─────────┐                  │
        │  OPEN   │                  │
        │(Blocked)│                  │
        └────┬────┘                  │
             │ timeout               │
             ▼ elapsed               │
        ┌──────────┐                 │
        │HALF-OPEN │─────────────────┘
        │ (Test)   │  success threshold
        └──────────┘  met
```

### 3. Session Pool Pattern

Each authenticated user gets their own connection pool:

```
┌──────────────────────────────────────────────────────────┐
│              SessionConnectionManager                     │
├──────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────┐ │
│  │            Session Pools (ConcurrentHashMap)        │ │
│  │  ┌───────────────┐ ┌───────────────┐ ┌───────────┐ │ │
│  │  │ Session UUID1 │ │ Session UUID2 │ │    ...    │ │ │
│  │  │  HikariCP     │ │  HikariCP     │ │           │ │ │
│  │  │  (5 conn)     │ │  (5 conn)     │ │           │ │ │
│  │  └───────────────┘ └───────────────┘ └───────────┘ │ │
│  └─────────────────────────────────────────────────────┘ │
│                                                          │
│  Cleanup Thread: Removes expired sessions (35 min)       │
└──────────────────────────────────────────────────────────┘
```

## Security Architecture

### Security Layers

```
┌─────────────────────────────────────────────────────────┐
│                    Security Stack                        │
├─────────────────────────────────────────────────────────┤
│  ┌────────────────────────────────────────────────────┐ │
│  │  Layer 1: Transport Security (HTTPS/TLS)           │ │
│  └────────────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────────────┐ │
│  │  Layer 2: CORS Policy (Origin restriction)         │ │
│  └────────────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────────────┐ │
│  │  Layer 3: Rate Limiting (Sliding window)           │ │
│  │           - Login: 5/min, Block: 5 min             │ │
│  │           - API: 100/min                           │ │
│  │           - Query: 30/min                          │ │
│  └────────────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────────────┐ │
│  │  Layer 4: CSRF Protection (Token validation)       │ │
│  └────────────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────────────┐ │
│  │  Layer 5: JWT Authentication (httpOnly cookie)     │ │
│  └────────────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────────────┐ │
│  │  Layer 6: Input Validation (Identifier/Privilege)  │ │
│  └────────────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────────────┐ │
│  │  Layer 7: SQL Injection Prevention (Quoting/Bind)  │ │
│  └────────────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────────────┐ │
│  │  Layer 8: Audit Logging (All state changes)        │ │
│  └────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────┘
```

## Monitoring & Observability

### Metrics Collection

```
┌─────────────────────────────────────────────────────────┐
│                  Monitoring Stack                        │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  Application ─────┬────▶ Prometheus Metrics             │
│                   │      - JVM Memory                    │
│                   │      - GC Statistics                 │
│                   │      - Request Latency               │
│                   │                                      │
│                   ├────▶ Structured Logs (JSON)         │
│                   │      - Request/Response              │
│                   │      - Errors                        │
│                   │                                      │
│                   ├────▶ Audit Logs (JSONL)             │
│                   │      - Account Operations            │
│                   │      - Permission Changes            │
│                   │      - Session Events                │
│                   │                                      │
│                   └────▶ Health Endpoints               │
│                          - /health                       │
│                          - /api/monitoring/*             │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

## Technology Stack

| Layer | Technology |
|-------|------------|
| Frontend | React 18, TypeScript, Ant Design, Zustand |
| API Client | Axios with Interceptors |
| Backend | Kotlin, Ktor 2.3 |
| Authentication | JWT (HS256), httpOnly Cookies |
| Database Connectivity | HikariCP, JDBC |
| Metrics | Micrometer, Prometheus |
| Logging | Logback, Logstash Encoder |
| Testing | JUnit 5, Vitest, Playwright |
| Container | Docker, Alpine Linux |

## Performance Characteristics

| Metric | Target |
|--------|--------|
| API Response Time | < 200ms (p95) |
| WebSocket Latency | < 50ms |
| Connection Pool Size | 5 per session |
| Session Timeout | 35 minutes |
| Max Concurrent Sessions | ~100 (depends on memory) |
| Circuit Breaker Timeout | 30 seconds |

---

*Last Updated: 2026-01*
