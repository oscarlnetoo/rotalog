package com.rotalog.service;

import com.rotalog.domain.Veiculo;
import com.rotalog.repository.VeiculoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VeiculoServiceTest {

    @Mock
    private VeiculoRepository veiculoRepository;

    @Mock
    private NotificacaoClient notificacaoClient;

    private VeiculoService veiculoService;

    @BeforeEach
    void setUp() {
        veiculoService = new VeiculoService(veiculoRepository, new VeiculoValidator(),
                new VeiculoNotificador(notificacaoClient));
    }

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

        RuntimeException e = assertThrows(RuntimeException.class, () -> veiculoService.buscarPorId(99L));

        assertEquals("Veículo não encontrado: 99", e.getMessage());
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

        RuntimeException e = assertThrows(RuntimeException.class, () -> veiculoService.buscarPorPlaca("ZZZ0000"));

        assertEquals("Veículo não encontrado com placa: ZZZ0000", e.getMessage());
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
        RuntimeException e = assertThrows(RuntimeException.class, () -> veiculoService.obterVeiculosPorStatus("QUEBRADO"));

        assertEquals("Status inválido: QUEBRADO", e.getMessage());
        verify(veiculoRepository, never()).findByStatus(anyString());
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

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> veiculoService.registrarVeiculo("ABC1234", "Volvo FH", 2020));

        assertEquals("Veículo com placa ABC1234 já existe", e.getMessage());
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
}
