package com.rotalog.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * AlertaManutencao entity - alerta de manutenção preventiva
 * Tabela frotas.alertas_manutencao (sem FK, como as demais tabelas)
 */
@Entity
@Table(name = "alertas_manutencao")
@Getter
@Setter
@NoArgsConstructor
public class AlertaManutencao {

    public static final String STATUS_PENDENTE = "PENDENTE";
    public static final String STATUS_ENVIADO = "ENVIADO";
    public static final String STATUS_FALHA = "FALHA";

    public static final String MOTIVO_QUILOMETRAGEM = "QUILOMETRAGEM";
    public static final String MOTIVO_TEMPO = "TEMPO";
    public static final String MOTIVO_AMBOS = "AMBOS";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "veiculo_id", nullable = false)
    private Long veiculoId;

    @Column(name = "placa", nullable = false, length = 7)
    private String placa;

    @Column(name = "modelo", length = 100)
    private String modelo;

    @Column(name = "motivo", nullable = false, length = 20)
    private String motivo;

    @Column(name = "quilometragem_atual")
    private Long quilometragemAtual;

    @Column(name = "limite_quilometragem")
    private Long limiteQuilometragem;

    @Column(name = "data_ultima_manutencao")
    private LocalDate dataUltimaManutencao;

    @Column(name = "meses_desde_ultima_manutencao")
    private Integer mesesDesdeUltimaManutencao;

    @Column(name = "meses_limite")
    private Integer mesesLimite;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "notificacao_id")
    private Long notificacaoId;

    @Column(name = "destinatario", length = 255)
    private String destinatario;

    @Column(name = "erro_mensagem")
    private String erroMensagem;

    @Column(name = "tentativas", nullable = false)
    private Integer tentativas;

    @Column(name = "data_criacao", nullable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "data_envio")
    private LocalDateTime dataEnvio;

    @Column(name = "data_atualizacao", nullable = false)
    private LocalDateTime dataAtualizacao;
}
