package com.rotalog.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class NotificacaoClientTest {

    private static final String URL = "http://localhost:5000/api/notificacoes/alertas-manutencao";

    private MockRestServiceServer server;
    private NotificacaoClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        client = new NotificacaoClient(restTemplate, "http://localhost:5000");
    }

    private NotificacaoClient.ResultadoAlerta enviar() {
        return client.enviarAlertaManutencao(12L, "ABC1D23", "Fiat Fiorino", 52000L, 10000L,
                LocalDate.of(2025, 3, 1), 6, "QUILOMETRAGEM");
    }

    @Test
    void enviadoEnviaPayloadSemDestinatario() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.veiculoId").value(12))
                .andExpect(jsonPath("$.placa").value("ABC1D23"))
                .andExpect(jsonPath("$.dataUltimaManutencao").value("2025-03-01"))
                .andExpect(jsonPath("$.motivo").value("QUILOMETRAGEM"))
                .andExpect(jsonPath("$.destinatario").doesNotExist())
                .andRespond(withSuccess("{\"notificacaoId\":5,\"status\":\"ENVIADO\",\"destinatario\":\"gestor@rotalog.com\"}",
                        MediaType.APPLICATION_JSON));

        NotificacaoClient.ResultadoAlerta r = enviar();

        assertEquals("ENVIADO", r.getStatus());
        assertFalse(r.isInalcancavel());
        assertEquals(5L, r.getNotificacaoId());
        assertEquals("gestor@rotalog.com", r.getDestinatario());
        server.verify();
    }

    @Test
    void falha200() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"notificacaoId\":6,\"status\":\"FALHA\",\"destinatario\":\"g@x.com\",\"erroMensagem\":\"smtp\"}",
                MediaType.APPLICATION_JSON));

        NotificacaoClient.ResultadoAlerta r = enviar();

        assertEquals("FALHA", r.getStatus());
        assertFalse(r.isInalcancavel());
        assertEquals("smtp", r.getErroMensagem());
        assertEquals(6L, r.getNotificacaoId());
    }

    @Test
    void statusDesconhecidoVirapendente() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"status\":\"XYZ\"}", MediaType.APPLICATION_JSON));

        NotificacaoClient.ResultadoAlerta r = enviar();

        assertEquals("PENDENTE", r.getStatus());
        assertFalse(r.isInalcancavel());
    }

    @Test
    void erro4xxViraFalha() {
        server.expect(requestTo(URL)).andRespond(
                withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"motivo inválido\"}"));

        NotificacaoClient.ResultadoAlerta r = enviar();

        assertEquals("FALHA", r.getStatus());
        assertFalse(r.isInalcancavel());
        assertTrue(r.getErroMensagem().contains("400"));
    }

    @Test
    void erro5xxViraPendenteMasResponderam() {
        server.expect(requestTo(URL)).andRespond(withServerError());

        NotificacaoClient.ResultadoAlerta r = enviar();

        assertEquals("PENDENTE", r.getStatus());
        assertFalse(r.isInalcancavel());
    }

    @Test
    void ioExceptionViraPendenteInalcancavel() {
        server.expect(requestTo(URL)).andRespond(withException(new IOException("Connection refused")));

        NotificacaoClient.ResultadoAlerta r = enviar();

        assertEquals("PENDENTE", r.getStatus());
        assertTrue(r.isInalcancavel());
    }
}
