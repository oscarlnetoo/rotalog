package com.rotalog.service;

import com.rotalog.domain.Veiculo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class VeiculoNotificadorTest {

    @Mock
    private NotificacaoClient notificacaoClient;

    @InjectMocks
    private VeiculoNotificador notificador;

    private Veiculo veiculo() {
        Veiculo veiculo = new Veiculo();
        veiculo.setPlaca("ABC1234");
        veiculo.setModelo("Volvo FH");
        return veiculo;
    }

    @Test
    void notificarNovoVeiculoEnviaTipoDestinatarioEMensagem() {
        notificador.notificarNovoVeiculo(veiculo());

        verify(notificacaoClient).enviarNotificacao("NOVO_VEICULO", "gestor@rotalog.com",
                "Novo veículo cadastrado: ABC1234 - Volvo FH");
    }

    @Test
    void notificarAlertaManutencaoEnviaTipoDestinatarioEMensagem() {
        notificador.notificarAlertaManutencao(veiculo(), 50000L);

        verify(notificacaoClient).enviarNotificacao("ALERTA_MANUTENCAO", "gestor@rotalog.com",
                "Veículo ABC1234 atingiu 50000 km. Agendar manutenção preventiva.");
    }

    @Test
    void notificarDesativacaoEnviaTipoDestinatarioEMensagem() {
        notificador.notificarDesativacao(veiculo());

        verify(notificacaoClient).enviarNotificacao("VEICULO_DESATIVADO", "gestor@rotalog.com",
                "Veículo ABC1234 foi desativado");
    }

    @Test
    void falhaNoEnvioNaoPropagaExcecao() {
        doThrow(new RuntimeException("falha")).when(notificacaoClient)
                .enviarNotificacao(anyString(), anyString(), anyString());

        assertDoesNotThrow(() -> notificador.notificarNovoVeiculo(veiculo()));
        assertDoesNotThrow(() -> notificador.notificarAlertaManutencao(veiculo(), 1L));
        assertDoesNotThrow(() -> notificador.notificarDesativacao(veiculo()));
    }
}
