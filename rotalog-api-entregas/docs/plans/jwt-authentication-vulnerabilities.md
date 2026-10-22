# Plan: fix V1–V6 of ADR 0001 (real JWT auth) in rotalog-api-entregas

## Context
`src/middleware/auth.js` is fake auth. It has a hardcoded secret fallback (V1), it is skipped whenever `NODE_ENV !== 'production'` (V2), any token of 10 or more characters passes (V3), every caller becomes a hardcoded admin (V4), `POST /api/rastreamento/:entregaId` is open to anyone (V5), and it writes a useless log line on every request (V6). ADR `docs/adr/0001-jwt-authentication.md` decides the fix (D1–D7). This plan follows its 6-step implementation plan, one commit per step, with `npm test` green after each step.

What I found while exploring:
- `.env` is **already untracked**: the root `.gitignore` has `**/.env`, and `git log -- .env` is empty. So `git rm --cached` is not needed. Only the misleading FIXME in `rotalog-api-entregas/.gitignore` changes (add `.env` there too).
- The local `JWT_SECRET` is 45 bytes, which passes the 32-byte check. The local `.env` sets `NODE_ENV=development` and has no `AUTH_MODE`.
- Requiring `models` / `config/database` does not open a DB connection, so supertest can import `app` without a database.
- Node 22 is installed locally. `jsonwebtoken` and `supertest` are not installed yet.
- Existing tests: `tests/middleware/auth.test.js` (characterization tests, they mutate `process.env`). Coverage covers only `src/middleware/auth.js`, with an 80% threshold.

## Steps (one commit each, scope `api-entregas`, no trailers per root CLAUDE.md)

### 1. `test`: route-mount characterization
- `npm i -D supertest`.
- `src/index.js`: export `app`, and call `startServer()` only when `require.main === module`.
- New `tests/routes/mount.test.js`. It mocks `../../src/models` (jest.mock) so handlers do not touch the DB. For each route in the D4 table, it records today's behaviour with `NODE_ENV=production`: health and all rastreamento routes return non-401, entregas/frotas without a token return 401.

### 2. `build`: dependency and secrets hygiene (D5)
- `npm i jsonwebtoken@^9`.
- `.gitignore`: replace the FIXME/commented `# .env` with `.env`.
- New `.env.example` with placeholders (`JWT_SECRET=CHANGE_ME_min_32_bytes`, `AUTH_MODE=off`, `JWT_ISSUER=rotalog`, `JWT_AUDIENCE=rotalog-api-entregas`, `JWT_ALGORITHMS=HS256`, DB vars without real values).
- README: in the setup section, copy `.env.example` to `.env`, and document `AUTH_MODE` and `npm run token:dev`. Replace the "bypassed when NODE_ENV…" sentence.
- Local `.env` (untracked): add `AUTH_MODE=off` and the `JWT_ISSUER`/`JWT_AUDIENCE`/`JWT_ALGORITHMS` keys. Rotate `JWT_SECRET` to a new random 48-byte value written by a node one-liner that never prints it. Real deploy rotation is reported to the user as a manual task.

### 3. `refactor`: `src/config/auth.js` (D3, A3, A4)
- `loadAuthConfig(env = process.env)` returns `Object.freeze({ mode, secret, issuer, audience, algorithms })`.
- `mode` defaults to `enforce`. It throws on a mode outside `enforce|report|off`, on `off` with `NODE_ENV=production`, and on `enforce`/`report` when the secret is missing or < 32 bytes (`Buffer.byteLength`). `algorithms` is a CSV and is never read from the token. `audience` defaults to `rotalog-api-entregas`. There is no secret fallback.
- `index.js`: wrap the load in try/catch, and on error `console.error` + `process.exit(1)` before anything else. In `off` mode, log one `console.warn` at startup.
- `middleware/auth.js` becomes a factory, `createAuthMiddleware(config)`: `off` → `next()`. For this step it keeps the old header checks so behaviour stays the same, and it drops the per-request logs (V6) and the hardcoded secret (V1).

### 4. `fix`: real verification and report mode (D1, D6, V3, V4)
- New `src/services/tokenService.js`: `verifyToken(token, config)` calls `jwt.verify(token, config.secret, { algorithms, issuer, audience, clockTolerance: 30 })`, requires `sub`, `role` and `exp` (it throws `JsonWebTokenError` when one is missing), and returns `{ id: sub, nome, role }`.
- `createAuthMiddleware(config)`:
  - It parses `Authorization` with a case-insensitive `Bearer` and exactly 2 parts.
  - It keeps the 401 bodies (`Token não fornecido` / `Token mal formatado` / `Token inválido`) and sets `WWW-Authenticate: Bearer` on every 401.
  - It logs `console.warn('[AUTH] <METHOD> <path> rejeitado: <err.name>')`, never with the token.
  - In `report` mode it sets `req.user` when the token is valid, logs failures, and always calls `next()`.
- Rewrite `tests/middleware/auth.test.js`:
  - The tests pass a config object and sign real tokens with `jsonwebtoken`.
  - Cases: valid token → exact `req.user`; expired, wrong secret, `alg: none`, HS512 when only HS256 is allowed, wrong iss, wrong aud, missing sub, missing role → 401 `Token inválido`; lowercase `bearer` accepted; header-parsing cases kept; report mode (next called, the warn log has no token); off mode.
- New `tests/config/auth.test.js` covers every D3 startup check, plus `tests/services/tokenService.test.js`.
- New `scripts/token-dev.js` and `"token:dev": "node scripts/token-dev.js"`. It reads `.env`, parses `--role` (default `operador`) and `--sub`, signs with the configured iss/aud/alg and a 1h expiry, and refuses when `NODE_ENV=production`. `requests.http` gets an `@token` variable and an `Authorization: Bearer {{token}}` header on the protected requests.

### 5. `fix`: authorization and closing the tracking write (D4, V5, R2, R3)
- New `src/middleware/authorize.js`: `requireRole(...roles)` → 403 `{ error: 'Acesso negado' }` when `req.user` is missing or its role is not allowed. It needs to know the mode (in `off`, authorization is skipped too), so it is built as `createAuthorize(config)` returning `requireRole`. In `report` mode a denial is logged and `next()` is called, matching the "never rejects" rule.
- Routers become factories that receive `{ authenticate, requireRole }`:
  - `routes/entregas.js`: POST/PUT/PATCH/DELETE get `requireRole('admin','operador')`.
  - `routes/rastreamento.js`: `POST /:entregaId` gets `authenticate, requireRole('admin','operador','motorista')`, and the GETs stay public.
  - `routes/frotas.js`: stays read-only, so any authenticated role.
- `index.js` builds config → `authenticate` → `requireRole` once, and mounts `authenticate` on `/api/entregas` and `/api/frotas` only. The `FIXME: sem auth no rastreamento` comment is removed.
- Update `mount.test.js` to the D4 policy (tracking POST now 401 without a token, 403 for the `servico` role), plus a new `tests/middleware/authorize.test.js` (allowed role, denied role, missing `req.user`, off, report).

### 6. `test`: coverage
- `collectCoverageFrom`: `src/config/auth.js`, `src/services/tokenService.js`, `src/middleware/auth.js`, `src/middleware/authorize.js`. Keep the 80% thresholds and fill any gaps.

## Also
- Set the ADR status to `Accepted`. Leave the TECH_DEBT_MAP entry open (the ADR says enforce depends on an issuer that does not exist yet).
- Flag to the user: this is a breaking change for consumers under `enforce`/`report` (painel-admin, rastreamento, api-frotas), and the secret must be rotated in real deployments.

## Verification
- `npm test` and `npm run test:coverage` after every step (thresholds ≥ 80%).
- Smoke test without the DB: `node -e "require('./src/index')"` with bad configs (`AUTH_MODE=foo`, `AUTH_MODE=off NODE_ENV=production`, a short secret) → exit 1 with a clear message.
- With the DB up (if available): `npm start` in `AUTH_MODE=enforce`, `npm run token:dev -- --role operador`, then curl: no token → 401 + `WWW-Authenticate`, valid token → 200, `--role servico` on POST → 403, tracking GET public, tracking POST without a token → 401.
