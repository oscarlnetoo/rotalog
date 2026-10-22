# Plan: Preventive Maintenance Alert

## Context
Spec: `docs/specs/preventive-maintenance-alert-spec.md`.

api-frotas must find vehicles that are due for preventive maintenance. For each one it calls api-notificacoes synchronously, and api-notificacoes emails the manager and returns sent/failed. api-frotas then stores the alert with that status, or `PENDENTE` if api-notificacoes is unreachable. painel-admin lists the alerts with a status filter.

The plan comes from four specialist analyses (api-frotas, api-notificacoes, frontend, workspace), coordinated so the interfaces agree. api-entregas is not involved.

**User decisions:**
- **Mileage rule:** km driven since the last completed maintenance, not the absolute odometer.
- **Pending alerts:** retried at the start of every check, then marked FALHA after the maximum number of attempts.
- **Existing 50,000 km alert:** kept unchanged.
- **Simulated email failure:** api-notificacoes gets a config flag to switch off its random 10% email failure for E2E.

## Shared vocabulary (used everywhere: DB, APIs, UI, tests; no translation)
- **Alert status:** `PENDENTE` | `ENVIADO` | `FALHA`.
- **Reason (`motivo`):** `QUILOMETRAGEM` | `TEMPO` | `AMBOS`. When both criteria hold, the vehicle gets a single alert with `AMBOS`.
- **JSON:** camelCase, dates as ISO strings, plain arrays, no auth (CORS is already open on both APIs).

## Interface contracts

### C1. api-notificacoes endpoint called by api-frotas (new)
`POST http://localhost:5000/api/notificacoes/alertas-manutencao`

Request:
```json
{ "veiculoId": 12, "placa": "ABC1D23", "modelo": "Fiat Fiorino",
  "quilometragemAtual": 52000, "limiteQuilometragem": 10000,
  "dataUltimaManutencao": "2025-03-01", "mesesLimite": 6,
  "motivo": "QUILOMETRAGEM", "destinatario": null }
```
- Required fields: `veiculoId`, `placa`, `motivo`.
- `destinatario` is optional. When it is omitted, the config value `AlertaManutencao:DestinatarioGestor` is used (default `gestor@rotalog.com`). api-frotas omits it.

Responses:
- 200 when a send was attempted: `{ notificacaoId, status: "ENVIADO"|"FALHA", destinatario, erroMensagem, dataEnvio, veiculoId }`. The service makes one attempt only, with no automatic retry.
- 400 `{ "error": "..." }` when validation fails.
- 500 `{ "error", "detalhes" }` on unexpected errors.

How api-frotas maps the response to an alert status:

| api-notificacoes response | Alert status |
|---|---|
| 200 + `ENVIADO` | `ENVIADO` |
| 200 + `FALHA` | `FALHA` |
| 4xx | `FALHA` |
| Connection error, timeout, 5xx, or 200 with an unknown status | `PENDENTE` (`tentativas` + 1) |

### C2. api-frotas API used by painel-admin (new)
Base URL: `http://localhost:8080/api`.

`GET /alertas-manutencao?status=PENDENTE|ENVIADO|FALHA`
- `status` is optional and case-insensitive.
- Returns a plain array ordered by `dataCriacao` descending.
- An invalid status returns 400 `{"erro": "Status inválido: X"}`.
- Item fields: `id, veiculoId, placa, modelo, motivo, quilometragemAtual, limiteQuilometragem, dataUltimaManutencao (yyyy-MM-dd|null), mesesDesdeUltimaManutencao, mesesLimite, status, notificacaoId, destinatario, erroMensagem, tentativas, dataCriacao, dataEnvio, dataAtualizacao`.

`POST /alertas-manutencao/verificar` (no body)
- Returns 200 whenever the check ran, including when api-notificacoes is down. Only a real server error returns 500.
- Response body:
```json
{ "veiculosVerificados": 7, "criados": 2, "enviados": 1, "pendentes": 1, "falhas": 0,
  "reenviados": 0, "ignoradosPorDuplicidade": 3, "alertas": [ /* items above */ ] }
```

### C3. Table `frotas.alertas_manutencao` (identical in 3 places)
The DDL must match exactly in all three places, because Flyway is disabled and `ddl-auto=validate` checks the entity against the table:
- workspace script `09-add-alertas-manutencao.sql`, which is what actually runs;
- Flyway file `V3__Alerta_Manutencao.sql`, kept as a copy;
- the JPA entity.
```sql
CREATE TABLE IF NOT EXISTS alertas_manutencao (
  id BIGSERIAL PRIMARY KEY, veiculo_id BIGINT NOT NULL, placa VARCHAR(7) NOT NULL,
  modelo VARCHAR(100), motivo VARCHAR(20) NOT NULL, quilometragem_atual BIGINT,
  limite_quilometragem BIGINT, data_ultima_manutencao DATE,
  meses_desde_ultima_manutencao INTEGER, meses_limite INTEGER,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE', notificacao_id BIGINT,
  destinatario VARCHAR(255), erro_mensagem TEXT, tentativas INTEGER NOT NULL DEFAULT 0,
  data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, data_envio TIMESTAMP,
  data_atualizacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE INDEX IF NOT EXISTS idx_alertas_manutencao_status ON alertas_manutencao (status);
CREATE INDEX IF NOT EXISTS idx_alertas_manutencao_veiculo ON alertas_manutencao (veiculo_id);
```
Java types:

| Columns | Java type |
|---|---|
| `id` and all BIGINT columns | `Long` |
| INTEGER columns | `Integer` |
| `data_ultima_manutencao` | `LocalDate` |
| TIMESTAMP columns | `LocalDateTime` |
| VARCHAR / TEXT columns | `String` (no `@Lob`) |

There is no foreign key, matching the existing tables.

## Implementation by repo (one commit scope each; land them in this order)

### 1. rotalog-workspace (`workspace`)
- **New `tools/scripts/09-add-alertas-manutencao.sql`:** the C3 DDL, starting with `SET search_path TO frotas;`. It must be idempotent, in the style of `08-add-motorista-veiculo-info.sql`.
- **New `tools/scripts/10-seed-template-manutencao-preventiva.sql`:** an idempotent insert of the `MANUTENCAO_PREVENTIVA` email template into `notificacoes.templates_notificacao`, using the `{{placa}}`, `{{modelo}}`, `{{quilometragem_atual}}`, `{{limite_quilometragem}}`, `{{data_ultima_manutencao}}`, `{{meses_limite}}` and `{{motivo}}` variables.
- **`docker-compose.yml`:** mount both new scripts.
- **`README.md`:** add both scripts to the script table.
- **Existing databases:** init scripts only run on an empty volume, so apply the new scripts by hand: `docker exec -i rotalog-db psql -U rotalog_admin -d rotalog < tools/scripts/09-...sql`.

### 2. rotalog-api-notificacoes (`api-notificacoes`)
- **New DTOs in `DTOs/`:** `AlertaManutencaoRequest` and `AlertaManutencaoResponse`.
- **`Services/NotificacaoService.cs`:** add `EnviarAlertaManutencao(req)`. It:
  - resolves the recipient (request value, then config, then the default);
  - builds the `Variaveis` and applies the template through the existing `AplicarTemplate`, falling back to a built-in message because `Mensagem` is required;
  - saves a `Notificacao` with `Tipo="MANUTENCAO_PREVENTIVA"`, `ServicoOrigem="api-frotas"` and `ReferenciaId="veiculo-{id}"`;
  - calls the existing `EnviarEmail` once and sets `ENVIADO` or `FALHA` explicitly, without going through the 3-attempt logic in `TentarEnviar`.
- **`Controllers/NotificacoesController.cs`:** add `[HttpPost("alertas-manutencao")]`. Use the existing try/catch and `{error}` pattern, and return 200 (not 201).
- **`appsettings.json`:**
  - add `AlertaManutencao:DestinatarioGestor`;
  - add `EmailSettings:SimularFalha` (default `true`). `EnviarEmail` throws its random 10% failure only when this is true, so legacy behaviour is unchanged.
- **Other files:** add a sample request to `requests.http`, and list the new endpoint in `Program.cs`.
- **`Data/seed.sql`:** add the same template insert as the workspace script.
- **No schema change:** the existing `notificacoes` table is reused.

### 3. rotalog-api-frotas (`api-frotas`)
New files under `src/main/java/com/rotalog/`:
- `domain/AlertaManutencao.java`: the entity, with String status constants.
- `repository/AlertaManutencaoRepository.java`: the list queries (all, and by status, both ordered by `dataCriacao` descending), the open-alert query used for duplicate checks, and a query for PENDENTE alerts with `tentativas < max`.
- `service/AlertaManutencaoService.java`: the eligibility check, duplicate check, retry and persistence (rules below).
- `controller/AlertaManutencaoController.java`: implements C2, using the try/catch and `{"erro"}` style of `ManutencaoController`.
- `dto/AlertaManutencaoResponse.java` and `dto/VerificacaoAlertasResponse.java`.
- `src/main/resources/db/migration/V3__Alerta_Manutencao.sql`: the C3 DDL.

Modified files:
- **`service/NotificacaoClient.java`:**
  - inject a `RestTemplate` built with `RestTemplateBuilder` and the connect/read timeouts;
  - read the base URL from `rotalog.api.notificacoes.url`;
  - add `enviarAlertaManutencao(...)`, which returns a result that tells "unreachable" apart from "responded";
  - leave the existing methods unchanged. They will pick up the configurable URL, which is a small side effect.
- **`repository/ManutencaoRepository.java`:** add a query for the last `CONCLUIDA` maintenance ordered by `COALESCE(data_manutencao, data_atualizacao)`. Do not reuse `findUltimaManutencao`: it would pick a PENDENTE row whose date is NULL.
- **`application.properties`:**
  - `rotalog.manutencao.alerta.limite-km=10000`
  - `rotalog.manutencao.alerta.limite-meses=6`
  - `rotalog.manutencao.alerta.max-tentativas=5`
  - `rotalog.api.notificacoes.url=http://localhost:5000`
  - `rotalog.api.notificacoes.connect-timeout-ms=2000`
  - `rotalog.api.notificacoes.read-timeout-ms=5000`

Rules for `verificar`:
1. **Retry first:** resend each PENDENTE alert with `tentativas < max`. When an alert reaches the maximum, mark it FALHA with an "attempts exhausted" message.
2. **Scope:** check only vehicles with status `ATIVO`.
3. **Baseline:** the last maintenance with status `CONCLUIDA`, of any type. If the vehicle has none, use `veiculo.dataCadastro` for the date and a km baseline of 0.
4. **Eligibility:**
   - QUILOMETRAGEM when `quilometragem - baseline km > limite-km`;
   - TEMPO when the months since the baseline date exceed `limite-meses`;
   - AMBOS when both hold.
5. **Duplicates:** skip a vehicle that already has a PENDENTE or ENVIADO alert created after its baseline date. FALHA alerts do not block a new one.
6. **Persistence:** save the alert as PENDENTE before the HTTP call, then update it with the C1 mapping.
7. **Circuit break:** once a call in the run hits a connection error or timeout, record the remaining new alerts as PENDENTE without calling.

Not changed: `VeiculoQuilometragemService`, `VeiculoNotificador`, and the hardcoded manager email in `ManutencaoService.java:154`.

### 4. rotalog-frontend (`frontend`)
All paths are under `apps/painel-admin/src/app/`.
- **`models/index.ts`:** add `AlertaManutencao` and `VerificacaoAlertas` types in camelCase. Do not copy the snake_case `Manutencao` model.
- **`services/frotas.service.ts`:** add `getAlertas(status?)` and `verificarAlertas()`, using the existing fetch, try/catch and `API_URL` style and `encodeURIComponent` for the query.
- **New `components/alertas/alertas.component.ts`:**
  - a standalone component cloned from `manutencoes.component.ts` (inline template, `.status-X` badges);
  - a status `<select>` (Todos / PENDENTE / ENVIADO / FALHA) that filters on the server through `?status=`;
  - a "Verificar agora" button that calls `verificar` and reloads;
  - loading, empty and minimal error states.
- **`app.routes.ts` and `components/layout/sidebar.component.ts`:** add an `alertas` route and an emoji menu entry.
- **Optional docs:** add the C2 paths and schema to `libs/api-contracts/src/openapi.yaml`.

## Tests

**Unit tests:**
- **api-notificacoes:** create the new test project `api-notificacoes.Tests/` (none exists today) with xUnit, EF Core InMemory 6.x, Microsoft.NET.Test.Sdk and coverlet. Cover:
  - recipient resolution order;
  - template rendering and the fallback message;
  - ENVIADO, and FALHA (with `SimularFalha`, or by injecting a failure);
  - the persisted fields;
  - controller responses: 400 for missing fields or an invalid `motivo`, 200 for both outcomes.

  Run with `dotnet test api-notificacoes.Tests`.
- **api-frotas:** JUnit 5 + Mockito, following `VeiculoNotificadorTest`. Run with `mvn test`.
  - `AlertaManutencaoServiceTest`: eligible by km, by time, by both; not eligible; no prior maintenance; ATIVO-only filter; duplicate skip; retry path and attempt cap; circuit break.
  - `NotificacaoClientTest`, using `MockRestServiceServer`: 200 ENVIADO, 200 FALHA, 200 with an unknown status, 4xx, 5xx, and an IOException.
  - `AlertaManutencaoControllerTest` (`@WebMvcTest`): the status filter, 400 for an invalid status, and the response shape of `verificar`.
- **frontend:** Jest. Run with `npx nx test painel-admin`.
  - `alertas.component.spec.ts`: rows render, the filter passes its value to the service, empty and loading states, badge classes.
  - `frotas.service.spec.ts`: stub `fetch`.
  - Run the suite once before starting, because `app.component.spec.ts` may already fail.

**E2E (Playwright, new `apps/painel-admin-e2e/src/alertas.spec.ts`):**
1. **Mocked UI test (always runs):** `page.route` serves rows with all three statuses. Assert the rows, and that picking FALHA sends a request containing `status=FALHA`.
2. **Full flow (`@fullstack`, skipped unless `E2E_FULLSTACK` is set):**
   - Start the stack in this order: postgres → notificacoes with `EmailSettings__SimularFalha=false` → frotas → painel-admin.
   - Create a dedicated vehicle through the frotas API with mileage over the limit.
   - Call `POST /api/alertas-manutencao/verificar`, open `/alertas`, and expect that plate with ENVIADO.
   - Optionally confirm the email record through `GET :5000/api/notificacoes`.
   - Assert on that plate only. The seed's 2024 dates make many seeded vehicles eligible, so counting alerts would be unreliable.
3. **Pending case (same env flag):** run frotas with `ROTALOG_API_NOTIFICACOES_URL=http://localhost:59999`. Running `verificar` should give PENDENTE for a new vehicle. Then restart frotas with the real URL and run `verificar` again: the same alert should become ENVIADO.

## Verification
1. Workspace:
   - `docker-compose down -v && docker-compose up -d` (destructive; confirm before running).
   - `\d frotas.alertas_manutencao` in psql to check the table.
   - Check that the template row exists.
2. api-notificacoes:
   - `dotnet build`, `dotnet test api-notificacoes.Tests`.
   - Send the `requests.http` sample. Expect 200 with ENVIADO when `SimularFalha=false`.
3. api-frotas:
   - `mvn test`.
   - `mvn spring-boot:run`. Startup confirms that `ddl-auto=validate` accepts the new table.
   - `curl -X POST :8080/api/alertas-manutencao/verificar`, then `curl ':8080/api/alertas-manutencao?status=ENVIADO'`.
   - Stop notificacoes, call `verificar` again, and expect PENDENTE alerts. Restart notificacoes and expect the next call to retry them.
4. Frontend:
   - `npx nx lint painel-admin`, `npx nx test painel-admin`, `npx nx build painel-admin`.
   - `npx nx e2e painel-admin-e2e`, then the same with `E2E_FULLSTACK=1` against the running stack.

## Known risks and caveats
- **Two emails for one vehicle:** the legacy 50k km alert stays, so the manager can receive both it and the new alert, which has a different notification type.
- **Possible duplicate email after a read timeout:** api-notificacoes may already have sent the email when api-frotas gives up waiting. api-frotas records PENDENTE and the next retry sends a second email. This is accepted and documented.
- **Concurrent checks:** two `verificar` calls running at the same time can race on the duplicate check. There is no unique index, so this is accepted.
- **`TECH_DEBT_MAP.md` is out of date:** the 50k alert it describes now lives in `VeiculoQuilometragemService`.
