# Rotalog

Rotalog is a fleet and delivery management system. It manages vehicles, drivers, maintenance, deliveries, real-time tracking and email/SMS notifications through three backend APIs and two frontends, all sharing a single PostgreSQL database split into one schema per service.

This project is a practice exercise from the Alura course **"Software engineering in the age of AI: working with legacy systems, scale, and multiple services"**. The codebase is an intentionally legacy system, with technical debt left on purpose (hardcoded credentials, missing tests, inconsistent patterns) so it can be analyzed and refactored with the help of AI. See `TECH_DEBT_MAP.md` for the catalog of known issues.

| Repository | Stack | Domain | Port |
| --- | --- | --- | --- |
| [`rotalog-api-frotas`](rotalog-api-frotas/README.md) | Java 11 / Spring Boot 2.7 | Vehicles, drivers, maintenance | `8080` |
| [`rotalog-api-entregas`](rotalog-api-entregas/README.md) | Node.js 18 / Express 4 | Orders, routing, tracking | `3000` |
| [`rotalog-api-notificacoes`](rotalog-api-notificacoes/README.md) | .NET 6 / ASP.NET Core | Email and SMS notifications | `5000` |
| [`rotalog-frontend`](rotalog-frontend/README.md) | NX 19 (Angular 18 + React 18) | Admin panel and tracking portal | `4200`, `3001` |
| [`rotalog-workspace`](rotalog-workspace/README.md) | Docker Compose / PostgreSQL 14 | Database, schemas, migrations, seeds | `5432` |

## Prerequisites

Before starting, make sure you've met the following requirements:

- You have installed `Docker` with `Docker Compose`.
- You have installed version 11 of the `JDK` and version 3.6+ of `Maven`.
- You have installed version 18+ of `Node.js` and `npm`.
- You have installed version 6 of the `.NET SDK`.
- You have a Windows, Linux or Mac machine (all services run on any OS supported by the tools above).
- You have read `CLAUDE.md` for an overview of each repository's structure.

## Installing the Project

Each service lives in its own directory and is installed separately. Start with the database, since every API depends on it.

1. Clone the repository

    ```bash
    git clone <repository_url> rotalog
    cd rotalog
    ```

2. Start PostgreSQL with schemas, migrations and seed data

    ```bash
    cd rotalog-workspace
    docker compose up -d
    cd ..
    ```

3. Build the fleet API

    ```bash
    cd rotalog-api-frotas
    mvn clean install
    cd ..
    ```

4. Install the deliveries API dependencies

    ```bash
    cd rotalog-api-entregas
    npm install
    cd ..
    ```

5. Restore the notifications API dependencies

    ```bash
    cd rotalog-api-notificacoes
    dotnet restore
    cd ..
    ```

6. Install the frontend dependencies

    ```bash
    cd rotalog-frontend
    npm install
    cd ..
    ```

## Using the Project

To use Rotalog, follow these steps (each command in its own terminal, with the database already running):

```bash
# Fleet API - http://localhost:8080/api
cd rotalog-api-frotas && mvn spring-boot:run

# Deliveries API - http://localhost:3000/api
cd rotalog-api-entregas && npm run dev

# Notifications API - http://localhost:5000 (Swagger at /swagger)
cd rotalog-api-notificacoes && dotnet run

# Admin panel - http://localhost:4200
cd rotalog-frontend && npx nx serve painel-admin

# Tracking portal - http://localhost:3001
cd rotalog-frontend && npx nx serve rastreamento
```

Check that the APIs are up:

```bash
curl http://localhost:8080/api/health
curl http://localhost:3000/api/health
curl http://localhost:5000/api/health
```

Stop the database when you're done:

```bash
cd rotalog-workspace && docker compose down
```

See each repository's README for service-specific commands, endpoints and configuration.
