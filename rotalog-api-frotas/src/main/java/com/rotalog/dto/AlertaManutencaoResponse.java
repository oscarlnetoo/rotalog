package com.rotalog.dto;

import com.rotalog.domain.AlertaManutencao;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class AlertaManutencaoResponse {

    private Long id;
    private Long veiculoId;
    private String placa;
    private String modelo;
    private String motivo;
    private Long quilometragemAtual;
    private Long limiteQuilometragem;
    private LocalDate dataUltimaManutencao;
    private Integer mesesDesdeUltimaManutencao;
    private Integer mesesLimite;
    private String status;
    private Long notificacaoId;
    private String destinatario;
    private String erroMensagem;
    private Integer tentativas;
    private LocalDateTime dataCriacao;
    private LocalDateTime dataEnvio;
    private LocalDateTime dataAtualizacao;

    public static AlertaManutencaoResponse from(AlertaManutencao a) {
        AlertaManutencaoResponse r = new AlertaManutencaoResponse();
        r.id = a.getId();
        r.veiculoId = a.getVeiculoId();
        r.placa = a.getPlaca();
        r.modelo = a.getModelo();
        r.motivo = a.getMotivo();
        r.quilometragemAtual = a.getQuilometragemAtual();
        r.limiteQuilometragem = a.getLimiteQuilometragem();
        r.dataUltimaManutencao = a.getDataUltimaManutencao();
        r.mesesDesdeUltimaManutencao = a.getMesesDesdeUltimaManutencao();
        r.mesesLimite = a.getMesesLimite();
        r.status = a.getStatus();
        r.notificacaoId = a.getNotificacaoId();
        r.destinatario = a.getDestinatario();
        r.erroMensagem = a.getErroMensagem();
        r.tentativas = a.getTentativas();
        r.dataCriacao = a.getDataCriacao();
        r.dataEnvio = a.getDataEnvio();
        r.dataAtualizacao = a.getDataAtualizacao();
        return r;
    }
}
