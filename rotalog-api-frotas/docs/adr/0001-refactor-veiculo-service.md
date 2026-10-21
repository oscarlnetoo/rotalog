# ADR 0001: Split VeiculoService and give it typed errors, a status enum and test coverage

- **Status:** Proposed
- **Date:** 2026-10-21
- **Scope:** `rotalog-api-frotas`, `service/VeiculoService.java` and the classes it touches
- **Related:** [`TECH_DEBT_MAP.md`](../../../TECH_DEBT_MAP.md), section 1

## Context

`VeiculoService` is the main business class for vehicles. It is 339 lines long and mixes input validation, persistence, odometer rules, maintenance alerts, notifications, statistics, and some dead code. `VeiculoController` is its only caller. The tech debt map and a read of the class turned up these problems:

### Responsibilities

| # | Problem | Location |
|---|---|---|
| R1 | The class mixes validation, persistence, notifications, odometer rules, statistics, a cost calculation and a stub "external sync". | whole class |
| R2 | Validation rules (plate, model, year, odometer) are written inline in `registrarVeiculo` and `atualizarQuilometragem`, so nothing else can reuse them. | `:69-91`, `:159-165` |
| R3 | The same try/catch block for notifications is copied four times, and the recipient `gestor@rotalog.com` is hardcoded in each copy. | `:107-117`, `:174-183`, `:219-227`, `:273-281` |
| R4 | `precisaDeManutencao(Long)` loads the vehicle from the database again right after `atualizarQuilometragem` loaded and saved it. | `:173`, `:249-256` |
| R5 | `obterEstatisticasFreita` loads every vehicle for each status just to count them, then builds the JSON by hand with `String.format`. The method name has a typo. | `:304-316` |
| R6 | Three public methods are never called: `agendarManutencaoPreventiva`, `calcularCustoManutencao` and `sincronizarComSistemaExterno` (which is empty). | `:208-242`, `:324-332` |
| R7 | Dependencies are injected into fields with `@Autowired`, so the class can only be unit tested through `@InjectMocks` and reflection. | `:27-31` |

### Errors

| # | Problem | Location |
|---|---|---|
| E1 | Every error is a plain `RuntimeException`. The controller cannot tell "not found" from "invalid input" or "duplicate", so it picks the HTTP status from which endpoint caught the error, not from what went wrong. | `:49, 57, 70-90, 160, 196` |
| E2 | Notification failures are caught as `Exception`, which also hides programming errors such as an NPE in the message text. | `:113, 180, 225, 279` |

### Code hygiene

| # | Problem | Location |
|---|---|---|
| H1 | Javadoc on almost every method repeats the method name ("Busca veículo por id"). Many blocks are just lists of `FIXME`/`TODO` notes that this refactor resolves or that are already tracked in `TECH_DEBT_MAP.md`. | whole class |
| H2 | The class logs with `System.out.println` alongside SLF4J. | `:116`, `:164`, `:327` |
| H3 | Statuses (`"ATIVO"`, `"INATIVO"`, `"MANUTENCAO"`) are free strings, written out in 7 places in this class, in `ManutencaoService`, and in a JPQL literal in `VeiculoRepository`. Only `obterVeiculosPorStatus` checks them. | `:98, 195, 266, 291, 307-309`, `domain/Veiculo.java:43` |
| H4 | Two local variables are never used: `intervaloQuilometragem` and `intervaloMeses`. | `:212-213` |
| H5 | When the odometer goes down, `atualizarVeiculo` logs a warning and `atualizarQuilometragem` prints to `System.out`. The same rule behaves two different ways. | `:138-142`, `:163-165` |

### Tests

| # | Problem |
|---|---|
| T1 | `VeiculoServiceTest` has only 2 tests, both for `buscarPorId`, and checks for `RuntimeException`. Nothing measures coverage: there is no JaCoCo in `pom.xml`. |

### Known behaviour this ADR does *not* change

These bugs are listed in the tech debt map. Fixing them would change behaviour that clients can observe, so they stay out of this refactor (see [Out of scope](#out-of-scope)):

- The maintenance alert fires on every odometer update at or above 50,000 km. The threshold is an absolute value, not an interval.
- The odometer can still go down. The service logs a warning but does not reject the update.
- The notification `tipo` values have no matching templates in `api-notificacoes`.

## Decision

This is a **behaviour-preserving refactor**. Routes, request and response JSON, HTTP status codes and error messages stay the same. Only the internal structure changes.

### D1. Split responsibilities (R1–R6)

Replace the single class with these focused classes in `com.rotalog.service` (plus a `veiculo` sub-package if it helps readability). Each class should stay well under 150 lines.

| Class | Responsibility | Methods taken from `VeiculoService` |
|---|---|---|
| `VeiculoService` | Look up, register and update vehicles | `listarTodos`, `buscarPorId`, `buscarPorPlaca`, `obterVeiculosPorStatus`, `registrarVeiculo`, `atualizarVeiculo` |
| `VeiculoStatusService` | Change a vehicle's status | `desativarVeiculo`, `reativarVeiculo` |
| `VeiculoQuilometragemService` | Odometer updates and the maintenance threshold | `atualizarQuilometragem`, `precisaDeManutencao` (now takes a `Veiculo`, not an id: fixes R4) |
| `VeiculoEstatisticasService` | Fleet statistics | `obterEstatisticas` (typo fixed) returns an `EstatisticasFrotaResponse` DTO and uses a new `VeiculoRepository.countByStatus` |
| `VeiculoValidator` (`@Component`) | Pure validation rules: plate, model, year, odometer | the inline checks from `registrarVeiculo` and `atualizarQuilometragem` |
| `VeiculoNotificador` (`@Component`) | Sends vehicle notifications. It is the only class that catches and logs notification failures, and it holds the recipient constant | the 4 copied try/catch blocks |

The vehicle's status is still owned by the vehicle classes, and `ManutencaoService` still writes to `VeiculoRepository` directly. Routing it through `VeiculoStatusService` is a separate change (see Out of scope).

Delete the dead methods from R6 instead of moving them. Their unused variables (H4) go with them. Git history keeps the code if anyone needs it later.

`EstatisticasFrotaResponse` must serialize to exactly `{"total", "ativos", "inativos", "em_manutencao"}`, using `@JsonProperty("em_manutencao")`. `VeiculoController.obterEstatisticas` then returns `ResponseEntity<EstatisticasFrotaResponse>`, and the body clients receive does not change.

Every new class, and `VeiculoService` itself, uses **constructor injection** (`private final` fields plus Lombok `@RequiredArgsConstructor`) (R7).

### D2. Specific exceptions (E1, E2)

Add a `com.rotalog.exception` package with an unchecked hierarchy:

```
VeiculoException (abstract, extends RuntimeException)
├── VeiculoNaoEncontradoException          // by id or by plate
├── PlacaDuplicadaException
├── PlacaInvalidaException                 // missing or not 7 characters
├── ModeloInvalidoException                // missing
├── AnoFabricacaoInvalidoException         // null or outside 1900–2100
├── QuilometragemInvalidaException         // negative
└── StatusVeiculoInvalidoException         // not a StatusVeiculo value
```

- Each exception keeps the **exact message the code sends today** (for example, `"Veículo não encontrado: " + id`), because those messages reach clients in the `{"erro": ...}` body.
- These exceptions extend `RuntimeException`, so the existing `catch (RuntimeException e)` blocks in `VeiculoController` keep working, with the same status codes and the same body. Moving the HTTP mapping into a `@ControllerAdvice` is planned as a follow-up (see Out of scope). Once there, the codes can follow the exception type (for example, 409 for `PlacaDuplicadaException`), but that is a client-visible change and needs its own decision.
- `VeiculoNotificador` catches only `RestClientException` (what `NotificacaoClient.enviarNotificacao` rethrows). Any other exception is a bug and is allowed to propagate.

### D3. `StatusVeiculo` enum (H3)

- Create `domain/StatusVeiculo { ATIVO, INATIVO, MANUTENCAO }`, with a `static StatusVeiculo from(String)` method that throws `StatusVeiculoInvalidoException("Status inválido: " + status)`. The match stays case-sensitive, as it is today.
- Change `Veiculo.status` to `StatusVeiculo` with `@Enumerated(EnumType.STRING)`. The column stays `VARCHAR(20)`, so `ddl-auto=validate` passes and **no migration is needed**. Every value in the seed data (`05-seed-frotas.sql`) is one of the three constants.
- Update the other places that use these values:
  - `ManutencaoService:115, 142, 174-175`
  - the JPQL literal `v.status = 'ATIVO'` in `VeiculoRepository.findVeiculosAtivosComQuilometragemAlta`: replace it with a parameter
  - `findByStatus` and the new `countByStatus` take `StatusVeiculo`
- Jackson writes the enum by name, so the JSON stays `"status": "ATIVO"`.
- Notification types get the same treatment: a `TipoNotificacaoVeiculo` enum (`NOVO_VEICULO`, `ALERTA_MANUTENCAO`, `VEICULO_DESATIVADO`) inside `VeiculoNotificador`. The string values sent stay the same.
- Driver (`Motorista`) and maintenance (`Manutencao`) statuses are out of scope. They should get the same pattern in their own ADRs.

### D4. Comments and logging (H1, H2, H5)

- Remove Javadoc that only repeats the method name or signature. Keep a comment only where it explains a non-obvious *why*. One example is the "absolute threshold" in `VeiculoQuilometragemService`, which explains why the alert fires on every update.
- Remove `FIXME`/`TODO` notes that this refactor resolves. Unresolved items (pagination, cache, audit trail, authorization, metrics) are already tracked in `TECH_DEBT_MAP.md`. They come out of the code instead of being copied into the new classes.
- Replace each `System.out.println`:
  - `:116`: remove it. The `log.error` on the line before already records the failure.
  - `:164`: replace it with the same `log.warn` that `atualizarVeiculo` uses. `VeiculoValidator` now provides this odometer-decrease check to both paths (H5), which still only log.
  - `:327`: deleted along with `sincronizarComSistemaExterno` (D1).

### D5. At least 80% unit test coverage

- Add `jacoco-maven-plugin` (0.8.x, which supports Java 11) to `pom.xml`, with `prepare-agent`, `report`, and a `check` goal bound to `verify`.
- Set the check rule to **≥ 80% line coverage and ≥ 80% branch coverage**, applied to the classes from D1 and D2: `com.rotalog.service.Veiculo*`, `com.rotalog.exception.*`, and `StatusVeiculo`. `mvn verify` fails below that threshold. The rule covers only these classes so that untested legacy services do not block the build.
- Write unit tests with JUnit 5 and Mockito, one test class per production class, under `src/test/java/com/rotalog/...`. Mock only repositories and `NotificacaoClient`. At minimum, cover:
  - every validation branch, each asserting the **specific** exception type and its message
  - that a notification failure does not fail registration or deactivation, and that a non-`RestClientException` is not swallowed
  - the maintenance threshold at 49,999 km, 50,000 km and with a `null` odometer
  - the statistics DTO, including its JSON field names (one `ObjectMapper` assertion)
  - `StatusVeiculo.from` for each valid value, an invalid value, and lowercase input
- Update the existing `buscarPorIdLancaExcecaoQuandoNaoExiste` test to expect `VeiculoNaoEncontradoException`.
- **Write characterization tests first**, against the current `VeiculoService`, before moving any code. They pin down today's behaviour so the split can be shown to preserve it.

## Consequences

**Positive**
- Each class has one reason to change. `VeiculoService` drops from 339 lines to roughly 80.
- Errors have types, so the planned `@ControllerAdvice` becomes a mechanical change.
- Invalid statuses can no longer be stored. The compiler catches typos such as `"ATIVOS"`.
- Statistics run 3 `COUNT` queries instead of loading the whole table 3 times.
- Coverage is enforced by the build, not just promised.

**Negative / risks**
- More classes for readers to navigate. This is acceptable given how small each one is.
- `Veiculo.status` becoming an enum touches `ManutencaoService` and `VeiculoRepository` as well as the vehicle classes. If any database row holds a value outside the enum, reading that row fails at runtime. This holds for the seed data, but production data must be checked before release, for example with `SELECT DISTINCT status FROM frotas.veiculos`.
- Clients and the API contract see no change. Consumers (`api-entregas`, `painel-admin`) need nothing. If a later change alters HTTP codes or messages, it must be flagged as breaking.

## Alternatives considered

- **Keep one class and only extract private methods.** Rejected: the class would stay over 300 lines, and the notification and statistics code would still be mixed in with persistence.
- **A single `VeiculoInvalidoException` for every validation error.** Rejected: the requirement is one exception per kind of error, and separate types let tests and the future `@ControllerAdvice` tell the cases apart.
- **Keep `status` as a `String` and check it against the enum only at the edges.** Rejected: invalid states could still be stored, and the 7 string literals would remain.
- **Move the dead methods into a `ManutencaoPreventivaService`.** Rejected: nothing calls them, and maintenance already belongs to `ManutencaoService`. Moving them would preserve dead code.

## Out of scope

These are tracked in `TECH_DEBT_MAP.md` §1 and each needs its own decision:

- `@ControllerAdvice` and changes to HTTP status codes or error bodies
- `@Transactional` boundaries; having `ManutencaoService` go through `VeiculoStatusService`
- Rejecting odometer decreases; making the maintenance threshold interval-based
- Fixing the notification template names; making notifications asynchronous
- Response DTOs for `Veiculo`, pagination, caching, and authentication

## Implementation plan

Each step is one commit with scope `api-frotas`. Each must pass `mvn verify` before the next step starts.

1. `test`: write characterization tests for the current `VeiculoService`, and add JaCoCo as report-only.
2. `refactor`: remove dead code, unused variables, `System.out`, and redundant Javadoc.
3. `refactor`: introduce the exception hierarchy and `StatusVeiculo` (entity, repository, `ManutencaoService`).
4. `refactor`: extract `VeiculoValidator` and `VeiculoNotificador`; switch to constructor injection.
5. `refactor`: extract `VeiculoStatusService`, `VeiculoQuilometragemService` and `VeiculoEstatisticasService`; rewire `VeiculoController`.
6. `test`: add unit tests for each new class and turn on the JaCoCo 80% `check` rule.
