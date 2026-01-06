# DONUT

**D**atabase Acc**o**u**n**t Manager **U**nified **T**ool

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
docker run -d -p 12080:12080 --name donut jiin724/dbaccman:latest
```

브라우저에서 http://localhost:12080 접속

### 환경 변수 설정

```bash
# .env 파일 생성 (선택사항)
JWT_SECRET=your-secure-secret-key
```

---

## 주요 기능

### 계정 관리
- 사용자 생성, 삭제, 비밀번호 변경, 잠금 해제
- 계정 복제 (권한 포함)
- 일괄 계정 생성/삭제

### 권한 관리
- 데이터베이스/테이블별 권한 부여 및 회수
- Oracle 역할(Role) 관리
- 시스템 권한 및 객체 권한 지원

### 세션 관리
- 활성 세션 모니터링
- 장시간 실행 쿼리 감지
- 세션/쿼리 강제 종료

### 테이블 관리
- 스키마별 테이블 목록 조회
- 테이블 구조 및 인덱스 조회
- 통계 수집 (Oracle DBMS_STATS)

### Tablespace 관리
- Tablespace 생성/삭제
- 테이블 Tablespace 이동
- 사용량 모니터링

### SQL Console
- 쿼리 실행 및 결과 조회
- 다중 SQL 문장 실행 지원
- 스키마 전환 기능
- 감사 로그 추적

### 역할 기반 접근 제어
- Admin: 모든 기능 접근 가능
- User: Dashboard만 접근 가능

---

## 개발 환경 설정

### Backend

```bash
cd backend

# 실행 (포트: 12080)
./gradlew run

# 테스트
./gradlew test

# 테스트 커버리지
./gradlew jacocoTestReport

# 빌드
./gradlew shadowJar
```

### Frontend

```bash
cd frontend

# 의존성 설치
npm install

# 개발 서버 실행 (포트: 12081)
npm run dev

# 빌드
npm run build
```

---

## 프로젝트 구조

```
dbaccman/
├── backend/           # Kotlin + Ktor API 서버
│   └── src/
│       ├── main/      # 애플리케이션 코드
│       └── test/      # 테스트 코드
├── frontend/          # React + TypeScript 클라이언트
│   └── src/
│       ├── api/       # API 클라이언트
│       ├── components/# 공통 컴포넌트
│       ├── pages/     # 페이지 컴포넌트
│       └── types/     # TypeScript 타입 정의
├── docker-compose.yml # Docker 구성
├── Dockerfile         # 멀티스테이지 빌드
└── logs/              # 애플리케이션 로그
```

---

## 포트

| 서비스 | 포트 | 설명 |
|--------|------|------|
| Backend API | 12080 | Ktor 서버 |
| Frontend Dev | 12081 | Vite 개발 서버 |

---

## Docker 빌드

```bash
# 빌드
docker compose build

# 실행
docker compose up -d

# 로그 확인
docker compose logs -f

# 중지
docker compose down
```

---

## 라이선스

MIT License
