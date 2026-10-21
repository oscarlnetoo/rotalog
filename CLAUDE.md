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

## Commit Conventions
Use conventional commits in the format `<type>(<scope>): <imperative summary>`.

### Type
Use exactly one of: `feat`, `fix`, `refactor`, `perf`, `docs`, `test`, `chore`, `build`, `ci`, `style`, `revert`.

### Scope
`<scope>` is where the change lives, and is one of:
- `api-entregas` for changes under `rotalog-api-entregas`
- `api-frotas` for changes under `rotalog-api-frotas`
- `api-notificacoes` for changes under `rotalog-api-notificacoes`
- `frontend` for changes under `rotalog-frontend`
- `workspace` for changes under `rotalog-workspace`
- `root` for changes in the repository root

If a change touches more than one scope, prefer splitting it into separate commits, one per scope.

### Summary
- Imperative mood.
- Lowercase first letter, no trailing period.
- Describe what changed and why it matters, not how.
- Keep the whole subject line at or under about 72 characters.

### Body
- Only when the subject is not self-explanatory.

### Constraints
- Never add trailers or attribution lines to a commit message.
