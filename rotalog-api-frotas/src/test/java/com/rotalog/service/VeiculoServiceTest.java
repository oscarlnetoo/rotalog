package com.rotalog.service;

import com.rotalog.domain.Veiculo;
import com.rotalog.repository.VeiculoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VeiculoServiceTest {

    @Mock
    private VeiculoRepository veiculoRepository;

    @Mock
    private NotificacaoClient notificacaoClient;

    @InjectMocks
    private VeiculoService veiculoService;

    private Veiculo veiculo(Long id, String placa, Long km) {
        Veiculo veiculo = new Veiculo();
        veiculo.setId(id);
        veiculo.setPlaca(placa);
        veiculo.setModelo("Modelo Antigo");
        veiculo.setAnoFabricacao(2015);
        veiculo.setStatus("ATIVO");
        veiculo.setQuilometragem(km);
        return veiculo;
    }

    @Test
    void buscarPorIdRetornaVeiculoQuandoExiste() {
        Veiculo veiculo = new Veiculo();
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));

        assertSame(veiculo, veiculoService.buscarPorId(1L));
    }

    @Test
    void buscarPorIdLancaExcecaoQuandoNaoExiste() {
        when(veiculoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> veiculoService.buscarPorId(99L));
    }

    @Test
    void listarTodosRetornaResultadoDoRepositorio() {
        List<Veiculo> lista = Arrays.asList(veiculo(1L, "ABC1234", 0L), veiculo(2L, "DEF5678", 0L));
        when(veiculoRepository.findAll()).thenReturn(lista);

        assertSame(lista, veiculoService.listarTodos());
    }

    @Test
    void buscarPorPlacaRetornaVeiculoQuandoExiste() {
        Veiculo veiculo = veiculo(1L, "ABC1234", 0L);
        when(veiculoRepository.findByPlaca("ABC1234")).thenReturn(Optional.of(veiculo));

        assertSame(veiculo, veiculoService.buscarPorPlaca("ABC1234"));
    }

    @Test
    void buscarPorPlacaLancaExcecaoQuandoNaoExiste() {
        when(veiculoRepository.findByPlaca("ZZZ0000")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> veiculoService.buscarPorPlaca("ZZZ0000"));
    }

    @Test
    void registrarVeiculoSalvaVeiculoAtivoEmMaiusculoENotificaGestor() {
        when(veiculoRepository.findByPlaca("abc1234")).thenReturn(Optional.empty());
        when(veiculoRepository.save(any(Veiculo.class))).thenAnswer(inv -> inv.getArgument(0));

        Veiculo resultado = veiculoService.registrarVeiculo("abc1234", "Volvo FH", 2020);

        ArgumentCaptor<Veiculo> captor = ArgumentCaptor.forClass(Veiculo.class);
        verify(veiculoRepository).save(captor.capture());
        Veiculo salvo = captor.getValue();
        assertEquals("ABC1234", salvo.getPlaca());
        assertEquals("Volvo FH", salvo.getModelo());
        assertEquals(2020, salvo.getAnoFabricacao());
        assertEquals("ATIVO", salvo.getStatus());
        assertEquals(0L, salvo.getQuilometragem());
        assertNotNull(salvo.getDataCadastro());
        assertNotNull(salvo.getDataAtualizacao());
        assertSame(salvo, resultado);
        verify(notificacaoClient).enviarNotificacao(eq("NOVO_VEICULO"), eq("gestor@rotalog.com"), contains("ABC1234"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"ABC123", "ABC12345"})
    void registrarVeiculoLancaExcecaoQuandoPlacaInvalida(String placa) {
        assertThrows(RuntimeException.class, () -> veiculoService.registrarVeiculo(placa, "Volvo FH", 2020));

        verify(veiculoRepository, never()).save(any());
    }

    @Test
    void registrarVeiculoLancaExcecaoQuandoPlacaDuplicada() {
        when(veiculoRepository.findByPlaca("ABC1234")).thenReturn(Optional.of(veiculo(1L, "ABC1234", 0L)));

        assertThrows(RuntimeException.class, () -> veiculoService.registrarVeiculo("ABC1234", "Volvo FH", 2020));

        verify(veiculoRepository, never()).save(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    void registrarVeiculoLancaExcecaoQuandoModeloInvalido(String modelo) {
        when(veiculoRepository.findByPlaca("ABC1234")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> veiculoService.registrarVeiculo("ABC1234", modelo, 2020));

        verify(veiculoRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {1899, 2101})
    void registrarVeiculoLancaExcecaoQuandoAnoForaDoIntervalo(int ano) {
        when(veiculoRepository.findByPlaca("ABC1234")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> veiculoService.registrarVeiculo("ABC1234", "Volvo FH", ano));

        verify(veiculoRepository, never()).save(any());
    }

    @Test
    void registrarVeiculoLancaExcecaoQuandoAnoNulo() {
        when(veiculoRepository.findByPlaca("ABC1234")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> veiculoService.registrarVeiculo("ABC1234", "Volvo FH", null));

        verify(veiculoRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {1900, 2100})
    void registrarVeiculoAceitaAnosNosLimites(int ano) {
        when(veiculoRepository.findByPlaca("ABC1234")).thenReturn(Optional.empty());
        when(veiculoRepository.save(any(Veiculo.class))).thenAnswer(inv -> inv.getArgument(0));

        Veiculo resultado = veiculoService.registrarVeiculo("ABC1234", "Volvo FH", ano);

        assertEquals(ano, resultado.getAnoFabricacao());
    }

    @Test
    void registrarVeiculoRetornaVeiculoQuandoNotificacaoFalha() {
        when(veiculoRepository.findByPlaca("ABC1234")).thenReturn(Optional.empty());
        when(veiculoRepository.save(any(Veiculo.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("falha")).when(notificacaoClient)
                .enviarNotificacao(anyString(), anyString(), anyString());

        Veiculo resultado = veiculoService.registrarVeiculo("ABC1234", "Volvo FH", 2020);

        assertEquals("ABC1234", resultado.getPlaca());
    }

    @Test
    void atualizarVeiculoAtualizaTodosOsCampos() {
        Veiculo existente = veiculo(1L, "ABC1234", 1000L);
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(existente));
        when(veiculoRepository.save(any(Veiculo.class))).thenAnswer(inv -> inv.getArgument(0));

        Veiculo resultado = veiculoService.atualizarVeiculo(1L, "Novo Modelo", 2022, 5000L);

        assertEquals("Novo Modelo", resultado.getModelo());
        assertEquals(2022, resultado.getAnoFabricacao());
        assertEquals(5000L, resultado.getQuilometragem());
        assertNotNull(resultado.getDataAtualizacao());
    }

    @ParameterizedTest
    @NullAndEmptySource
    void atualizarVeiculoMantemModeloAntigoQuandoModeloNuloOuVazio(String modelo) {
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo(1L, "ABC1234", 1000L)));
        when(veiculoRepository.save(any(Veiculo.class))).thenAnswer(inv -> inv.getArgument(0));

        Veiculo resultado = veiculoService.atualizarVeiculo(1L, modelo, 2022, 5000L);

        assertEquals("Modelo Antigo", resultado.getModelo());
    }

    @Test
    void atualizarVeiculoMantemAnoEQuilometragemQuandoNulos() {
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo(1L, "ABC1234", 1000L)));
        when(veiculoRepository.save(any(Veiculo.class))).thenAnswer(inv -> inv.getArgument(0));

        Veiculo resultado = veiculoService.atualizarVeiculo(1L, "Novo Modelo", null, null);

        assertEquals(2015, resultado.getAnoFabricacao());
        assertEquals(1000L, resultado.getQuilometragem());
    }

    @Test
    void atualizarVeiculoAplicaQuilometragemMenorQueAAtual() {
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo(1L, "ABC1234", 1000L)));
        when(veiculoRepository.save(any(Veiculo.class))).thenAnswer(inv -> inv.getArgument(0));

        Veiculo resultado = veiculoService.atualizarVeiculo(1L, null, null, 500L);

        assertEquals(500L, resultado.getQuilometragem());
    }

    @Test
    void atualizarVeiculoLancaExcecaoQuandoNaoExiste() {
        when(veiculoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> veiculoService.atualizarVeiculo(99L, "M", 2020, 1L));

        verify(veiculoRepository, never()).save(any());
    }

    @Test
    void atualizarQuilometragemLancaExcecaoQuandoNegativa() {
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo(1L, "ABC1234", 1000L)));

        assertThrows(RuntimeException.class, () -> veiculoService.atualizarQuilometragem(1L, -1L));

        verify(veiculoRepository, never()).save(any());
    }

    @Test
    void atualizarQuilometragemSalvaSemNotificarQuandoAbaixoDoLimite() {
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo(1L, "ABC1234", 1000L)));
        when(veiculoRepository.save(any(Veiculo.class))).thenAnswer(inv -> inv.getArgument(0));

        Veiculo resultado = veiculoService.atualizarQuilometragem(1L, 49999L);

        assertEquals(49999L, resultado.getQuilometragem());
        verifyNoInteractions(notificacaoClient);
    }

    @Test
    void atualizarQuilometragemEnviaAlertaQuandoAtingeLimite() {
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo(1L, "ABC1234", 1000L)));
        when(veiculoRepository.save(any(Veiculo.class))).thenAnswer(inv -> inv.getArgument(0));

        veiculoService.atualizarQuilometragem(1L, 50000L);

        verify(notificacaoClient).enviarNotificacao(eq("ALERTA_MANUTENCAO"), eq("gestor@rotalog.com"), contains("ABC1234"));
    }

    @Test
    void atualizarQuilometragemSalvaQuandoMenorQueAAtual() {
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo(1L, "ABC1234", 1000L)));
        when(veiculoRepository.save(any(Veiculo.class))).thenAnswer(inv -> inv.getArgument(0));

        Veiculo resultado = veiculoService.atualizarQuilometragem(1L, 500L);

        assertEquals(500L, resultado.getQuilometragem());
        verify(veiculoRepository).save(resultado);
    }

    @Test
    void atualizarQuilometragemEngoleFalhaDaNotificacao() {
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo(1L, "ABC1234", 1000L)));
        when(veiculoRepository.save(any(Veiculo.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("falha")).when(notificacaoClient)
                .enviarNotificacao(anyString(), anyString(), anyString());

        Veiculo resultado = veiculoService.atualizarQuilometragem(1L, 60000L);

        assertEquals(60000L, resultado.getQuilometragem());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ATIVO", "INATIVO", "MANUTENCAO"})
    void obterVeiculosPorStatusDelegaParaORepositorio(String status) {
        List<Veiculo> lista = Collections.singletonList(veiculo(1L, "ABC1234", 0L));
        when(veiculoRepository.findByStatus(status)).thenReturn(lista);

        assertSame(lista, veiculoService.obterVeiculosPorStatus(status));
    }

    @Test
    void obterVeiculosPorStatusLancaExcecaoQuandoStatusInvalido() {
        assertThrows(RuntimeException.class, () -> veiculoService.obterVeiculosPorStatus("QUEBRADO"));

        verify(veiculoRepository, never()).findByStatus(anyString());
    }

    @Test
    void agendarManutencaoPreventivaNotificaComPlacaEQuilometragem() {
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo(1L, "ABC1234", 1000L)));

        veiculoService.agendarManutencaoPreventiva(1L, 60000L);

        ArgumentCaptor<String> mensagem = ArgumentCaptor.forClass(String.class);
        verify(notificacaoClient).enviarNotificacao(eq("MANUTENCAO_AGENDADA"), eq("gestor@rotalog.com"), mensagem.capture());
        assertTrue(mensagem.getValue().contains("ABC1234"));
        assertTrue(mensagem.getValue().contains("60000"));
    }

    @Test
    void agendarManutencaoPreventivaEngoleFalhaDaNotificacao() {
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo(1L, "ABC1234", 1000L)));
        doThrow(new RuntimeException("falha")).when(notificacaoClient)
                .enviarNotificacao(anyString(), anyString(), anyString());

        veiculoService.agendarManutencaoPreventiva(1L, 60000L);

        verify(notificacaoClient).enviarNotificacao(eq("MANUTENCAO_AGENDADA"), anyString(), anyString());
    }

    @Test
    void agendarManutencaoPreventivaLancaExcecaoQuandoNaoExiste() {
        when(veiculoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> veiculoService.agendarManutencaoPreventiva(99L, 1L));

        verifyNoInteractions(notificacaoClient);
    }

    @Test
    void calcularCustoManutencaoRetornaApenasCustoBaseQuandoKmZero() {
        assertEquals(500.0, veiculoService.calcularCustoManutencao("Volvo FH", 0L));
    }

    @Test
    void calcularCustoManutencaoSomaCustoPorKm() {
        assertEquals(1000.0, veiculoService.calcularCustoManutencao("Volvo FH", 10000L));
    }

    @Test
    void precisaDeManutencaoRetornaFalseAbaixoDoLimite() {
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo(1L, "ABC1234", 49999L)));

        assertFalse(veiculoService.precisaDeManutencao(1L));
    }

    @Test
    void precisaDeManutencaoRetornaTrueNoLimite() {
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo(1L, "ABC1234", 50000L)));

        assertTrue(veiculoService.precisaDeManutencao(1L));
    }

    @Test
    void precisaDeManutencaoRetornaFalseQuandoQuilometragemNula() {
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo(1L, "ABC1234", null)));

        assertFalse(veiculoService.precisaDeManutencao(1L));
    }

    @Test
    void desativarVeiculoMarcaComoInativoSalvaENotifica() {
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo(1L, "ABC1234", 1000L)));
        when(veiculoRepository.save(any(Veiculo.class))).thenAnswer(inv -> inv.getArgument(0));

        Veiculo resultado = veiculoService.desativarVeiculo(1L);

        assertEquals("INATIVO", resultado.getStatus());
        verify(veiculoRepository).save(resultado);
        verify(notificacaoClient).enviarNotificacao(eq("VEICULO_DESATIVADO"), eq("gestor@rotalog.com"), contains("ABC1234"));
    }

    @Test
    void desativarVeiculoEngoleFalhaDaNotificacao() {
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo(1L, "ABC1234", 1000L)));
        when(veiculoRepository.save(any(Veiculo.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("falha")).when(notificacaoClient)
                .enviarNotificacao(anyString(), anyString(), anyString());

        Veiculo resultado = veiculoService.desativarVeiculo(1L);

        assertEquals("INATIVO", resultado.getStatus());
    }

    @Test
    void reativarVeiculoMarcaComoAtivoSemNotificar() {
        Veiculo inativo = veiculo(1L, "ABC1234", 1000L);
        inativo.setStatus("INATIVO");
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(inativo));
        when(veiculoRepository.save(any(Veiculo.class))).thenAnswer(inv -> inv.getArgument(0));

        Veiculo resultado = veiculoService.reativarVeiculo(1L);

        assertEquals("ATIVO", resultado.getStatus());
        verify(veiculoRepository).save(resultado);
        verifyNoInteractions(notificacaoClient);
    }

    @Test
    void obterEstatisticasFreitaRetornaJsonComContagens() {
        when(veiculoRepository.count()).thenReturn(6L);
        when(veiculoRepository.findByStatus("ATIVO")).thenReturn(Arrays.asList(new Veiculo(), new Veiculo(), new Veiculo()));
        when(veiculoRepository.findByStatus("INATIVO")).thenReturn(Arrays.asList(new Veiculo(), new Veiculo()));
        when(veiculoRepository.findByStatus("MANUTENCAO")).thenReturn(Collections.singletonList(new Veiculo()));

        assertEquals("{\"total\": 6, \"ativos\": 3, \"inativos\": 2, \"em_manutencao\": 1}",
                veiculoService.obterEstatisticasFreita());
    }

    @Test
    void sincronizarComSistemaExternoNaoInterageComDependencias() {
        veiculoService.sincronizarComSistemaExterno();

        verifyNoInteractions(veiculoRepository, notificacaoClient);
    }
}
