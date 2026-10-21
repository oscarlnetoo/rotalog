# Unit tests for VeiculoService (≥80% coverage)

## Context
`VeiculoService` (rotalog-api-frotas) is the main legacy service. It has only 2 tests (`buscarPorId` found / not found) in
`rotalog-api-frotas/src/test/java/com/rotalog/service/VeiculoServiceTest.java`. The goal is at least 80% coverage of the
class, measured with JaCoCo (report only, no threshold that fails the build). This is a legacy course codebase, so **production code stays unchanged**. Tests describe what the code does now, including its deliberate quirks.

## 1. Add JaCoCo report to the build: `rotalog-api-frotas/pom.xml`
Add `org.jacoco:jacoco-maven-plugin:0.8.12` under `<build><plugins>` with executions:
- `prepare-agent` (default phase)
- `report` bound to the `test` phase, so `mvn test` writes `target/site/jacoco/` (`index.html`, `jacoco.csv`)

No `check` goal.

## 2. Extend `VeiculoServiceTest.java` (keep the existing style)
Keep the existing setup: `@ExtendWith(MockitoExtension.class)`, `@Mock VeiculoRepository`, `@Mock NotificacaoClient`, `@InjectMocks VeiculoService`. Use Portuguese camelCase test names (`metodoFazXQuandoY`), static JUnit/Mockito imports, and no `@DisplayName` or nested classes. Add a small private helper `veiculo(Long id, String placa, Long km)`. Use `ArgumentCaptor` / `verify` to check saved entities and notifications. Save stubs use `thenAnswer(inv -> inv.getArgument(0))`.

Cases per method:
- **listarTodos**: returns `findAll()` result.
- **buscarPorPlaca**: found; not found → RuntimeException.
- **registrarVeiculo**:
  - success: placa is uppercased, status ATIVO, km 0, both dates set, `NOVO_VEICULO` sent to `gestor@rotalog.com`
  - placa null / empty / not 7 chars → exception (`@ParameterizedTest` + `@NullAndEmptySource`/`@ValueSource`)
  - duplicate placa → exception, `save` never called
  - modelo null / empty → exception
  - ano null / 1899 / 2101 → exception; boundaries 1900 and 2100 accepted
  - notification throws → vehicle still returned (exception swallowed)
- **atualizarVeiculo**: updates all fields; null/empty modelo and null ano/km keep old values; lower km is still applied (only logged); not found → exception.
- **atualizarQuilometragem**:
  - negative → exception, no save
  - < 50000 → saved, no notification
  - ≥ 50000 → `ALERTA_MANUTENCAO` sent
  - lower than current → still saved
  - notification failure swallowed

  Note: `precisaDeManutencao` calls `findById` again, so the same stub covers both calls.
- **obterVeiculosPorStatus**: ATIVO/INATIVO/MANUTENCAO delegate to `findByStatus` (parameterized); invalid → exception.
- **agendarManutencaoPreventiva**: sends `MANUTENCAO_AGENDADA` with placa + km; notification failure swallowed; not found → exception.
- **calcularCustoManutencao**: 0 km → 500.0; 10000 km → 1000.0.
- **precisaDeManutencao**: 49999 → false; 50000 → true; null km → false.
- **desativarVeiculo**: status INATIVO, saved, `VEICULO_DESATIVADO` sent; notification failure swallowed.
- **reativarVeiculo**: status ATIVO, saved, no notification.
- **obterEstatisticasFreita**: stubs `count()` and `findByStatus` for the 3 statuses, then asserts the exact JSON string.
- **sincronizarComSistemaExterno**: runs without exception and has no repository/client interaction (`verifyNoInteractions`).

Intentional bugs, such as an NPE on a null status, are left untested and not fixed.

## Verification
- `cd rotalog-api-frotas && mvn test`: all tests pass.
- Read `target/site/jacoco/jacoco.csv`, row `com.rotalog.service,VeiculoService`. Expected: line and branch coverage ≥ 80% (target ~100% line coverage).

## Commits (scope `api-frotas`, no trailers per root CLAUDE.md)
1. `build(api-frotas): add jacoco coverage report to test phase`
2. `test(api-frotas): cover VeiculoService with unit tests`
