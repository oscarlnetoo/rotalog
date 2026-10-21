package com.rotalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value
public class EstatisticasFrotaResponse {

    long total;
    long ativos;
    long inativos;

    @JsonProperty("em_manutencao")
    long emManutencao;
}
