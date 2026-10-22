package com.rotalog.service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * NotificacaoClient - HTTP client for api-notificacoes
 * 
 * FIXME: URL hardcoded
 * FIXME: Sem circuit breaker
 * FIXME: Sem retry logic
 * FIXME: Sem timeout configurável
 * FIXME: RestTemplate instanciado manualmente em vez de ser Bean
 */
@Slf4j
@Component
public class NotificacaoClient {

    private final String notificacaoApiUrl;

    private final RestTemplate restTemplate;

    @Autowired
    public NotificacaoClient(
            RestTemplateBuilder builder,
            @Value("${rotalog.api.notificacoes.url:http://localhost:5000}") String notificacaoApiUrl,
            @Value("${rotalog.api.notificacoes.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${rotalog.api.notificacoes.read-timeout-ms:5000}") long readTimeoutMs) {
        this(builder
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .build(), notificacaoApiUrl);
    }

    NotificacaoClient(RestTemplate restTemplate, String notificacaoApiUrl) {
        this.restTemplate = restTemplate;
        this.notificacaoApiUrl = notificacaoApiUrl;
    }

    /**
     * Resultado de enviarAlertaManutencao.
     * inalcancavel=true: erro de conexão/timeout (api-notificacoes não respondeu) - aciona o circuit break.
     * inalcancavel=false: houve resposta; status é ENVIADO, FALHA ou PENDENTE (5xx / status desconhecido).
     */
    @Getter
    @AllArgsConstructor
    public static class ResultadoAlerta {
        private final String status;
        private final boolean inalcancavel;
        private final Long notificacaoId;
        private final String destinatario;
        private final String erroMensagem;
    }

    /**
     * Envia alerta de manutenção preventiva (C1). Nunca lança exceção.
     */
    public ResultadoAlerta enviarAlertaManutencao(Long veiculoId, String placa, String modelo,
                                                  Long quilometragemAtual, Long limiteQuilometragem,
                                                  LocalDate dataUltimaManutencao, Integer mesesLimite,
                                                  String motivo) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("veiculoId", veiculoId);
        body.put("placa", placa);
        body.put("modelo", modelo);
        body.put("quilometragemAtual", quilometragemAtual);
        body.put("limiteQuilometragem", limiteQuilometragem);
        body.put("dataUltimaManutencao", dataUltimaManutencao != null ? dataUltimaManutencao.toString() : null);
        body.put("mesesLimite", mesesLimite);
        body.put("motivo", motivo);
        // destinatario omitido: api-notificacoes usa o gestor configurado

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String url = notificacaoApiUrl + "/api/notificacoes/alertas-manutencao";

        try {
            log.info("Enviando alerta de manutenção para {}: veiculoId={}, motivo={}", url, veiculoId, motivo);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            Map<String, Object> corpo = response.getBody();
            Object status = corpo != null ? corpo.get("status") : null;
            Long notificacaoId = corpo != null && corpo.get("notificacaoId") instanceof Number
                    ? ((Number) corpo.get("notificacaoId")).longValue() : null;
            String destinatario = corpo != null && corpo.get("destinatario") != null
                    ? corpo.get("destinatario").toString() : null;
            String erro = corpo != null && corpo.get("erroMensagem") != null
                    ? corpo.get("erroMensagem").toString() : null;

            if ("ENVIADO".equals(status)) {
                return new ResultadoAlerta("ENVIADO", false, notificacaoId, destinatario, null);
            }
            if ("FALHA".equals(status)) {
                return new ResultadoAlerta("FALHA", false, notificacaoId, destinatario, erro);
            }
            return new ResultadoAlerta("PENDENTE", false, notificacaoId, destinatario,
                    "Status desconhecido na resposta de api-notificacoes: " + status);
        } catch (HttpClientErrorException e) {
            log.warn("api-notificacoes rejeitou o alerta (4xx): {}", e.getStatusCode());
            return new ResultadoAlerta("FALHA", false, null, null,
                    "api-notificacoes respondeu " + e.getRawStatusCode() + ": " + e.getResponseBodyAsString());
        } catch (HttpServerErrorException e) {
            log.warn("api-notificacoes com erro (5xx): {}", e.getStatusCode());
            return new ResultadoAlerta("PENDENTE", false, null, null,
                    "api-notificacoes respondeu " + e.getRawStatusCode());
        } catch (ResourceAccessException e) {
            log.warn("api-notificacoes inacessível: {}", e.getMessage());
            return new ResultadoAlerta("PENDENTE", true, null, null,
                    "api-notificacoes inacessível: " + e.getMessage());
        } catch (RestClientException e) {
            log.warn("Resposta inválida de api-notificacoes: {}", e.getMessage());
            return new ResultadoAlerta("PENDENTE", false, null, null,
                    "Resposta inválida de api-notificacoes: " + e.getMessage());
        }
    }

    /**
     * Envia notificação para api-notificacoes
     * 
     * FIXME: Sem retry
     * FIXME: Sem fallback
     * FIXME: Sem validação de resposta
     * FIXME: Engolindo exceções no chamador
     */
    public void enviarNotificacao(String tipo, String destinatario, String mensagem) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> body = new HashMap<>();
            body.put("tipo", tipo);
            body.put("destinatario", destinatario);
            body.put("mensagem", mensagem);
            body.put("canal", "email"); // FIXME: canal hardcoded

            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

            String url = notificacaoApiUrl + "/api/notificacoes";
            log.info("Enviando notificação para {}: tipo={}, destinatario={}", url, tipo, destinatario);

            restTemplate.postForEntity(url, request, String.class);

            log.info("Notificação enviada com sucesso: tipo={}", tipo);
        } catch (Exception e) {
            // FIXME: Engolindo exceção - apenas logando
            log.error("Erro ao enviar notificação: tipo={}, erro={}", tipo, e.getMessage());
            // FIXME: Sem retry, sem fallback, sem dead letter queue
            throw e; // re-throw para o chamador decidir o que fazer
        }
    }

    /**
     * Envia notificação SMS
     * 
     * FIXME: Código duplicado com enviarNotificacao
     * FIXME: Deveria ser o mesmo método com canal diferente
     */
    public void enviarSms(String destinatario, String mensagem) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> body = new HashMap<>();
            body.put("tipo", "SMS");
            body.put("destinatario", destinatario);
            body.put("mensagem", mensagem);
            body.put("canal", "sms");

            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

            String url = notificacaoApiUrl + "/api/notificacoes";
            restTemplate.postForEntity(url, request, String.class);
        } catch (Exception e) {
            log.error("Erro ao enviar SMS: {}", e.getMessage());
        }
    }
}
