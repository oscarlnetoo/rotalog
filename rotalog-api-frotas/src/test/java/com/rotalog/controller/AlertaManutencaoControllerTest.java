package com.rotalog.controller;

import com.rotalog.domain.AlertaManutencao;
import com.rotalog.dto.AlertaManutencaoResponse;
import com.rotalog.dto.VerificacaoAlertasResponse;
import com.rotalog.service.AlertaManutencaoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AlertaManutencaoController.class)
class AlertaManutencaoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AlertaManutencaoService service;

    private AlertaManutencao alerta() {
        AlertaManutencao a = new AlertaManutencao();
        a.setId(1L);
        a.setVeiculoId(12L);
        a.setPlaca("ABC1D23");
        a.setModelo("Fiat Fiorino");
        a.setMotivo("AMBOS");
        a.setQuilometragemAtual(52000L);
        a.setLimiteQuilometragem(10000L);
        a.setDataUltimaManutencao(LocalDate.of(2025, 3, 1));
        a.setMesesDesdeUltimaManutencao(8);
        a.setMesesLimite(6);
        a.setStatus("ENVIADO");
        a.setTentativas(0);
        a.setDataCriacao(LocalDateTime.of(2025, 11, 1, 10, 0, 0));
        a.setDataAtualizacao(LocalDateTime.of(2025, 11, 1, 10, 0, 1));
        return a;
    }

    @Test
    void listarSemFiltroRetornaArrayComCampos() throws Exception {
        when(service.listar(null)).thenReturn(List.of(alerta()));

        mockMvc.perform(get("/alertas-manutencao"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].veiculoId").value(12))
                .andExpect(jsonPath("$[0].placa").value("ABC1D23"))
                .andExpect(jsonPath("$[0].dataUltimaManutencao").value("2025-03-01"))
                .andExpect(jsonPath("$[0].mesesDesdeUltimaManutencao").value(8))
                .andExpect(jsonPath("$[0].status").value("ENVIADO"))
                .andExpect(jsonPath("$[0].dataCriacao").value("2025-11-01T10:00:00"))
                .andExpect(jsonPath("$[0].dataEnvio").isEmpty())
                .andExpect(jsonPath("$[0].notificacaoId").isEmpty());
    }

    @Test
    void listarPassaStatusParaOService() throws Exception {
        when(service.listar("falha")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/alertas-manutencao").param("status", "falha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        verify(service).listar("falha");
    }

    @Test
    void statusInvalidoRetorna400() throws Exception {
        when(service.listar("XPTO")).thenThrow(new IllegalArgumentException("Status inválido: XPTO"));

        mockMvc.perform(get("/alertas-manutencao").param("status", "XPTO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Status inválido: XPTO"));
    }

    @Test
    void verificarRetornaResumo() throws Exception {
        VerificacaoAlertasResponse r = new VerificacaoAlertasResponse();
        r.setVeiculosVerificados(7);
        r.setCriados(2);
        r.setEnviados(1);
        r.setPendentes(1);
        r.setFalhas(0);
        r.setReenviados(0);
        r.setIgnoradosPorDuplicidade(3);
        r.setAlertas(List.of(AlertaManutencaoResponse.from(alerta())));
        when(service.verificar()).thenReturn(r);

        mockMvc.perform(post("/alertas-manutencao/verificar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.veiculosVerificados").value(7))
                .andExpect(jsonPath("$.criados").value(2))
                .andExpect(jsonPath("$.enviados").value(1))
                .andExpect(jsonPath("$.pendentes").value(1))
                .andExpect(jsonPath("$.falhas").value(0))
                .andExpect(jsonPath("$.reenviados").value(0))
                .andExpect(jsonPath("$.ignoradosPorDuplicidade").value(3))
                .andExpect(jsonPath("$.alertas[0].placa").value("ABC1D23"));
    }

    @Test
    void verificarErroInternoRetorna500() throws Exception {
        when(service.verificar()).thenThrow(new RuntimeException("db fora"));

        mockMvc.perform(post("/alertas-manutencao/verificar"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.erro").value("db fora"));
    }
}
