package com.rotalog.controller;

import com.rotalog.dto.AlertaManutencaoResponse;
import com.rotalog.service.AlertaManutencaoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AlertaManutencaoController - alertas de manutenção preventiva (consumido pelo painel-admin)
 */
@Slf4j
@RestController
@RequestMapping("/alertas-manutencao")
public class AlertaManutencaoController {

    @Autowired
    private AlertaManutencaoService alertaService;

    @GetMapping
    public ResponseEntity<?> listar(@RequestParam(required = false) String status) {
        try {
            return ResponseEntity.ok(alertaService.listar(status).stream()
                    .map(AlertaManutencaoResponse::from)
                    .collect(Collectors.toList()));
        } catch (IllegalArgumentException e) {
            return erro(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (RuntimeException e) {
            log.error("Erro ao listar alertas de manutenção", e);
            return erro(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @PostMapping("/verificar")
    public ResponseEntity<?> verificar() {
        try {
            return ResponseEntity.ok(alertaService.verificar());
        } catch (RuntimeException e) {
            log.error("Erro ao verificar alertas de manutenção", e);
            return erro(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    private ResponseEntity<?> erro(HttpStatus status, String mensagem) {
        Map<String, String> error = new HashMap<>();
        error.put("erro", mensagem);
        return ResponseEntity.status(status).body(error);
    }
}
