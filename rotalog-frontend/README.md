# Rotalog Frontend

NX 19 monorepo with the frontends of the Rotalog system. It contains `painel-admin`, an Angular 18 admin panel for managing vehicles, drivers and deliveries; `rastreamento`, a React 18 + Leaflet portal for tracking deliveries on a map; and `painel-admin-e2e`, the Playwright end-to-end tests for the admin panel. Shared code lives in `libs/` (`api-contracts`, `shared-types`, `ui-components`).

## Prerequisites

Before starting, make sure you've met the following requirements:

- You have installed version 18+ of `Node.js` and `npm`.
- You have the Rotalog APIs running (see the root `README.md`) so the apps have data to show.
- You have read `CLAUDE.md` for the NX workspace guidelines.

## Installing the Project

Run every task through NX (`npx nx <target> <project>`) instead of calling Angular, Webpack or Jest directly.

1. Navigate to the project directory

    ```bash
    cd rotalog-frontend
    ```

2. Install the dependencies

    ```bash
    npm install
    ```

3. (Optional) Install the Playwright browsers for the e2e tests

    ```bash
    npx playwright install
    ```

## Using the Project

To use Rotalog Frontend, follow these steps:

```bash
# Admin panel - http://localhost:4200
npx nx serve painel-admin

# Tracking portal - http://localhost:3001
npx nx serve rastreamento
```

Other useful commands:

```bash
# Build
npx nx build painel-admin
npx nx build rastreamento

# Lint
npx nx lint painel-admin
npx nx run-many -t lint

# Unit tests (Jest)
npx nx test painel-admin

# End-to-end tests (Playwright, starts painel-admin automatically)
npx nx e2e painel-admin-e2e

# Show the project graph
npx nx graph
```
