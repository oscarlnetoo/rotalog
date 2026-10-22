package com.rotalog.service;

import com.rotalog.domain.AlertaManutencao;
import com.rotalog.domain.Manutencao;
import com.rotalog.domain.Veiculo;
import com.rotalog.dto.AlertaManutencaoResponse;
import com.rotalog.dto.VerificacaoAlertasResponse;
import com.rotalog.repository.AlertaManutencaoRepository;
import com.rotalog.repository.ManutencaoRepository;
import com.rotalog.repository.VeiculoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * AlertaManutencaoService - alertas de manutenção preventiva
 * (quilometragem desde a última manutenção CONCLUIDA e/ou tempo decorrido)
 */
@Slf4j
@Service
public class AlertaManutencaoService {

    private static final LocalDateTime EPOCA = LocalDateTime.of(1970, 1, 1, 0, 0);

    @Autowired
    private AlertaManutencaoRepository alertaRepository;

    @Autowired
    private VeiculoRepository veiculoRepository;

    @Autowired
    private ManutencaoRepository manutencaoRepository;

    @Autowired
    private NotificacaoClient notificacaoClient;

    @Value("${rotalog.manutencao.alerta.limite-km:10000}")
    private long limiteKm;

    @Value("${rotalog.manutencao.alerta.limite-meses:6}")
    private int limiteMeses;

    @Value("${rotalog.manutencao.alerta.max-tentativas:5}")
    private int maxTentativas;

    public List<AlertaManutencao> listar(String status) {
        if (status == null || status.trim().isEmpty()) {
            return alertaRepository.findAllByOrderByDataCriacaoDesc();
        }
        String normalizado = status.trim().toUpperCase(Locale.ROOT);
        if (!Arrays.asList(AlertaManutencao.STATUS_PENDENTE, AlertaManutencao.STATUS_ENVIADO,
                AlertaManutencao.STATUS_FALHA).contains(normalizado)) {
            throw new IllegalArgumentException("Status inválido: " + status);
        }
        return alertaRepository.findByStatusOrderByDataCriacaoDesc(normalizado);
    }

    public VerificacaoAlertasResponse verificar() {
        VerificacaoAlertasResponse resultado = new VerificacaoAlertasResponse();
        List<AlertaManutencao> processados = new java.util.ArrayList<>();
        boolean[] circuitoAberto = {false};

        // 1. Reenvio dos PENDENTE
        for (AlertaManutencao esgotado : alertaRepository
                .findByStatusAndTentativasGreaterThanEqual(AlertaManutencao.STATUS_PENDENTE, maxTentativas)) {
            marcarEsgotado(esgotado);
            processados.add(esgotado);
        }
        for (AlertaManutencao pendente : alertaRepository
                .findByStatusAndTentativasLessThanOrderByDataCriacaoAsc(AlertaManutencao.STATUS_PENDENTE, maxTentativas)) {
            if (circuitoAberto[0]) {
                break;
            }
            resultado.setReenviados(resultado.getReenviados() + 1);
            enviar(pendente, circuitoAberto);
            if (AlertaManutencao.STATUS_PENDENTE.equals(pendente.getStatus())
                    && pendente.getTentativas() >= maxTentativas) {
                marcarEsgotado(pendente);
            }
            processados.add(pendente);
        }

        // 2. Veículos ATIVO elegíveis
        List<Veiculo> veiculos = veiculoRepository.findByStatus("ATIVO");
        resultado.setVeiculosVerificados(veiculos.size());
        LocalDate hoje = LocalDate.now();

        for (Veiculo veiculo : veiculos) {
            Manutencao ultima = manutencaoRepository.findUltimaManutencaoConcluida(veiculo.getId());
            LocalDateTime baseData;
            long baseKm;
            LocalDate dataUltima = null;
            if (ultima != null) {
                baseData = ultima.getDataManutencao() != null ? ultima.getDataManutencao() : ultima.getDataAtualizacao();
                baseKm = ultima.getQuilometragemManutencao() != null ? ultima.getQuilometragemManutencao() : 0L;
                dataUltima = baseData != null ? baseData.toLocalDate() : null;
            } else {
                baseData = veiculo.getDataCadastro();
                baseKm = 0L;
            }

            long km = veiculo.getQuilometragem() != null ? veiculo.getQuilometragem() : 0L;
            boolean porKm = km - baseKm > limiteKm;
            int meses = baseData != null ? (int) ChronoUnit.MONTHS.between(baseData.toLocalDate(), hoje) : 0;
            boolean porTempo = baseData != null && meses > limiteMeses;

            if (!porKm && !porTempo) {
                continue;
            }

            LocalDateTime depoisDe = baseData != null ? baseData : EPOCA;
            if (alertaRepository.existsByVeiculoIdAndStatusInAndDataCriacaoAfter(veiculo.getId(),
                    Arrays.asList(AlertaManutencao.STATUS_PENDENTE, AlertaManutencao.STATUS_ENVIADO), depoisDe)) {
                resultado.setIgnoradosPorDuplicidade(resultado.getIgnoradosPorDuplicidade() + 1);
                continue;
            }

            AlertaManutencao alerta = new AlertaManutencao();
            alerta.setVeiculoId(veiculo.getId());
            alerta.setPlaca(veiculo.getPlaca());
            alerta.setModelo(veiculo.getModelo());
            alerta.setMotivo(porKm && porTempo ? AlertaManutencao.MOTIVO_AMBOS
                    : porKm ? AlertaManutencao.MOTIVO_QUILOMETRAGEM : AlertaManutencao.MOTIVO_TEMPO);
            alerta.setQuilometragemAtual(km);
            alerta.setLimiteQuilometragem(limiteKm);
            alerta.setDataUltimaManutencao(dataUltima);
            alerta.setMesesDesdeUltimaManutencao(baseData != null ? meses : null);
            alerta.setMesesLimite(limiteMeses);
            alerta.setStatus(AlertaManutencao.STATUS_PENDENTE);
            alerta.setTentativas(0);
            alerta.setDataCriacao(LocalDateTime.now());
            alerta.setDataAtualizacao(LocalDateTime.now());
            alerta = alertaRepository.save(alerta); // persiste PENDENTE antes da chamada HTTP
            resultado.setCriados(resultado.getCriados() + 1);

            if (circuitoAberto[0]) {
                alerta.setErroMensagem("api-notificacoes indisponível nesta verificação");
                alerta.setDataAtualizacao(LocalDateTime.now());
                alerta = alertaRepository.save(alerta);
            } else {
                enviar(alerta, circuitoAberto);
            }
            processados.add(alerta);
        }

        for (AlertaManutencao a : processados) {
            switch (a.getStatus()) {
                case AlertaManutencao.STATUS_ENVIADO:
                    resultado.setEnviados(resultado.getEnviados() + 1);
                    break;
                case AlertaManutencao.STATUS_FALHA:
                    resultado.setFalhas(resultado.getFalhas() + 1);
                    break;
                default:
                    resultado.setPendentes(resultado.getPendentes() + 1);
            }
        }
        resultado.setAlertas(processados.stream().map(AlertaManutencaoResponse::from).collect(Collectors.toList()));
        log.info("Verificação de alertas: {} veículos, {} criados, {} reenviados",
                resultado.getVeiculosVerificados(), resultado.getCriados(), resultado.getReenviados());
        return resultado;
    }

    private void enviar(AlertaManutencao alerta, boolean[] circuitoAberto) {
        NotificacaoClient.ResultadoAlerta r = notificacaoClient.enviarAlertaManutencao(
                alerta.getVeiculoId(), alerta.getPlaca(), alerta.getModelo(),
                alerta.getQuilometragemAtual(), alerta.getLimiteQuilometragem(),
                alerta.getDataUltimaManutencao(), alerta.getMesesLimite(), alerta.getMotivo());
        if (r.isInalcancavel()) {
            circuitoAberto[0] = true;
        }
        alerta.setStatus(r.getStatus());
        if (AlertaManutencao.STATUS_PENDENTE.equals(r.getStatus())) {
            alerta.setTentativas(alerta.getTentativas() + 1);
        } else {
            alerta.setNotificacaoId(r.getNotificacaoId());
            alerta.setDestinatario(r.getDestinatario());
        }
        if (AlertaManutencao.STATUS_ENVIADO.equals(r.getStatus())) {
            alerta.setDataEnvio(LocalDateTime.now());
        }
        alerta.setErroMensagem(r.getErroMensagem());
        alerta.setDataAtualizacao(LocalDateTime.now());
        alertaRepository.save(alerta);
    }

    private void marcarEsgotado(AlertaManutencao alerta) {
        alerta.setStatus(AlertaManutencao.STATUS_FALHA);
        alerta.setErroMensagem("Tentativas esgotadas (" + alerta.getTentativas() + "/" + maxTentativas + ")"
                + (alerta.getErroMensagem() != null ? ": " + alerta.getErroMensagem() : ""));
        alerta.setDataAtualizacao(LocalDateTime.now());
        alertaRepository.save(alerta);
    }
}
