package com.rotalog.service;

import com.rotalog.domain.Veiculo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class VeiculoNotificador {

    static final String DESTINATARIO_GESTOR = "gestor@rotalog.com";

    private final NotificacaoClient notificacaoClient;

    public void notificarNovoVeiculo(Veiculo veiculo) {
        enviar("NOVO_VEICULO",
                "Novo veículo cadastrado: " + veiculo.getPlaca() + " - " + veiculo.getModelo());
    }

    public void notificarAlertaManutencao(Veiculo veiculo, Long quilometragem) {
        enviar("ALERTA_MANUTENCAO",
                "Veículo " + veiculo.getPlaca() + " atingiu " + quilometragem + " km. Agendar manutenção preventiva.");
    }

    public void notificarDesativacao(Veiculo veiculo) {
        enviar("VEICULO_DESATIVADO", "Veículo " + veiculo.getPlaca() + " foi desativado");
    }

    // Best-effort: o veículo já foi salvo quando a notificação é enviada
    private void enviar(String tipo, String mensagem) {
        try {
            notificacaoClient.enviarNotificacao(tipo, DESTINATARIO_GESTOR, mensagem);
        } catch (Exception e) {
            log.error("Falha ao enviar notificação {}: {}", tipo, e.getMessage());
        }
    }
}
