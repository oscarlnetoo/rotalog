# Rotalog

## Context
Fleet and delivery management system. Manages vehicles, drivers, deliveries and notifications. Composed of 3 backend APIs and 2 frontends (NX monorepo).

## Repositories

### rotalog-api-frotas (Java 11 /Spring Boot 2.7)
- Domain: vehicles, drivers, maintenance
- Database: PostgreSQL (schema `frotas`)
- Migrations: Flyway
- Structure: Controller -> Service -> Repository
- JPA entities
- Port: 8080

### rotalog-api-entregas (Node.js 18 / Express 4)
- Domain: orders, routing, tracking
- Database: PostgreSQL (schema `entregas`)
- Migrations: raw SQL scripts (`src/config/migration.sql`, run via `npm run db:migrate`)
- Structure: routes -> services -> models (+ middleware for auth/error handling)
- Sequelize models
- Port: 3000

### rotalog-api-notificacoes (.NET 6 / ASP.NET Core)
- Domain: email and SMS notifications, templates, sending history
- Database: PostgreSQL (schema `notificacoes`)
- Migrations: none — `EnsureCreated()` on startup (SQL scripts in `Data/`)
- Structure: Controller -> Service -> DbContext (MediatR registered but unused)
- Entity Framework Core entities
- Port: 5000

### rotalog-frontend (NX 19 monorepo)
- Apps: `painel-admin` (Angular 18, port 4200), `rastreamento` (React 18 + Leaflet, port 3001), `painel-admin-e2e` (Playwright)
- Libs: `api-contracts` (OpenAPI), `shared-types`, `ui-components`
- Run tasks through NX (`npx nx <target> <project>`) — see `rotalog-frontend/CLAUDE.md`

### rotalog-workspace (infrastructure)
- Docker Compose with PostgreSQL 14 (database `rotalog`)
- Init scripts in `tools/scripts/` create the schemas, migrations and seeds
- APIs and frontends are started separately in their own repositories
