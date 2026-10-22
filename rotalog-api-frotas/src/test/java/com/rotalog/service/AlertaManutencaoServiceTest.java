package com.rotalog.service;

import com.rotalog.domain.AlertaManutencao;
import com.rotalog.domain.Manutencao;
import com.rotalog.domain.Veiculo;
import com.rotalog.dto.VerificacaoAlertasResponse;
import com.rotalog.repository.AlertaManutencaoRepository;
import com.rotalog.repository.ManutencaoRepository;
import com.rotalog.repository.VeiculoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertaManutencaoServiceTest {

    @Mock
    private AlertaManutencaoRepository alertaRepository;
    @Mock
    private VeiculoRepository veiculoRepository;
    @Mock
    private ManutencaoRepository manutencaoRepository;
    @Mock
    private NotificacaoClient notificacaoClient;

    @InjectMocks
    private AlertaManutencaoService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "limiteKm", 10000L);
        ReflectionTestUtils.setField(service, "limiteMeses", 6);
        ReflectionTestUtils.setField(service, "maxTentativas", 5);
        lenient().when(alertaRepository.findByStatusAndTentativasGreaterThanEqual(anyString(), anyInt()))
                .thenReturn(Collections.emptyList());
        lenient().when(alertaRepository.findByStatusAndTentativasLessThanOrderByDataCriacaoAsc(anyString(), anyInt()))
                .thenReturn(Collections.emptyList());
        lenient().when(alertaRepository.existsByVeiculoIdAndStatusInAndDataCriacaoAfter(anyLong(), any(), any()))
                .thenReturn(false);
        lenient().when(alertaRepository.save(any(AlertaManutencao.class))).thenAnswer(inv -> {
            AlertaManutencao a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId(100L);
            }
            return a;
        });
    }

    private Veiculo veiculo(long id, String placa, long km, int mesesCadastro) {
        Veiculo v = new Veiculo();
        v.setId(id);
        v.setPlaca(placa);
        v.setModelo("Fiat Fiorino");
        v.setStatus("ATIVO");
        v.setQuilometragem(km);
        v.setDataCadastro(LocalDateTime.now().minusMonths(mesesCadastro).minusDays(1));
        return v;
    }

    private Manutencao concluida(int mesesAtras, Long km) {
        Manutencao m = new Manutencao();
        m.setStatus("CONCLUIDA");
        m.setDataManutencao(LocalDateTime.now().minusMonths(mesesAtras).minusDays(1));
        m.setQuilometragemManutencao(km);
        return m;
    }

    private NotificacaoClient.ResultadoAlerta resp(String status, boolean inalcancavel) {
        return new NotificacaoClient.ResultadoAlerta(status, inalcancavel, "ENVIADO".equals(status) ? 9L : null,
                "gestor@rotalog.com", "FALHA".equals(status) ? "smtp" : null);
    }

    private void stubEnvio(NotificacaoClient.ResultadoAlerta r) {
        when(notificacaoClient.enviarAlertaManutencao(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(r);
    }

    @Test
    void elegivelPorQuilometragem() {
        when(veiculoRepository.findByStatus("ATIVO")).thenReturn(List.of(veiculo(1, "AAA1111", 60000, 1)));
        when(manutencaoRepository.findUltimaManutencaoConcluida(1L)).thenReturn(concluida(1, 45000L));
        stubEnvio(resp("ENVIADO", false));

        VerificacaoAlertasResponse r = service.verificar();

        assertEquals(1, r.getVeiculosVerificados());
        assertEquals(1, r.getCriados());
        assertEquals(1, r.getEnviados());
        assertEquals("QUILOMETRAGEM", r.getAlertas().get(0).getMotivo());
        assertEquals("ENVIADO", r.getAlertas().get(0).getStatus());
        assertEquals(9L, r.getAlertas().get(0).getNotificacaoId());
        assertNotNull(r.getAlertas().get(0).getDataEnvio());
    }

    @Test
    void elegivelPorTempo() {
        when(veiculoRepository.findByStatus("ATIVO")).thenReturn(List.of(veiculo(1, "AAA1111", 46000, 1)));
        when(manutencaoRepository.findUltimaManutencaoConcluida(1L)).thenReturn(concluida(8, 45000L));
        stubEnvio(resp("ENVIADO", false));

        VerificacaoAlertasResponse r = service.verificar();

        assertEquals("TEMPO", r.getAlertas().get(0).getMotivo());
        assertEquals(8, r.getAlertas().get(0).getMesesDesdeUltimaManutencao());
    }

    @Test
    void elegivelPorAmbos() {
        when(veiculoRepository.findByStatus("ATIVO")).thenReturn(List.of(veiculo(1, "AAA1111", 60000, 1)));
        when(manutencaoRepository.findUltimaManutencaoConcluida(1L)).thenReturn(concluida(8, 45000L));
        stubEnvio(resp("ENVIADO", false));

        VerificacaoAlertasResponse r = service.verificar();

        assertEquals(1, r.getAlertas().size());
        assertEquals("AMBOS", r.getAlertas().get(0).getMotivo());
    }

    @Test
    void naoElegivelNaoCriaAlerta() {
        when(veiculoRepository.findByStatus("ATIVO")).thenReturn(List.of(veiculo(1, "AAA1111", 50000, 1)));
        when(manutencaoRepository.findUltimaManutencaoConcluida(1L)).thenReturn(concluida(2, 45000L));

        VerificacaoAlertasResponse r = service.verificar();

        assertEquals(1, r.getVeiculosVerificados());
        assertEquals(0, r.getCriados());
        verifyNoInteractions(notificacaoClient);
        verify(alertaRepository, never()).save(any());
    }

    @Test
    void limiteExatoNaoDisparaPorQuilometragem() {
        when(veiculoRepository.findByStatus("ATIVO")).thenReturn(List.of(veiculo(1, "AAA1111", 55000, 1)));
        when(manutencaoRepository.findUltimaManutencaoConcluida(1L)).thenReturn(concluida(1, 45000L));

        assertEquals(0, service.verificar().getCriados());
    }

    @Test
    void semManutencaoUsaDataCadastroEKmZero() {
        when(veiculoRepository.findByStatus("ATIVO")).thenReturn(List.of(veiculo(1, "AAA1111", 12000, 1)));
        when(manutencaoRepository.findUltimaManutencaoConcluida(1L)).thenReturn(null);
        stubEnvio(resp("ENVIADO", false));

        VerificacaoAlertasResponse r = service.verificar();

        assertEquals("QUILOMETRAGEM", r.getAlertas().get(0).getMotivo());
        assertNull(r.getAlertas().get(0).getDataUltimaManutencao());
    }

    @Test
    void semManutencaoElegivelPorTempoDesdeCadastro() {
        when(veiculoRepository.findByStatus("ATIVO")).thenReturn(List.of(veiculo(1, "AAA1111", 100, 10)));
        when(manutencaoRepository.findUltimaManutencaoConcluida(1L)).thenReturn(null);
        stubEnvio(resp("ENVIADO", false));

        assertEquals("TEMPO", service.verificar().getAlertas().get(0).getMotivo());
    }

    @Test
    void verificaApenasVeiculosAtivos() {
        service.verificar();

        verify(veiculoRepository).findByStatus("ATIVO");
        verify(veiculoRepository, never()).findAll();
    }

    @Test
    void ignoraVeiculoComAlertaAbertoPosteriorABase() {
        when(veiculoRepository.findByStatus("ATIVO")).thenReturn(List.of(veiculo(1, "AAA1111", 60000, 1)));
        when(manutencaoRepository.findUltimaManutencaoConcluida(1L)).thenReturn(concluida(1, 45000L));
        when(alertaRepository.existsByVeiculoIdAndStatusInAndDataCriacaoAfter(eq(1L),
                eq(List.of("PENDENTE", "ENVIADO")), any())).thenReturn(true);

        VerificacaoAlertasResponse r = service.verificar();

        assertEquals(1, r.getIgnoradosPorDuplicidade());
        assertEquals(0, r.getCriados());
        verifyNoInteractions(notificacaoClient);
    }

    @Test
    void respostaFalhaGravaFalha() {
        when(veiculoRepository.findByStatus("ATIVO")).thenReturn(List.of(veiculo(1, "AAA1111", 60000, 1)));
        when(manutencaoRepository.findUltimaManutencaoConcluida(1L)).thenReturn(concluida(1, 45000L));
        stubEnvio(resp("FALHA", false));

        VerificacaoAlertasResponse r = service.verificar();

        assertEquals(1, r.getFalhas());
        assertEquals("smtp", r.getAlertas().get(0).getErroMensagem());
        assertEquals(0, r.getAlertas().get(0).getTentativas());
    }

    @Test
    void salvaPendenteAntesDaChamadaHttp() {
        when(veiculoRepository.findByStatus("ATIVO")).thenReturn(List.of(veiculo(1, "AAA1111", 60000, 1)));
        when(manutencaoRepository.findUltimaManutencaoConcluida(1L)).thenReturn(concluida(1, 45000L));
        java.util.List<String> eventos = new java.util.ArrayList<>();
        when(alertaRepository.save(any(AlertaManutencao.class))).thenAnswer(inv -> {
            AlertaManutencao al = inv.getArgument(0);
            eventos.add("save:" + al.getStatus());
            return al;
        });
        when(notificacaoClient.enviarAlertaManutencao(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    eventos.add("http");
                    return resp("ENVIADO", false);
                });

        service.verificar();

        assertEquals(List.of("save:PENDENTE", "http", "save:ENVIADO"), eventos);
    }

    @Test
    void pendenteSemRespostaIncrementaTentativas() {
        when(veiculoRepository.findByStatus("ATIVO")).thenReturn(List.of(veiculo(1, "AAA1111", 60000, 1)));
        when(manutencaoRepository.findUltimaManutencaoConcluida(1L)).thenReturn(concluida(1, 45000L));
        stubEnvio(resp("PENDENTE", false));

        VerificacaoAlertasResponse r = service.verificar();

        assertEquals(1, r.getPendentes());
        assertEquals(1, r.getAlertas().get(0).getTentativas());
    }

    private AlertaManutencao pendente(long id, int tentativas) {
        AlertaManutencao a = new AlertaManutencao();
        a.setId(id);
        a.setVeiculoId(id);
        a.setPlaca("PEN" + id);
        a.setMotivo("TEMPO");
        a.setStatus("PENDENTE");
        a.setTentativas(tentativas);
        a.setDataCriacao(LocalDateTime.now().minusDays(1));
        return a;
    }

    @Test
    void reenviaPendenteEViraEnviado() {
        AlertaManutencao p = pendente(7, 2);
        when(alertaRepository.findByStatusAndTentativasLessThanOrderByDataCriacaoAsc("PENDENTE", 5))
                .thenReturn(List.of(p));
        stubEnvio(resp("ENVIADO", false));

        VerificacaoAlertasResponse r = service.verificar();

        assertEquals(1, r.getReenviados());
        assertEquals(1, r.getEnviados());
        assertEquals(0, r.getCriados());
        assertEquals("ENVIADO", p.getStatus());
        assertEquals(2, p.getTentativas());
        assertNull(p.getErroMensagem());
    }

    @Test
    void pendenteQueAtingeMaximoViraFalha() {
        AlertaManutencao p = pendente(7, 4);
        when(alertaRepository.findByStatusAndTentativasLessThanOrderByDataCriacaoAsc("PENDENTE", 5))
                .thenReturn(List.of(p));
        stubEnvio(resp("PENDENTE", false));

        VerificacaoAlertasResponse r = service.verificar();

        assertEquals("FALHA", p.getStatus());
        assertEquals(5, p.getTentativas());
        assertTrue(p.getErroMensagem().contains("Tentativas esgotadas"));
        assertEquals(1, r.getFalhas());
    }

    @Test
    void pendenteJaNoMaximoNaoEReenviado() {
        AlertaManutencao p = pendente(7, 5);
        when(alertaRepository.findByStatusAndTentativasGreaterThanEqual("PENDENTE", 5)).thenReturn(List.of(p));

        VerificacaoAlertasResponse r = service.verificar();

        assertEquals("FALHA", p.getStatus());
        assertEquals(0, r.getReenviados());
        verifyNoInteractions(notificacaoClient);
    }

    @Test
    void circuitBreakInterrompeChamadasAposInalcancavel() {
        when(veiculoRepository.findByStatus("ATIVO")).thenReturn(List.of(
                veiculo(1, "AAA1111", 60000, 1), veiculo(2, "BBB2222", 60000, 1), veiculo(3, "CCC3333", 60000, 1)));
        when(manutencaoRepository.findUltimaManutencaoConcluida(anyLong())).thenReturn(concluida(1, 45000L));
        stubEnvio(resp("PENDENTE", true));

        VerificacaoAlertasResponse r = service.verificar();

        verify(notificacaoClient, times(1)).enviarAlertaManutencao(any(), any(), any(), any(), any(), any(), any(), any());
        assertEquals(3, r.getCriados());
        assertEquals(3, r.getPendentes());
        assertEquals(1, r.getAlertas().get(0).getTentativas());
        assertEquals(0, r.getAlertas().get(1).getTentativas());
        assertEquals(0, r.getAlertas().get(2).getTentativas());
    }

    @Test
    void circuitBreakNoReenvioTambemPulaNovosAlertas() {
        AlertaManutencao p = pendente(7, 1);
        when(alertaRepository.findByStatusAndTentativasLessThanOrderByDataCriacaoAsc("PENDENTE", 5))
                .thenReturn(List.of(p));
        when(veiculoRepository.findByStatus("ATIVO")).thenReturn(List.of(veiculo(1, "AAA1111", 60000, 1)));
        when(manutencaoRepository.findUltimaManutencaoConcluida(1L)).thenReturn(concluida(1, 45000L));
        stubEnvio(resp("PENDENTE", true));

        VerificacaoAlertasResponse r = service.verificar();

        verify(notificacaoClient, times(1)).enviarAlertaManutencao(any(), any(), any(), any(), any(), any(), any(), any());
        assertEquals(1, r.getCriados());
        assertEquals(2, r.getPendentes());
    }

    @Test
    void listarComStatusInvalidoLancaExcecao() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.listar("XPTO"));
        assertEquals("Status inválido: XPTO", e.getMessage());
    }

    @Test
    void listarStatusIgnoraCaixa() {
        service.listar("enviado");

        verify(alertaRepository).findByStatusOrderByDataCriacaoDesc("ENVIADO");
    }

    @Test
    void listarSemStatusRetornaTodos() {
        service.listar(null);

        verify(alertaRepository).findAllByOrderByDataCriacaoDesc();
    }
}
