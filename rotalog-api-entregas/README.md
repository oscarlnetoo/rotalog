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

3. Create your local `.env` from the example and fill in the placeholders (`.env` is not versioned)

    ```bash
    cp .env.example .env
    ```

    Authentication is controlled by `AUTH_MODE` (`enforce` is the default when unset, `report` verifies tokens but never rejects, `off` disables auth and is refused when `NODE_ENV=production`). With `enforce` or `report`, `JWT_SECRET` must have at least 32 bytes; the service refuses to start otherwise.

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

Authentication depends on `AUTH_MODE`, not on `NODE_ENV`: unless it is `off` (local development only), requests to `/api/entregas` and `/api/frotas` need an `Authorization: Bearer <token>` header. For manual testing, `npm run token:dev -- --role operador` prints a signed token (roles: `admin`, `operador`, `motorista`, `servico`). Main endpoints:

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
