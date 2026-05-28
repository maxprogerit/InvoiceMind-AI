# InvoiceMind AI

Clean production-style fullstack monorepo:

```text
frontend/
backend/
docker/
docs/
README.md
docker-compose.yml
.env.example
```

## Frontend

Located in `frontend/` with:

- Next.js 15
- React 19 + TypeScript
- Tailwind CSS + Shadcn-compatible component approach
- React Query
- Zustand
- Recharts
- React Flow

Key structure:

```text
frontend/
  src/
    app/
    components/
    features/
    lib/
    hooks/
    store/
    services/
    types/
    styles/
  public/
```

Run locally:

```bash
cd frontend
npm install
npm run dev
```

## Backend

Located in `backend/` as a standard Spring Boot 3 project (Java 21):

- Spring Web
- Spring Security + JWT
- Spring Data JPA (PostgreSQL)
- Flyway
- Redis
- Kafka
- WebSocket
- File upload + OCR simulation + workflow execution + approvals + analytics + export + audit services

Package structure:

```text
backend/src/main/java/com/invoicemind/ai/
  config/
  controller/
  service/
  repository/
  entity/
  dto/
  mapper/
  security/
  exception/
  workflow/
  ocr/
  analytics/
  approval/
  export/
  notification/
  audit/
```

Run locally:

```bash
cd backend
./mvnw spring-boot:run
```

## Docker

```bash
docker compose up --build
```

Services launched:

- frontend (`3000`)
- backend (`8080`)
- PostgreSQL (`5432`)
- Redis (`6379`)
- Kafka (`9092`)
- Nginx (`80`)
