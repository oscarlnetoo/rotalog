# Rotalog Workspace

Infrastructure for the Rotalog fleet and delivery management system. It provides a Docker Compose setup with PostgreSQL 14 (database `rotalog`) and the init scripts that create the `frotas`, `entregas` and `notificacoes` schemas, run each service's migrations and load seed data. The APIs and frontends are not containerized; they are started separately from their own repositories.

## Prerequisites

Before starting, make sure you've met the following requirements:

- You have installed `Docker` with `Docker Compose`.
- You have port `5432` free on your machine.
- You have read the root `README.md` for an overview of how the services fit together.

## Installing the Project

The database is created and populated automatically the first time the container starts.

1. Navigate to the workspace directory

    ```bash
    cd rotalog-workspace
    ```

2. Start the PostgreSQL container

    ```bash
    docker compose up -d
    ```

3. Confirm the container is running

    ```bash
    docker compose ps
    ```

The init scripts in `tools/scripts/` run in this order on the first start:

| Script | Purpose |
| --- | --- |
| `init-schemas.sql` | Creates the `frotas`, `entregas` and `notificacoes` schemas |
| `02-migration-frotas.sql` | Fleet tables |
| `03-migration-entregas.sql` | Delivery and tracking tables |
| `04-migration-notificacoes.sql` | Notification tables |
| `05-seed-frotas.sql` | Fleet seed data |
| `06-seed-entregas.sql` | Delivery seed data |
| `07-seed-notificacoes.sql` | Notification seed data |
| `08-add-motorista-veiculo-info.sql` | Driver/vehicle info on deliveries |
| `09-add-alertas-manutencao.sql` | Preventive maintenance alerts table (`frotas`) |
| `10-seed-template-manutencao-preventiva.sql` | `MANUTENCAO_PREVENTIVA` email template seed |

## Using the Project

To use Rotalog Workspace, follow these steps:

```bash
docker compose up -d
docker exec -it rotalog-db psql -U rotalog_admin -d rotalog
```

Connection details used by the APIs (credentials are defined in `docker-compose.yml`):

```text
Host:     localhost
Port:     5432
Database: rotalog
User:     rotalog_admin
Schemas:  frotas, entregas, notificacoes
```

Useful commands:

```bash
# Follow database logs
docker compose logs -f postgres

# List the schemas
docker exec -it rotalog-db psql -U rotalog_admin -d rotalog -c "\dn"

# Stop the database (data is kept in the pgdata volume)
docker compose down

# Reset the database and re-run all init scripts
docker compose down -v
docker compose up -d
```
