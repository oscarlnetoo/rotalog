package com.rotalog.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Resultado de POST /alertas-manutencao/verificar.
 * enviados/pendentes/falhas contam os alertas processados na execução
 * (novos + reenviados) pelo status final.
 */
@Getter
@Setter
@NoArgsConstructor
public class VerificacaoAlertasResponse {

    private int veiculosVerificados;
    private int criados;
    private int enviados;
    private int pendentes;
    private int falhas;
    private int reenviados;
    private int ignoradosPorDuplicidade;
    private List<AlertaManutencaoResponse> alertas = new ArrayList<>();
}
