package com.rotalog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rotalog.dto.EstatisticasFrotaResponse;
import com.rotalog.repository.VeiculoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VeiculoEstatisticasServiceTest {

    @Mock
    private VeiculoRepository veiculoRepository;

    @InjectMocks
    private VeiculoEstatisticasService estatisticasService;

    @Test
    void obterEstatisticasUsaContagensDoRepositorio() {
        when(veiculoRepository.count()).thenReturn(6L);
        when(veiculoRepository.countByStatus("ATIVO")).thenReturn(3L);
        when(veiculoRepository.countByStatus("INATIVO")).thenReturn(2L);
        when(veiculoRepository.countByStatus("MANUTENCAO")).thenReturn(1L);

        EstatisticasFrotaResponse resultado = estatisticasService.obterEstatisticas();

        assertEquals(6L, resultado.getTotal());
        assertEquals(3L, resultado.getAtivos());
        assertEquals(2L, resultado.getInativos());
        assertEquals(1L, resultado.getEmManutencao());
        verify(veiculoRepository).countByStatus("ATIVO");
    }

    @Test
    void respostaSerializaComOsMesmosNomesDeCampoDeAntes() throws Exception {
        String json = new ObjectMapper().writeValueAsString(new EstatisticasFrotaResponse(6, 3, 2, 1));

        assertEquals("{\"total\":6,\"ativos\":3,\"inativos\":2,\"em_manutencao\":1}", json);
    }
}
