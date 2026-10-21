package com.rotalog.service;

import com.rotalog.domain.Veiculo;
import com.rotalog.repository.VeiculoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VeiculoServiceTest {

    @Mock
    private VeiculoRepository veiculoRepository;

    @Mock
    private NotificacaoClient notificacaoClient;

    @InjectMocks
    private VeiculoService veiculoService;

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
}
