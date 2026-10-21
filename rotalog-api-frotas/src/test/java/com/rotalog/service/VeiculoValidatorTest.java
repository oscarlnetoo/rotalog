package com.rotalog.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VeiculoValidatorTest {

    private final VeiculoValidator validator = new VeiculoValidator();

    @ParameterizedTest
    @NullAndEmptySource
    void validarPlacaExigePlaca(String placa) {
        RuntimeException e = assertThrows(RuntimeException.class, () -> validator.validarPlaca(placa));

        assertEquals("Placa é obrigatória", e.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ABC123", "ABC12345"})
    void validarPlacaExigeSeteCaracteres(String placa) {
        RuntimeException e = assertThrows(RuntimeException.class, () -> validator.validarPlaca(placa));

        assertEquals("Placa deve ter 7 caracteres", e.getMessage());
    }

    @Test
    void validarPlacaAceitaSeteCaracteres() {
        assertDoesNotThrow(() -> validator.validarPlaca("ABC1234"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void validarModeloExigeModelo(String modelo) {
        RuntimeException e = assertThrows(RuntimeException.class, () -> validator.validarModelo(modelo));

        assertEquals("Modelo é obrigatório", e.getMessage());
    }

    @Test
    void validarModeloAceitaModeloPreenchido() {
        assertDoesNotThrow(() -> validator.validarModelo("Volvo FH"));
    }

    @Test
    void validarAnoFabricacaoRejeitaNulo() {
        RuntimeException e = assertThrows(RuntimeException.class, () -> validator.validarAnoFabricacao(null));

        assertEquals("Ano de fabricação inválido", e.getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = {1899, 2101})
    void validarAnoFabricacaoRejeitaForaDoIntervalo(int ano) {
        RuntimeException e = assertThrows(RuntimeException.class, () -> validator.validarAnoFabricacao(ano));

        assertEquals("Ano de fabricação inválido", e.getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = {1900, 2020, 2100})
    void validarAnoFabricacaoAceitaIntervaloInclusivo(int ano) {
        assertDoesNotThrow(() -> validator.validarAnoFabricacao(ano));
    }

    @Test
    void validarQuilometragemRejeitaNegativa() {
        RuntimeException e = assertThrows(RuntimeException.class, () -> validator.validarQuilometragem(-1L));

        assertEquals("Quilometragem não pode ser negativa", e.getMessage());
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 1L})
    void validarQuilometragemAceitaZeroEPositiva(long km) {
        assertDoesNotThrow(() -> validator.validarQuilometragem(km));
    }

    @Test
    void avisarSeQuilometragemReduzidaNuncaLancaExcecao() {
        assertDoesNotThrow(() -> validator.avisarSeQuilometragemReduzida(1L, 1000L, 500L));
        assertDoesNotThrow(() -> validator.avisarSeQuilometragemReduzida(1L, 1000L, 1500L));
    }
}
