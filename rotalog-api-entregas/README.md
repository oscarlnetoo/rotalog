# Rotalog API Entregas

Delivery management microservice of the Rotalog system, built with Node.js 18 and Express 4. It handles orders and deliveries (`entregas`), driver/vehicle assignment and delivery tracking (`rastreamento`), and stores its data in the `entregas` schema of the shared PostgreSQL database through Sequelize. It also calls the fleet API (`rotalog-api-frotas`) and the notifications API (`rotalog-api-notificacoes`).

## Prerequisites

Before starting, make sure you've met the following requirements:

- You have installed version 18+ of `Node.js` and `npm`.
- You have the Rotalog database running (see `rotalog-workspace/README.md`).
- You have the `psql` client installed, only if you want to run the `db:migrate` / `db:seed` scripts manually.

## Installing the Project

The database schema and seed data are created by `rotalog-workspace`; the SQL scripts in `src/config/` can also be run manually.

1. Navigate to the project directory

    ```bash
    cd rotalog-api-entregas
    ```

2. Install the dependencies

    ```bash
    npm install
    ```

3. Configure the environment variables in the `.env` file

    ```bash
    NODE_ENV=development
    PORT=3000
    DB_HOST=localhost
    DB_PORT=5432
    DB_NAME=rotalog
    DB_USER=rotalog_admin
    DB_PASSWORD=ENTER YOUR DB PASSWORD
    DB_SCHEMA=entregas
    JWT_SECRET=ENTER YOUR JWT SECRET
    API_FROTAS_URL=http://localhost:8080/api
    API_NOTIFICACOES_URL=http://localhost:5000
    ```

4. (Optional) Run the migration and seed scripts manually

    ```bash
    npm run db:migrate
    npm run db:seed
    ```

## Using the Project

To use Rotalog API Entregas, follow these steps:

```bash
npm run dev
curl http://localhost:3000/api/health
```

Authentication is bypassed when `NODE_ENV` is not `production`; in production, requests to `/api/entregas` and `/api/frotas` need an `Authorization: Bearer <token>` header. Main endpoints:

```text
GET    /api/entregas
GET    /api/entregas/stats
GET    /api/entregas/:id
GET    /api/entregas/pedido/:numeroPedido
POST   /api/entregas
PUT    /api/entregas/:id
PATCH  /api/entregas/:id/status
PATCH  /api/entregas/:id/atribuir
DELETE /api/entregas/:id

GET    /api/rastreamento/:entregaId
GET    /api/rastreamento/:entregaId/ultimo
GET    /api/rastreamento/pedido/:numeroPedido
POST   /api/rastreamento/:entregaId

GET    /api/frotas/veiculos/disponiveis
GET    /api/frotas/veiculos/:placa
GET    /api/frotas/veiculos/:placa/disponibilidade
GET    /api/frotas/motoristas/:id
```

Other useful commands:

```bash
# Start without auto-reload
npm start

# List deliveries
curl http://localhost:3000/api/entregas
```

Ready-made requests for every endpoint are available in `requests.http` (VS Code REST Client or JetBrains HTTP Client).
