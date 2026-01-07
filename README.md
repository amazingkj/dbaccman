# DONUT

**D**atabase Acc**o**u**n**t Manager **U**nified **T**ool - A unified database account management system for MySQL, PostgreSQL, and Oracle.

## Links

* Web site: http://localhost:12080
* Docker Hub: https://hub.docker.com/repository/docker/jiin724/dbaccman

## Features

* **Account Management** - Create, delete, clone accounts with batch operations
* **Permission Control** - Grant/revoke privileges, Oracle role management
* **Session Monitoring** - Active sessions, slow query detection, kill sessions
* **Table Browser** - Schema explorer, table structure, index viewer
* **Tablespace Management** - Create/delete tablespaces, usage monitoring
* **SQL Console** - Query execution with schema switching

## Quick Start

```bash
docker run -d -p 12080:12080 --name donut jiin724/dbaccman:latest
```
Then open http://localhost:12080 in your browser.

## Building from Source

### Requirements

* JDK 21 or above
* Node.js 18 or above

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `JWT_SECRET` | Secret key for JWT token signing | Auto-generated |
| `PORT` | Server port | 12080 |

## Tech Stack

| Component | Technology |
|-----------|------------|
| Backend | Kotlin, Ktor, JDBC |
| Frontend | React 18, TypeScript, Ant Design |
| Database | MySQL, PostgreSQL, Oracle |

