package com.rotalog.service;

import com.rotalog.domain.Veiculo;
import com.rotalog.repository.VeiculoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class VeiculoQuilometragemService {

    // Limite absoluto, não um intervalo: o alerta dispara em toda atualização a partir de 50.000 km
    private static final long LIMITE_QUILOMETRAGEM_MANUTENCAO = 50000L;

    private final VeiculoService veiculoService;
    private final VeiculoRepository veiculoRepository;
    private final VeiculoValidator validator;
    private final VeiculoNotificador notificador;

    public Veiculo atualizarQuilometragem(Long veiculoId, Long novaQuilometragem) {
        Veiculo veiculo = veiculoService.buscarPorId(veiculoId);

        validator.validarQuilometragem(novaQuilometragem);
        validator.avisarSeQuilometragemReduzida(veiculoId, veiculo.getQuilometragem(), novaQuilometragem);

        veiculo.setQuilometragem(novaQuilometragem);
        veiculo.setDataAtualizacao(LocalDateTime.now());

        Veiculo atualizado = veiculoRepository.save(veiculo);

        if (precisaDeManutencao(atualizado)) {
            notificador.notificarAlertaManutencao(atualizado, novaQuilometragem);
        }

        return atualizado;
    }

    public boolean precisaDeManutencao(Veiculo veiculo) {
        return veiculo.getQuilometragem() != null
                && veiculo.getQuilometragem() >= LIMITE_QUILOMETRAGEM_MANUTENCAO;
    }
}
