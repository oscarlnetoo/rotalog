# RotaLog — Technical Debt Map

> Generated 2026-10-20 from a static read of every source, config, and SQL file in the workspace (no builds or tests executed).
> Severity: 🔴 **Critical** (security hole or broken behavior) · 🟠 **High** (correctness/maintainability risk) · 🟡 **Medium** · ⚪ **Low**.
> Note: per `rotalog-workspace/CLAUDE_CONTEXT.md`, much of this debt is intentional course material. Items marked **(hidden)** are *not* flagged by any `FIXME`/`TODO` in the code — they are the subtle bugs worth prioritizing.

## Summary

| Project | Stack | Top risk |
|---|---|---|
| rotalog-api-frotas | Java 11 / Spring Boot 2.7 | No auth anywhere; non-transactional multi-entity updates |
| rotalog-api-entregas | Node 18 / Express 4 | Auth bypassed by the committed `.env`; unauthenticated tracking writes |
| rotalog-api-notificacoes | .NET 6 / EF Core 6 | Open notification relay + redirectable resend; reads other services' schemas |
| rotalog-frontend | NX 19 / Angular 18 + React 18 | Stored XSS in map popups; models don't match any backend |
| rotalog-workspace | Docker Compose + SQL | Seed data links events to the wrong deliveries; 3 copies of every schema |

---

## 1. rotalog-api-frotas — Java 11 / Spring Boot 2.7

### Architecture issues
| Sev | Issue | Location |
|---|---|---|
| 🟠 | No `@Transactional` anywhere. `iniciar/concluir/cancelarManutencao` save `Veiculo` and `Manutencao` separately — a failure between the two leaves the vehicle status out of sync. | `service/ManutencaoService.java:98-182` |
| 🟠 | `ManutencaoService` mutates `Veiculo` status directly through `VeiculoRepository`, bypassing `VeiculoService` — two owners for the same invariant. | `service/ManutencaoService.java:112-145` |
| 🟠 | **(hidden)** `GET /motoristas/cnh-vencida` sends one HTTP notification per expired driver *on every read* — a side effect on a safe/idempotent verb; every page refresh spams `api-notificacoes`. | `service/MotoristaService.java:126-143` |
| 🟠 | Synchronous, blocking `RestTemplate` calls to other services inside request threads, with no timeout, retry, or circuit breaker. `RestTemplate` instantiated with `new` (not a bean). | `service/NotificacaoClient.java`, `service/EntregaClient.java`, `controller/HealthCheckController.java:28` |
| 🟡 | `VeiculoService` (339 lines) mixes validation, persistence, notification, cost calculation, and a stub "external sync". | `service/VeiculoService.java` |
| 🟡 | `@Inheritance(TABLE_PER_CLASS)` on a class with no subclasses — no benefit, and blocks a future `IDENTITY` subclass hierarchy. | `domain/Veiculo.java:20` |
| 🟡 | Entities returned directly from controllers (no response DTOs); `Manutencao.veiculoId` is a raw `Long` instead of `@ManyToOne`. | all controllers, `domain/Manutencao.java:30` |
| 🟡 | Error handling copy-pasted in ~20 controller methods (`catch RuntimeException → Map{"erro"}`); no `@ControllerAdvice`, no domain exceptions. Validation errors and not-found are distinguished only by which controller method caught them. | `controller/*.java` |
| ⚪ | Dead code: `EntregaClient` is never injected; `calcularCustoManutencao`, `agendarManutencaoPreventiva` (unused locals), `sincronizarComSistemaExterno` (empty) are never called. | `service/EntregaClient.java`, `service/VeiculoService.java:208-332` |

### Code inconsistencies
| Sev | Issue | Location |
|---|---|---|
| 🟠 | **(hidden)** `EntregaClient.veiculoTemEntregasAtivas` compares against `"em_transito"` / `"pendente"` (lowercase), but entregas stores `EM_TRANSITO` / `PENDENTE` — would always return `false` if it were wired up. | `service/EntregaClient.java:64` |
| 🟠 | **(hidden)** `findUltimaManutencao` uses `ORDER BY data_manutencao DESC LIMIT 1`; PostgreSQL sorts `NULL` first on `DESC`, so a *pending* maintenance (null date) is returned as the "last" one (seed row 4 for vehicle 1 triggers this). | `repository/ManutencaoRepository.java:27` |
| 🟠 | **(hidden)** `cancelarManutencao` sets the vehicle back to `ATIVO` even if a *different* maintenance is still `EM_ANDAMENTO`. `concluirManutencao` has no state check (can conclude a cancelled one). | `service/ManutencaoService.java:129-182` |
| 🟠 | **(hidden)** Notification `tipo` values sent (`NOVO_VEICULO`, `NOVO_MOTORISTA`, `MANUTENCAO_CONCLUIDA`, `SMS`) have no matching template in `notificacoes` (`VEICULO_CADASTRADO`, …), and no `variaveis` are sent, so templates are never applied. | `service/*Service.java`, `tools/scripts/07-seed-notificacoes.sql` |
| 🟡 | Odometer can be decreased; `atualizarVeiculo` logs a warning, `atualizarQuilometragem` prints to `System.out` — two different behaviors for the same rule. | `service/VeiculoService.java:138-165` |
| 🟡 | **(hidden)** Every odometer update ≥ 50,000 km fires an `ALERTA_MANUTENCAO` (threshold is absolute, not interval-based) — 8 of 10 seeded vehicles alert on every update. | `service/VeiculoService.java:173, 249-256` |
| 🟡 | `NotificacaoClient.enviarNotificacao` rethrows (comment says "swallowing"), while `enviarSms` swallows — inconsistent contracts in the same class. | `service/NotificacaoClient.java:59-90` |
| 🟡 | Status/type fields are free `String`s (`ATIVO`, `MANUTENCAO`, …) validated ad hoc in one place only (`obterVeiculosPorStatus`); `listarPorStatus` for drivers accepts anything. | `domain/*.java`, `service/VeiculoService.java:194` |
| ⚪ | Typo `obterEstatisticasFreita`; stats returned as hand-built JSON `String` via `String.format`. | `service/VeiculoService.java:304` |
| ⚪ | README documents package `com/rotalog/frotas/` and folder `model/`; actual is `com/rotalog/` and `domain/` (root `CLAUDE.md` repeats the error). | `README.md:26-31` |

### Security vulnerabilities
| Sev | Issue | Location |
|---|---|---|
| 🔴 | No authentication/authorization at all (no Spring Security) — any caller can create, update, deactivate vehicles/drivers. | whole service |
| 🔴 | DB credentials in plain text in a committed file. | `application.properties:10-11` |
| 🟠 | CORS `allowedOrigins("*")` for all methods and headers. | `config/CorsConfig.java:23-26` |
| 🟠 | `BasicBinder=TRACE` + `show-sql` log every bound SQL parameter, including CNH numbers (PII); `MotoristaService` also logs CNH explicitly. | `application.properties:16,35`, `service/MotoristaService.java:66,79` |
| 🟡 | No Bean Validation despite `spring-boot-starter-validation` on the classpath; `@RequestBody` DTOs never `@Valid`. `concluirManutencao` does `new BigDecimal(body.get(...).toString())` on untyped `Map` input. | `dto/*.java`, `controller/ManutencaoController.java:97-102` |
| 🟡 | Exception messages echoed verbatim to clients. | all controllers |

### Dependency status
| Sev | Dependency | Status |
|---|---|---|
| 🟠 | Spring Boot **2.7.14** | OSS support ended Nov 2023; `javax.persistence` → requires the Jakarta migration to move to 3.x. |
| 🟠 | Java **11** | Old LTS; Boot 3 requires 17+. |
| 🟡 | `postgresql` **42.5.1** (pinned, overrides Boot BOM) | Below 42.5.5 → CVE-2024-1597 (SQLi, exploitable only with `preferQueryMode=simple`). |
| 🟡 | `flyway-core` **8.5.1** (pinned, overrides Boot BOM) | Pinned below the Boot-managed version; and Flyway is disabled anyway. |
| ⚪ | Maven plugins pinned to 2018-era versions (surefire 2.22.1, compiler 3.8.0, …) | Overrides the parent's newer managed versions. |

### Infrastructure quality
| Sev | Issue | Location |
|---|---|---|
| 🟠 | Flyway disabled (`spring.flyway.enabled=false`); schema owned by docker init scripts. `V1/V2` migrations are dead copies that drift from the real schema. `ddl-auto=validate` will fail at boot if they diverge. | `application.properties:24`, `db/migration/` |
| 🟠 | Zero tests (`src/test` doesn't exist). | — |
| 🟡 | README says `./mvnw spring-boot:run` but there is no Maven wrapper (`mvnw`, `.mvn/`) in the repo. | `README.md:18` |
| 🟡 | Service URLs hardcoded as constants (`localhost:3000`, `localhost:5000`); no profiles; no Actuator (manual `/health`, version hardcoded). No Dockerfile. | `service/*Client.java`, `controller/HealthCheckController.java` |
| ⚪ | `System.out.println` mixed with SLF4J; DEBUG logging for `org.springframework.web` in the only profile. | `service/VeiculoService.java:116,164,327` |

---

## 2. rotalog-api-entregas — Node.js 18 / Express 4 / Sequelize

### Architecture issues
| Sev | Issue | Location |
|---|---|---|
| 🟠 | Business logic lives in route handlers (pedido number generation, distance calc, state changes); the `services/` folder only holds HTTP clients. | `routes/entregas.js` |
| 🟠 | **(hidden)** `notificacaoService` is never `require`d — no delivery event ever produces a notification, despite templates existing for them. | `services/notificacaoService.js` |
| 🟠 | Entrega update + Rastreamento insert are two separate writes with no transaction (create, status change, assign, cancel). | `routes/entregas.js:167-190, 262-270, 311-319, 348-354` |
| 🟡 | `/api/frotas/*` is an ad-hoc proxy to api-frotas (claims to avoid CORS, but api-frotas already allows `*`); duplicates availability logic. | `routes/frotas.js`, `services/frotasService.js:94-107` |
| 🟡 | Denormalized `motorista_nome` / `veiculo_modelo` accepted verbatim from the client on `/atribuir` — never verified against api-frotas. | `routes/entregas.js:287-328` |
| 🟡 | `sequelize.sync()` at startup alongside 3 separate SQL schema files. | `index.js:91` |

### Code inconsistencies
| Sev | Issue | Location |
|---|---|---|
| 🟠 | No state machine: `PATCH /status` allows `ENTREGUE → PENDENTE`; `DELETE` cancels already-delivered orders; `EM_TRANSITO` overwrites `data_coleta` every time. | `routes/entregas.js:235-279, 336-363` |
| 🟠 | **(hidden)** `include: [{ limit: 5, order: [...] }]` on a `hasMany` — Sequelize ignores `order` inside a non-`separate` include, so the "last 5 events" are arbitrary. `findByPk` include has the same ignored `order`. | `routes/entregas.js:44-49, 99-103` |
| 🟡 | Three async styles in one router: `async/await`, `.then()` chains, and Node callbacks (`frotasService`). | `routes/*.js`, `services/frotasService.js` |
| 🟡 | `numero_pedido = 'PED-' + Date.now() + random(0-999)` — collision-prone and inconsistent with seeded format `PED-2024-NNN`. | `routes/entregas.js:153` |
| 🟡 | Distance is Euclidean degrees × 111 (wrong away from the equator); ETA hardcoded at 2 min/km. | `routes/entregas.js:156-165` |
| 🟡 | Error responses sometimes include `detalhes: error.message`, sometimes not; 404 bodies use `error`, frotas uses `erro`. | `routes/*.js` |
| ⚪ | README lists `/api/entregas/:id/tracking` and `/api/rotas` (don't exist) and `middlewares/` (actual: `middleware/`); `npm test` just exits 1. | `README.md` |

### Security vulnerabilities
| Sev | Issue | Location |
|---|---|---|
| 🔴 | Auth middleware is a no-op whenever `NODE_ENV !== 'production'` — and the committed `.env` sets `development`. In "production" it accepts any `Bearer` string ≥ 10 chars and injects a fake admin user. JWT is never verified. | `middleware/auth.js:16-46`, `.env:5` |
| 🔴 | `.env` with DB password and JWT secret is committed (`.env` explicitly un-ignored); same secrets duplicated as code fallbacks. | `.env`, `.gitignore:44-45`, `config/database.js:20`, `middleware/auth.js:12` |
| 🔴 | `/api/rastreamento` is fully unauthenticated, **including `POST /:entregaId`** — anyone can inject tracking events (feeds the frontend XSS below). Sequential IDs make all deliveries enumerable. | `index.js:76`, `routes/rastreamento.js:40-67` |
| 🔴 | No input validation on any body field; free-text `evento`/`descricao` stored and later rendered as HTML by the tracking portal. | `routes/rastreamento.js:51-57`, `routes/entregas.js` |
| 🟠 | Error handler returns `err.stack` to clients outside production and logs full request bodies. | `middleware/errorHandler.js:12-21` |
| 🟠 | CORS `*` hand-rolled; no rate limiting on public endpoints; no `helmet`. | `index.js:34-42` |
| 🟡 | Sequelize `logging: console.log` prints every query with parameter values. | `config/database.js:27` |

### Dependency status
| Sev | Dependency | Status |
|---|---|---|
| 🟠 | Node.js **18** (README) | EOL April 2025. No `engines` field enforces any version. |
| 🟡 | `express` ^4.18.2 (lock: 4.22.1) | Patched 4.x line; Express 5 is the current major. |
| 🟡 | `nodemon` ^2.0.20 (lock: 2.0.22) | Two majors behind (3.x). |
| ⚪ | `cors` ^2.8.5 | Declared but never imported (and absent from the lockfile). |
| ⚪ | `package-lock.json` exists but is listed in `.gitignore` | Non-reproducible installs. |

### Infrastructure quality
| Sev | Issue | Location |
|---|---|---|
| 🟠 | No tests, no linter, no formatter config. | `package.json:9` |
| 🟡 | Schema defined in three places (`src/config/migration.sql`, workspace `03-migration-entregas.sql` + `08-…`); seed in three (`seed.sql`, `populate.sql`, workspace `06-…`). `populate.sql` `TRUNCATE`s tables. | `src/config/` |
| 🟡 | Health check is static (`database: 'UNKNOWN'`); no graceful shutdown; `unhandledRejection` only logged. | `index.js:60-72, 135-144` |
| ⚪ | `console.log` everywhere instead of a logger; `db:migrate` script hardcodes host/user. No Dockerfile. | `package.json:10-11` |

---

## 3. rotalog-api-notificacoes — .NET 6 / EF Core 6

### Architecture issues
| Sev | Issue | Location |
|---|---|---|
| 🔴 | Bounded-context violation: health endpoint opens connections into the `frotas` and `entregas` schemas with the shared admin credentials (via a fragile `connectionString.Replace("SearchPath=…")`). | `Controllers/HealthController.cs:56-87` |
| 🟠 | "Clean Architecture" abandoned: MediatR registered but unused, no `Domain/Application/Infrastructure` folders (README claims they exist), single 369-line god service with no interface. | `Program.cs:38-41`, `Services/NotificacaoService.cs`, `README.md:25-33` |
| 🟠 | Sending is synchronous inside the `POST` request (no queue/background worker); batch processing exposed as a public HTTP endpoint instead of a hosted job. | `Services/NotificacaoService.cs:132`, `Controllers/NotificacoesController.cs:176` |
| 🟡 | `EnsureCreated()` at startup next to SQL scripts — and it silently does nothing when *any* table already exists in the database (always true here). App keeps running if the DB is down. | `Program.cs:81-95` |
| 🟡 | `ConfiguracaoNotificacao` (preferences, quiet hours) is mapped but never read. | `Models/ConfiguracaoNotificacao.cs` |

### Code inconsistencies
| Sev | Issue | Location |
|---|---|---|
| 🟠 | Email/SMS sending is fake with random failures (10% / 20%) — status `ENVIADO` is meaningless. | `Services/NotificacaoService.cs:280-329` |
| 🟠 | **(hidden)** `ReenviarNotificacao` has no status or retry-limit check: an already-`ENVIADO` notification is sent again (duplicate delivery), and a `FALHA` one at `MaxTentativas` gets attempt 4, 5, … on each call because `Tentativas` keeps incrementing past the max. | `Services/NotificacaoService.cs:141-163, 264` |
| 🟡 | **(hidden)** `DateTime.UtcNow` written to columns created as `TIMESTAMP` (without time zone) by the workspace scripts, while EF/Npgsql 6 maps `DateTime` to `timestamptz` — mismatch causes timezone drift or cast errors depending on session TZ. Verify at runtime. | `Models/*.cs`, `tools/scripts/04-migration-notificacoes.sql` |
| 🟡 | Validation duplicated in controller and service; template lookup only happens when `variaveis` is present (no caller sends it). | `Controllers/NotificacoesController.cs:88-93`, `Services/NotificacaoService.cs:82-104` |
| 🟡 | `ObterEstatisticas` loads the entire table into memory to count. | `Services/NotificacaoService.cs:172` |
| ⚪ | README endpoints (`/email`, `/sms`, `/historico`, `/health`) don't exist; actual is `/api/notificacoes` and `/api/health`. | `README.md:37-42` |

### Security vulnerabilities
| Sev | Issue | Location |
|---|---|---|
| 🔴 | No authentication at all: anyone can `POST /api/notificacoes` to any recipient — an open email/SMS relay once real sending is implemented. | `Program.cs:76`, `Controllers/NotificacoesController.cs` |
| 🔴 | **(hidden)** `POST /{id}/reenviar` accepts `NovoDestinatario` — an unauthenticated caller can redirect *any* stored notification (with its content) to an address they control. | `Services/NotificacaoService.cs:148-151` |
| 🟠 | Secrets in committed `appsettings.json`: DB password, SMTP password, SMS API key (explicitly not git-ignored). | `appsettings.json:10-20`, `.gitignore:10-11` |
| 🟠 | Health endpoint discloses row counts of other services and internal SMTP/SMS URLs; exception messages returned as `detalhes` on every 500. | `Controllers/HealthController.cs:65,81,101-106` |
| 🟡 | Swagger UI enabled in all environments; CORS `AllowAnyOrigin`; no HTTPS redirection; `AllowedHosts: *`. | `Program.cs:44-74` |
| 🟡 | Recipient addresses and message bodies logged at `Information`. | `Services/NotificacaoService.cs:127,255,288-319` |

### Dependency status
| Sev | Dependency | Status |
|---|---|---|
| 🟠 | **.NET 6** (`net6.0`) | EOL Nov 2024 — no security patches. |
| 🟠 | EF Core 6.0.12 / Npgsql.EFCore 6.0.8 | Tied to the EOL runtime; behind latest 6.0.x patches too. |
| 🟡 | MediatR 11.1.0 (+ deprecated `MediatR.Extensions.Microsoft.DependencyInjection`) | Unused — remove rather than upgrade (newer MediatR versions changed licensing). |
| ⚪ | Swashbuckle 6.5.0 | Outdated. |

### Infrastructure quality
| Sev | Issue | Location |
|---|---|---|
| 🟠 | No tests; concrete-class DI makes the service hard to unit test. | — |
| 🟡 | Duplicate schema/seed SQL in `Data/` vs workspace scripts; no EF migrations. | `Data/migration.sql`, `Data/seed.sql` |
| 🟡 | Health check hand-written instead of `IHealthCheck`; startup banner via `Console.WriteLine`; no structured logging; no Dockerfile. | `Program.cs:97-112` |

---

## 4. rotalog-frontend — NX 19 monorepo (Angular 18 + React 18)

### Architecture issues
| Sev | Issue | Location |
|---|---|---|
| 🟠 | Shared libs are orphaned: `shared-types`, `ui-components`, `api-contracts` have no `project.json`, no `tsconfig` path aliases, and no app imports them. Each app redefines its own types. | `libs/`, `tsconfig.base.json` |
| 🟠 | God components with inline templates and styles (list + form + detail in one file): `veiculos` 464 lines, `dashboard` 349, `motoristas` 251 (copy of veiculos). | `apps/painel-admin/src/app/components/` |
| 🟠 | `fetch()` instead of `HttpClient`; no interceptors, no auth, no environments; base URLs hardcoded per service. | `services/frotas.service.ts:8`, `services/entregas.service.ts:6` |
| 🟡 | No lazy loading or route guards. | `app.routes.ts` |
| 🟡 | React app: class components with `any` props/state; `MapView` loads Leaflet from a CDN at runtime even though `leaflet`/`react-leaflet` are declared in `package.json`. | `apps/rastreamento/src/` |
| 🟡 | "Public" tracking portal loads **all** deliveries from the authenticated `/api/entregas` list endpoint (works only because auth is bypassed in dev). | `apps/rastreamento/src/App.tsx:40` |

### Code inconsistencies
| Sev | Issue | Location |
|---|---|---|
| 🔴 | **(hidden)** Admin models don't match the Java API's camelCase JSON (`ano`/`km_atual`/`marca`/`tipo` vs `anoFabricacao`/`quilometragem`). Creating a vehicle sends `ano`, so the backend always rejects it ("Ano de fabricação inválido"). Drivers send `validade_cnh`/`categoria_cnh` vs `vencimentoCnh`/`categoriaCnh` → saved with null expiry and category "B". | `models/index.ts`, `veiculos.component.ts:369-372`, `motoristas.component.ts` |
| 🟠 | **(hidden)** Calls to endpoints/filters that don't exist: `DELETE /api/veiculos/{id}` (no such route), `?veiculoId=` on manutencoes and `?motorista=` on entregas (ignored → returns everything). | `services/frotas.service.ts:89-100,178-187`, `services/entregas.service.ts:39-48` |
| 🟠 | `StatusVeiculo.MANUTENCAO = 'EM_MANUTENCAO'` and `BAIXADO` don't exist in the backend (`MANUTENCAO`). `Entrega.codigo_rastreio` / `created_at` don't exist (`numero_pedido` / `data_criacao`). | `models/index.ts:48-75` |
| 🟠 | **(hidden)** `TrackingDashboard` polls the API every 5 s and **discards the response** — the UI never refreshes, but load is generated anyway. | `apps/rastreamento/src/components/TrackingDashboard.tsx:39-63` |
| 🟡 | Services swallow errors and return `[]`/`null`, so the UI shows "empty" instead of "failed". Manual cache in `FrotasService` never expires; `EntregasService` has none. | `services/*.ts` |
| 🟡 | `tsconfig.app.json` for `rastreamento` turns `strict` off, contradicting the strict base config. | `apps/rastreamento/tsconfig.app.json:7-11` |
| ⚪ | `STATUS_CONFIG` duplicated in two React components; 808-line `nx-welcome.component.ts` scaffold left in. | `DeliveryList.tsx:5`, `TrackingDashboard.tsx:6` |

### Security vulnerabilities
| Sev | Issue | Location |
|---|---|---|
| 🔴 | **(hidden)** Stored XSS: Leaflet popups are built by concatenating `destino_endereco`, `motorista_nome`, and tracking `descricao` into HTML strings. `descricao` comes from the *unauthenticated* `POST /api/rastreamento/:id`, so anyone can run script in every tracking-portal visitor's browser. | `apps/rastreamento/src/components/MapView.tsx:153-158, 205-215` |
| 🟠 | Admin panel has no authentication or route guards. | `app.routes.ts`, `app.component.ts` |
| 🟡 | Public portal exposes every customer's addresses, driver names, and plates (no per-order lookup). | `apps/rastreamento/src/App.tsx` |

### Dependency status
| Sev | Dependency | Status |
|---|---|---|
| 🟠 | Angular **18.2** (lock 18.2.14) | Support ended Nov 2025. |
| 🟠 | `package-lock.json` out of sync: `leaflet` and `react-leaflet` are declared but absent from the lockfile. `zone.js` and `rxjs` (required by Angular) aren't declared — present only transitively. | `package.json`, `package-lock.json` |
| 🟡 | Nx **19.8.4**, with deprecated `@nrwl/angular` / `@nrwl/react` installed alongside `@nx/*`. | `package.json:21-22` |
| 🟡 | Runtime deps (`@angular/*`, `react`, `react-dom`) declared as `devDependencies`. | `package.json:8-55` |
| ⚪ | `prettier` ^2 (v3 current); `@types/node` 18.16.9 pinned to an EOL Node line. | `package.json` |

### Infrastructure quality
| Sev | Issue | Location |
|---|---|---|
| 🟠 | Existing tests are broken: unit spec expects `'Welcome painel-admin'` / title `'painel-admin'`; e2e expects an `h1` containing `Welcome`. None match the real app. | `app.component.spec.ts`, `painel-admin-e2e/src/example.spec.ts` |
| 🟠 | `rastreamento` lint target uses `@nx/linter:eslint`, which isn't installed — lint can't run. No test target for `rastreamento` at all. | `apps/rastreamento/project.json:35` |
| 🟡 | Root `package.json` has no `scripts`; stray `error.log` (a git error) committed. | `package.json:5`, `error.log` |

---

## 5. rotalog-workspace — Docker Compose + SQL init scripts

### Architecture issues
| Sev | Issue | Location |
|---|---|---|
| 🟠 | Single shared database and a single superuser-style role for all three services (`GRANT ALL` on every schema) — any service can read or write any other's data, which is what makes the notificacoes health-check violation possible. | `tools/scripts/init-schemas.sql:17-19` |
| 🟠 | Schema ownership split three ways: workspace init scripts (actual source of truth), per-service SQL files, and Flyway/EF/Sequelize — already drifting (e.g. `08-add-motorista-veiculo-info.sql` patches columns the other copies already have). | `tools/scripts/`, each service |
| 🟡 | No referential integrity: `manutencoes.veiculo_id` and `rastreamentos.entrega_id` have no FKs (the latter is in-schema, so nothing prevents it). | `02-…`, `03-migration-entregas.sql` |

### Code inconsistencies (seed data)
| Sev | Issue | Location |
|---|---|---|
| 🔴 | **(hidden)** Rastreamento seeds use hardcoded `entrega_id`s that assume insert order = pedido number. Events labeled `PED-2024-003` attach to id 3 (actually `PED-2024-011`, already *ENTREGUE*), and `PED-2024-004` events attach to id 4 (`PED-2024-012`). The real 003/004 have no events. | `06-seed-entregas.sql:64-92` |
| 🟠 | **(hidden)** Entregas reference plate `JKL3M45`, which doesn't exist in `frotas` (the Iveco is `JKL0M12`, and it's in `MANUTENCAO`). Driver 4 (expired CNH since 2024-01-05) is assigned active deliveries. | `06-seed-entregas.sql:17,25`, `05-seed-frotas.sql` |
| 🟡 | `06-seed-entregas.sql` claims to be idempotent but uses plain `INSERT` on a `UNIQUE` column; `07` admits it isn't. Seeds fail on re-run. | `06-…:3`, `07-…:32` |
| ⚪ | Notification seed `referencia_id 'entrega-3'` / `'entrega-10'` refers to pedido numbers, not ids. | `07-seed-notificacoes.sql:26-28` |

### Security vulnerabilities
| Sev | Issue | Location |
|---|---|---|
| 🟠 | `POSTGRES_PASSWORD: rotalog123` hardcoded and the same password reused by every service; Postgres published on `0.0.0.0:5432`. | `docker-compose.yml:9,12` |

### Dependency status
| Sev | Dependency | Status |
|---|---|---|
| 🟡 | `postgres:14` (floating minor tag) | PG 14 EOL Nov 2026 — upgrade window is now. |
| ⚪ | `version: '3.8'` compose key | Obsolete in Compose v2 (ignored with a warning). |

### Infrastructure quality
| Sev | Issue | Location |
|---|---|---|
| 🟠 | Only the database is containerized; none of the 3 APIs or 2 frontends have a Dockerfile or compose service, so there is no one-command environment and no service discovery (hence all the `localhost` URLs). | `docker-compose.yml:29-33` |
| 🟠 | No health check, resource limits, backup, or restart policy on the DB container. No CI anywhere in the workspace. | `docker-compose.yml:25-27` |
| 🟡 | Init scripts run only on an empty volume; schema changes require `down -v` (data loss) — no migration path. | `README.md:47-48` |
| 🟡 | `CLAUDE_CONTEXT.md` and the root `CLAUDE.md` are out of date: they claim React 17 + Redux (actual: React 18, no Redux), MediatR handlers (none exist), a 400-line `VeiculoService` (339), and that frotas handles "pedidos, roteamento, tracking" (that's entregas). | `CLAUDE_CONTEXT.md:25-48`, `../CLAUDE.md` |

---

## Cross-project themes (where to start)

1. **Auth is absent end to end.** Fixing one service doesn't help while the others accept anonymous writes. Highest-value single fixes: lock down `POST /api/rastreamento/:id` (entregas) and escape popup HTML (frontend); together they close the stored XSS.
2. **No shared contract.** Field names, status enums, notification types, and endpoints differ between every pair of services and the UI. `libs/api-contracts/openapi.yaml` exists but is stale and unused. Generating types from a real spec would remove a whole class of the hidden bugs above.
3. **Schema has no single owner.** Pick one migration tool per service, delete the duplicate SQL copies, and give each service its own DB role scoped to its schema.
4. **Runtime EOL across the board.** Spring Boot 2.7, .NET 6, Node 18, and Angular 18 are all past end of support; Postgres 14 follows next month.
5. **Zero working tests in any project.** Add characterization tests before refactoring, especially around the maintenance/delivery state transitions.
