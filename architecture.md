# InvoiceMind AI Architecture

## Monorepo

- `frontend/` Next.js application
- `backend/` Spring Boot API
- `docker/` infrastructure configs (Nginx)
- `docs/` architecture and documentation

## Backend layers

- `controller` REST API routes
- `service` core domain services
- `repository` JPA data access
- `entity` relational models
- `dto` API contracts
- `mapper` DTO/entity transformations
- `security` JWT + Spring Security filters
- `exception` global error handling
- domain modules: `workflow`, `ocr`, `analytics`, `approval`, `export`, `notification`, `audit`

## Runtime

- PostgreSQL primary relational storage
- Redis cache support
- Kafka event bus (`workflow-events`, `ocr-events`)
- WebSocket endpoint `/ws/live` for live processing updates
