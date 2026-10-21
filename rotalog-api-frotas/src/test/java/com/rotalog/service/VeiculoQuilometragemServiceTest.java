package com.rotalog.service;

import com.rotalog.domain.Veiculo;
import com.rotalog.repository.VeiculoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
class VeiculoQuilometragemServiceTest {

    @Mock
    private VeiculoRepository veiculoRepository;

    @Mock
    private NotificacaoClient notificacaoClient;

    private VeiculoQuilometragemService quilometragemService;

    @BeforeEach
    void setUp() {
        VeiculoValidator validator = new VeiculoValidator();
        VeiculoNotificador notificador = new VeiculoNotificador(notificacaoClient);
        VeiculoService veiculoService = new VeiculoService(veiculoRepository, validator, notificador);
        quilometragemService = new VeiculoQuilometragemService(veiculoService, veiculoRepository, validator, notificador);
    }

    private Veiculo veiculo(Long km) {
        Veiculo veiculo = new Veiculo();
        veiculo.setId(1L);
        veiculo.setPlaca("ABC1234");
        veiculo.setQuilometragem(km);
        return veiculo;
    }

    @Test
    void atualizarQuilometragemLancaExcecaoQuandoNegativa() {
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo(1000L)));

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> quilometragemService.atualizarQuilometragem(1L, -1L));

        assertEquals("Quilometragem não pode ser negativa", e.getMessage());
        verify(veiculoRepository, never()).save(any());
    }

    @Test
    void atualizarQuilometragemLancaExcecaoQuandoVeiculoNaoExiste() {
        when(veiculoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> quilometragemService.atualizarQuilometragem(99L, 10L));

        verify(veiculoRepository, never()).save(any());
    }

    @Test
    void atualizarQuilometragemSalvaSemNotificarQuandoAbaixoDoLimite() {
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo(1000L)));
        when(veiculoRepository.save(any(Veiculo.class))).thenAnswer(inv -> inv.getArgument(0));

        Veiculo resultado = quilometragemService.atualizarQuilometragem(1L, 49999L);

        assertEquals(49999L, resultado.getQuilometragem());
        verifyNoInteractions(notificacaoClient);
    }

    @Test
    void atualizarQuilometragemEnviaAlertaQuandoAtingeLimite() {
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo(1000L)));
        when(veiculoRepository.save(any(Veiculo.class))).thenAnswer(inv -> inv.getArgument(0));

        quilometragemService.atualizarQuilometragem(1L, 50000L);

        verify(veiculoRepository).findById(1L);
        verify(notificacaoClient).enviarNotificacao(eq("ALERTA_MANUTENCAO"), eq("gestor@rotalog.com"), contains("ABC1234"));
    }

    @Test
    void atualizarQuilometragemSalvaQuandoMenorQueAAtual() {
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo(1000L)));
        when(veiculoRepository.save(any(Veiculo.class))).thenAnswer(inv -> inv.getArgument(0));

        Veiculo resultado = quilometragemService.atualizarQuilometragem(1L, 500L);

        assertEquals(500L, resultado.getQuilometragem());
        verify(veiculoRepository).save(resultado);
    }

    @Test
    void atualizarQuilometragemNaoFalhaQuandoNotificacaoFalha() {
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo(1000L)));
        when(veiculoRepository.save(any(Veiculo.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("falha")).when(notificacaoClient)
                .enviarNotificacao(anyString(), anyString(), anyString());

        assertEquals(60000L, quilometragemService.atualizarQuilometragem(1L, 60000L).getQuilometragem());
    }

    @Test
    void precisaDeManutencaoRetornaFalseAbaixoDoLimite() {
        assertFalse(quilometragemService.precisaDeManutencao(veiculo(49999L)));
    }

    @Test
    void precisaDeManutencaoRetornaTrueNoLimite() {
        assertTrue(quilometragemService.precisaDeManutencao(veiculo(50000L)));
    }

    @Test
    void precisaDeManutencaoRetornaFalseQuandoQuilometragemNula() {
        assertFalse(quilometragemService.precisaDeManutencao(veiculo(null)));
    }
}
