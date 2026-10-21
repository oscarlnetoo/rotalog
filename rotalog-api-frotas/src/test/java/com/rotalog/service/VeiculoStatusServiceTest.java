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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
class VeiculoStatusServiceTest {

    @Mock
    private VeiculoRepository veiculoRepository;

    @Mock
    private NotificacaoClient notificacaoClient;

    private VeiculoStatusService statusService;

    @BeforeEach
    void setUp() {
        VeiculoNotificador notificador = new VeiculoNotificador(notificacaoClient);
        VeiculoService veiculoService = new VeiculoService(veiculoRepository, new VeiculoValidator(), notificador);
        statusService = new VeiculoStatusService(veiculoService, veiculoRepository, notificador);
    }

    private Veiculo veiculo(String status) {
        Veiculo veiculo = new Veiculo();
        veiculo.setId(1L);
        veiculo.setPlaca("ABC1234");
        veiculo.setStatus(status);
        return veiculo;
    }

    @Test
    void desativarVeiculoMarcaComoInativoSalvaENotifica() {
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo("ATIVO")));
        when(veiculoRepository.save(any(Veiculo.class))).thenAnswer(inv -> inv.getArgument(0));

        Veiculo resultado = statusService.desativarVeiculo(1L);

        assertEquals("INATIVO", resultado.getStatus());
        assertNotNull(resultado.getDataAtualizacao());
        verify(veiculoRepository).save(resultado);
        verify(notificacaoClient).enviarNotificacao(eq("VEICULO_DESATIVADO"), eq("gestor@rotalog.com"), contains("ABC1234"));
    }

    @Test
    void desativarVeiculoNaoFalhaQuandoNotificacaoFalha() {
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo("ATIVO")));
        when(veiculoRepository.save(any(Veiculo.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("falha")).when(notificacaoClient)
                .enviarNotificacao(anyString(), anyString(), anyString());

        assertEquals("INATIVO", statusService.desativarVeiculo(1L).getStatus());
    }

    @Test
    void desativarVeiculoLancaExcecaoQuandoNaoExiste() {
        when(veiculoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> statusService.desativarVeiculo(99L));

        verify(veiculoRepository, never()).save(any());
        verifyNoInteractions(notificacaoClient);
    }

    @Test
    void reativarVeiculoMarcaComoAtivoSemNotificar() {
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo("INATIVO")));
        when(veiculoRepository.save(any(Veiculo.class))).thenAnswer(inv -> inv.getArgument(0));

        Veiculo resultado = statusService.reativarVeiculo(1L);

        assertEquals("ATIVO", resultado.getStatus());
        verify(veiculoRepository).save(resultado);
        verifyNoInteractions(notificacaoClient);
    }

    @Test
    void reativarVeiculoLancaExcecaoQuandoNaoExiste() {
        when(veiculoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> statusService.reativarVeiculo(99L));

        verify(veiculoRepository, never()).save(any());
    }
}
