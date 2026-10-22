# ADR 0001: Replace the fake auth middleware with real JWT verification and per-route authorization

- **Status:** Proposed
- **Date:** 2026-10-22
- **Scope:** `rotalog-api-entregas`: `src/middleware/auth.js`, how it is mounted in `src/index.js`, and the secrets it depends on
- **Related:** [`TECH_DEBT_MAP.md`](../../../TECH_DEBT_MAP.md), section 2 (Security vulnerabilities, rows 1–3)

## Context

`authMiddleware` guards `/api/entregas` and `/api/frotas`. It is 51 lines long, and its own header says it was copied from StackOverflow in 2020. It never decodes a JWT. The tech debt map, a read of the middleware, and a search for its callers and consumers turned up these problems:

### Vulnerabilities

| # | Problem | Location |
|---|---|---|
| V1 | The JWT secret has a hardcoded fallback, so the service starts with a known secret whenever `JWT_SECRET` is missing. The real value is also in the committed `.env` (`.env` is deliberately left out of `.gitignore`), so it is in git history and must be treated as leaked. | `auth.js:12`, `.env:19`, `.gitignore:44-45` |
| V2 | Auth is **fail-open**: it is skipped whenever `NODE_ENV !== 'production'`. Any other value (`development`, `staging`, `prod`, a typo, or unset) turns it off. The committed `.env` sets `development`, so a deploy that uses it runs with no auth at all. | `auth.js:16-19`, `.env:5` |
| V3 | In production mode the only "validation" is that the token is at least 10 characters long. Signature, expiry, issuer and audience are never checked. `Bearer aaaaaaaaaa` is accepted. | `auth.js:27-43` |
| V4 | Every accepted request gets the same hardcoded identity, `{ id: 1, nome: 'Admin', role: 'admin' }`. Any caller is an admin, and nothing can tell callers apart for auditing. | `auth.js:46` |
| V5 | `/api/rastreamento` is mounted with no auth, including `POST /:entregaId`. Anyone can write tracking events, and the tracking portal later renders their free text as HTML (stored XSS, see section 4 of the debt map). | `index.js:76`, `routes/rastreamento.js:40-67` |
| V6 | Each request logs `[AUTH] Bypass em desenvolvimento` or `Token aceito (sem validação real)` with `console.log`. This adds noise but records no useful fact: who called, or why a token was rejected. | `auth.js:17`, `auth.js:43` |

### Architecture

| # | Problem | Location |
|---|---|---|
| A1 | There is no token issuer in the system. No service has a login endpoint, no library can sign or verify a JWT (`jsonwebtoken` is not a dependency), and no consumer sends an `Authorization` header: `painel-admin` (`entregas.service.ts:18-41`), `rastreamento` (`App.tsx:40`, `TrackingDashboard.tsx:56`) and api-frotas `EntregaClient` (unused today). Auth "works" only because it is bypassed. | whole system |
| A2 | Security policy is chosen per **router mount**, all or nothing. A router cannot have some public routes and some protected ones. That is why the tracking router, which needs public reads but protected writes, ended up fully open. | `index.js:75-77` |
| A3 | Security behaviour is tied to `NODE_ENV`, a variable meant for runtime tuning (logging, caching). The same fail-open check also decides whether `errorHandler` sends stack traces to clients. | `auth.js:16`, `errorHandler.js:20` |
| A4 | Configuration is read from `process.env` when the module loads, with no validation. A missing or weak secret is not found at startup. In tests, behaviour can only be changed by mutating `process.env`. | `auth.js:12`, `tests/middleware/auth.test.js:4-21` |

### Responsibilities

| # | Problem | Location |
|---|---|---|
| R1 | One function does five jobs: it reads configuration, applies environment policy, parses the header, "validates" the token, and makes up an identity. Each of these changes for a different reason. | `auth.js:11-49` |
| R2 | There is no authorization layer. `req.user.role` is set but **no route reads `req.user`**, so the RBAC the `FIXME` asks for has nowhere to go. | `auth.js:46`, `routes/*.js` |
| R3 | The tracking router owns no security decisions. It relies on `index.js` to protect it, and `index.js` does not. | `index.js:76`, `routes/rastreamento.js:1-7` |

### Tests

| # | Problem |
|---|---|
| T1 | `tests/middleware/auth.test.js` (commit `e7c202b`) consists of characterization tests. They pin today's behaviour, including insecure cases such as "accepts a long token without validating signature or expiry". They are a useful safety net, but this ADR changes several of their expectations on purpose. No test covers which routes are mounted with or without auth. |

### Known behaviour this ADR does *not* change

- `/api/health` and the read-only tracking routes (`GET /api/rastreamento/...`) stay public. Delivery IDs are sequential, so they can still be enumerated (see [Out of scope](#out-of-scope)).
- CORS stays `*`. Bearer tokens are sent in a header, not a cookie, so a wildcard origin does not by itself expose them.
- `errorHandler` still exposes stack traces outside production.

## Decision

Unlike the frotas refactor (ADR 0001 in `rotalog-api-frotas`), this is a **deliberate security change, not a behaviour-preserving refactor**. Requests that are accepted today will be rejected. Routes, response bodies, and the existing 401 messages stay the same. Section D6 controls the rollout so that consumers do not break all at once.

### D1. Verify real JWTs (V3, V4)

- Add `jsonwebtoken` `^9` as a dependency. Version 9 rejects `alg: none` and fixes the CVE-2022-23529 family.
- Verify every token with `jwt.verify(token, key, { algorithms, issuer, audience, clockTolerance: 30 })`. The algorithm list is **pinned in configuration and never read from the token header**. This blocks algorithm-confusion attacks.
- Start with **HS256** and a shared secret, because the system has no issuer that could publish a public key (A1). All verification code lives in one module (D2), so a later move to RS256 or JWKS changes configuration and that module, nothing else.
- Required claims: `sub`, `role`, `exp`, `iss`, `aud`. `nome` is optional. `aud` must be `rotalog-api-entregas`.
- `req.user` is built **only from verified claims**: `{ id: sub, nome, role }`. The hardcoded admin is removed.
- Responses keep the current shape and messages:

  | Case | Status | Body |
  |---|---|---|
  | No `Authorization` header | 401 | `{"error": "Token não fornecido"}` |
  | Not `Bearer <token>` | 401 | `{"error": "Token mal formatado"}` |
  | Bad signature, expired, wrong `iss`/`aud`, missing claim | 401 | `{"error": "Token inválido"}` |
  | Valid token, role not allowed | 403 | `{"error": "Acesso negado"}` (new) |

  All 401 responses add `WWW-Authenticate: Bearer` (RFC 6750). Every verification failure returns the same `Token inválido` message, so clients cannot probe which check failed. The specific reason (`TokenExpiredError`, `JsonWebTokenError`, …) is logged on the server only.
- The `Bearer` scheme is matched case-insensitively, as RFC 7235 requires. This changes one characterization test (`bearer` in lowercase is currently rejected).

### D2. Split the middleware by responsibility (R1, A4)

| Module | Responsibility |
|---|---|
| `src/config/auth.js` | Reads and validates `AUTH_MODE`, `JWT_SECRET`, `JWT_ISSUER`, `JWT_AUDIENCE` and `JWT_ALGORITHMS` **once, at startup**, and returns a frozen config object. No fallbacks. |
| `src/services/tokenService.js` | `verifyToken(token, config)`: a pure function that wraps `jwt.verify` and maps claims to the user object. It has no Express dependency. |
| `src/middleware/auth.js` | `createAuthMiddleware(config)`: parses the header, calls `tokenService`, sets `req.user`, and sends the 401. |
| `src/middleware/authorize.js` | `requireRole(...roles)`: returns 403 when `req.user.role` is not in the list (D4). |

The factory takes its config as an argument, so tests pass a config object instead of mutating `process.env`. `index.js` builds the config, creates the middleware once and mounts it.

### D3. Fail closed, with an explicit mode instead of `NODE_ENV` (V1, V2, A3)

- A new variable, `AUTH_MODE`, takes one of `enforce | report | off`. **The default when it is unset is `enforce`.** Forgetting to configure auth now locks the service instead of opening it.
- `NODE_ENV` no longer affects auth.
- Startup **fails** (`process.exit(1)` with a clear message, before `app.listen`) when:
  - `AUTH_MODE` is `enforce` or `report` and `JWT_SECRET` is missing or shorter than 32 bytes
  - `AUTH_MODE` is `off` and `NODE_ENV` is `production`
  - `AUTH_MODE` has any value outside the three above
- `off` disables authentication **and** authorization. It must be chosen explicitly in a local `.env`, and startup logs one `warn` line saying so (not one line per request, see V6).
- The hardcoded secret fallback in `auth.js:12` is deleted.

### D4. Per-route authorization (R2, R3, A2, V5)

Each router declares its own policy with `requireRole`. `index.js` keeps a single `authenticate` mount per protected router. Tracking reads stay public, so the tracking router authenticates per route instead of at the mount. Roles:

| Role | Meaning |
|---|---|
| `admin` | Full access |
| `operador` | Back-office staff: manages deliveries |
| `motorista` | Driver app: posts tracking events |
| `servico` | Another RotaLog service (for example api-frotas): read-only |

| Route | Authentication | Allowed roles |
|---|---|---|
| `GET /api/health` | none | — |
| `GET /api/rastreamento/*` | none (unchanged) | — |
| `POST /api/rastreamento/:entregaId` | **required (new)** | `admin`, `operador`, `motorista` |
| `GET /api/entregas*`, `GET /api/frotas/*` | required | any valid role |
| `POST`, `PUT`, `PATCH`, `DELETE /api/entregas*` | required | `admin`, `operador` |

`DELETE /api/entregas/:id` is not limited to `admin` on purpose. Today it cancels a delivery, which is an operator task. Narrowing it would be a separate product decision.

### D5. Secrets out of the repository (V1)

- Add `.env` to `.gitignore` and remove it from the index (`git rm --cached .env`). Add a committed `.env.example` with placeholders and `AUTH_MODE=off` for local development, and update the README setup section to copy it.
- **Rotate `JWT_SECRET`.** The committed value is in git history permanently. Removing the file does not make it secret again.
- Add `npm run token:dev -- --role operador`: a script that reads the local `.env` and prints a signed token for manual testing with `requests.http`. It refuses to run when `NODE_ENV=production`.

### D6. Rollout: `report` mode first (A1)

Switching straight to `enforce` would break every consumer, because none of them sends a token. The rollout is in stages:

1. Deploy with `AUTH_MODE=report`. The middleware verifies tokens and sets `req.user` when one is valid, but **never rejects**. Each failure is logged as one `warn` line with method, path, and reason (never the token). The logs show which callers still need to be updated.
2. Consumers start sending tokens. Issuing those tokens is out of scope (see below). Until an issuer exists, the only tokens available are ones minted with the shared secret.
3. Switch to `enforce` once the `report` logs show no unexpected failures.

**This is a breaking change for consumers** (`painel-admin`, `rastreamento`, and api-frotas if `EntregaClient` is ever wired up). Each must be told before step 3.

### D7. Tests

- Add `supertest` as a dev dependency, and export `app` from `index.js` without starting the server, so route mounting can be tested without a database.
- **Characterization first:** before changing any code, add a route-mount test that records which routes are public today. That test then changes on purpose in step 5 of the plan, so the diff shows exactly which routes were closed.
- Unit tests with real tokens signed by `jsonwebtoken` inside the tests (no mocking of `jwt.verify`) cover at least:
  - a valid token, and the exact `req.user` it produces
  - an expired token, a wrong signature, `alg: none`, a different algorithm (`HS512` when only `HS256` is allowed), and wrong `iss` and `aud`, each giving 401 `Token inválido`
  - a missing `sub` or `role` claim
  - `report` mode, which calls `next()` on failure and logs without the token
  - `off` mode
  - every startup check in D3
  - `requireRole` for an allowed role, a denied role, and a missing `req.user`
- Rewrite the existing characterization tests in `auth.test.js` that pin insecure behaviour (the 10-character token, the hardcoded user, the `NODE_ENV` bypass) to match D1–D3. Keep the header-parsing cases.
- Extend `collectCoverageFrom` in `package.json` to the four modules in D2. Keep the existing **80% threshold** for statements, branches, functions and lines.

## Consequences

**Positive**
- A request is accepted only if it carries a token signed with a secret the attacker does not have. Today, any 10-character string works.
- A misconfigured deploy refuses to start. It no longer runs silently without auth.
- Closing `POST /api/rastreamento/:entregaId` removes the write side of the stored-XSS chain. The frontend still has to escape popup HTML (debt map, section 4), but anonymous users can no longer inject the payload.
- `req.user` holds a real identity, so audit logs and per-user rules become possible.
- Each module has one job and can be tested without touching `process.env`.

**Negative / risks**
- Without an issuer (A1), `enforce` cannot be switched on in practice. This ADR makes the service *ready* for real auth, but closing the hole fully depends on the identity work listed in Out of scope. Until then, a production deploy should run in `report` mode, and the debt map entry stays open.
- An HS256 shared secret means every service that verifies tokens can also **mint** them. This is acceptable for a single verifying service. It becomes a risk once more services verify the same tokens, and that is the point to move to RS256 (see Alternatives).
- The `rastreamento` portal lists deliveries through the authenticated `GET /api/entregas` (debt map, section 4). Under `enforce` the portal stops working until it uses the public `GET /api/rastreamento/pedido/:numero`.
- Developers who pull this change must create a local `.env` from `.env.example`, because `.env` is no longer in the repository.

## Alternatives considered

- **Keep the `NODE_ENV` check and only add real verification.** Rejected: the bypass would still be fail-open, and the committed `.env` would still turn it off.
- **RS256 with a JWKS endpoint now.** This is the target, but it needs an issuer that publishes keys, and none exists. Choosing it now would block the whole ADR on identity work. D2 keeps the move to RS256 to one module.
- **`express-jwt` or Passport.** Rejected for now: both wrap `jsonwebtoken`, which this ADR uses directly. One small middleware is easier to read and test than a framework layer in a codebase that does not use either today.
- **Auth at an API gateway or reverse proxy.** This is a reasonable long-term option, but the workspace has no gateway and does not containerize the APIs (debt map, section 5). The service must protect itself first.
- **Put the whole tracking router behind auth.** Rejected: the public tracking portal needs anonymous reads by order number. Only the write route needs protection.

## Out of scope

These are tracked in `TECH_DEBT_MAP.md` and each needs its own decision:

- A token issuer (login endpoint or identity provider), refresh tokens, and token revocation
- Adding login and the `Authorization` header to `painel-admin` and `rastreamento`
- Rate limiting, `helmet`, and restricting CORS origins
- Enumerable sequential IDs on public tracking reads; replacing them with order-number lookups only
- `errorHandler` exposing stack traces and logging request bodies (it uses the same `NODE_ENV` check as A3)
- The hardcoded database password fallback in `config/database.js:20`
- Input validation and escaping for tracking event text
- Auth in api-frotas and api-notificacoes, which have none at all (debt map, sections 1 and 3)

## Implementation plan

Each step is one commit with scope `api-entregas`. Each must pass `npm test` before the next step starts.

1. `test`: add `supertest`, export `app` from `index.js`, and add the route-mount characterization test.
2. `build`: add `jsonwebtoken`; untrack `.env`; add `.env.example`; update the README. Rotate the secret wherever the service is deployed.
3. `refactor`: add `config/auth.js` with the D3 startup checks, and switch from `NODE_ENV` to `AUTH_MODE` with `off` as the local default. Behaviour stays the same for a correctly configured local `.env`.
4. `fix`: add `tokenService` and `createAuthMiddleware` with real verification and `report` mode; rewrite the affected characterization tests; add `token:dev`.
5. `fix`: add `requireRole`, apply the D4 policy, and protect `POST /api/rastreamento/:entregaId`; update the route-mount test.
6. `test`: fill coverage gaps and extend the coverage threshold to all auth modules.
