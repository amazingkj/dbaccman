# DBAccMan

데이터베이스 계정 관리 시스템

## 기술 스택

- **Backend**: Kotlin + Ktor (JDK 21)
- **Frontend**: React 18 + TypeScript + Vite + Ant Design
- **지원 DB**: MySQL, PostgreSQL, Oracle

---

## 빠른 시작

### 사전 요구사항

- [Docker](https://docs.docker.com/get-docker/) 설치
- [Docker Compose](https://docs.docker.com/compose/install/) 설치

### Docker Compose로 실행

```bash
git clone https://github.com/amazingkj/dbaccman.git
cd dbaccman
docker compose up -d
```

### Docker Hub에서 바로 실행

```bash
docker run -d -p 3080:3080 --name dbaccman jiin724/dbaccman:latest
```

브라우저에서 http://localhost:3080 접속

### 환경 변수 설정

```bash
# .env 파일 생성 (선택사항)
JWT_SECRET=your-secure-secret-key
```

---

## 개발 환경 설정

### Backend

```bash
cd backend

# 실행
./gradlew run

# 빌드
./gradlew shadowJar
```

### Frontend

```bash
cd frontend

# 의존성 설치
npm install

# 개발 서버 실행
npm run dev

# 빌드
npm run build
```

---

## 프로젝트 구조

```
dbaccman/
├── backend/           # Kotlin + Ktor API 서버
├── frontend/          # React + TypeScript 클라이언트
├── docker-compose.yml # Docker 구성
├── Dockerfile         # 멀티스테이지 빌드
└── logs/              # 애플리케이션 로그
```

---

## 포트

| 서비스 | 포트 |
|--------|------|
| Web UI | 3080 |


