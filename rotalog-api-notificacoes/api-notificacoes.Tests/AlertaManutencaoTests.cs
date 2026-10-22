using api_notificacoes.Controllers;
using api_notificacoes.Data;
using api_notificacoes.DTOs;
using api_notificacoes.Models;
using api_notificacoes.Services;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging.Abstractions;
using Xunit;

namespace api_notificacoes.Tests;

/// <summary>Service que permite forcar sucesso/falha do envio de email.</summary>
public class ServiceComEnvioControlado : NotificacaoService
{
    private readonly Exception? _falha;
    public int ChamadasEnvio { get; private set; }

    public ServiceComEnvioControlado(NotificacoesDbContext ctx, IConfiguration cfg, Exception? falha)
        : base(ctx, NullLogger<NotificacaoService>.Instance, cfg)
    {
        _falha = falha;
    }

    protected override Task EnviarEmail(Notificacao notificacao)
    {
        ChamadasEnvio++;
        if (_falha != null) throw _falha;
        return Task.CompletedTask;
    }
}

public class AlertaManutencaoTests
{
    private static NotificacoesDbContext NovoContexto()
    {
        var options = new DbContextOptionsBuilder<NotificacoesDbContext>()
            .UseInMemoryDatabase(Guid.NewGuid().ToString())
            .Options;
        return new NotificacoesDbContext(options);
    }

    private static IConfiguration Config(params (string, string?)[] valores) =>
        new ConfigurationBuilder()
            .AddInMemoryCollection(valores.ToDictionary(v => v.Item1, v => v.Item2))
            .Build();

    private static AlertaManutencaoRequest Req(Action<AlertaManutencaoRequest>? ajuste = null)
    {
        var r = new AlertaManutencaoRequest
        {
            VeiculoId = 12,
            Placa = "ABC1D23",
            Modelo = "Fiat Fiorino",
            QuilometragemAtual = 52000,
            LimiteQuilometragem = 10000,
            DataUltimaManutencao = new DateTime(2025, 3, 1),
            MesesLimite = 6,
            Motivo = "QUILOMETRAGEM"
        };
        ajuste?.Invoke(r);
        return r;
    }

    private static ServiceComEnvioControlado Service(NotificacoesDbContext ctx, IConfiguration? cfg = null, Exception? falha = null) =>
        new(ctx, cfg ?? Config(), falha);

    // ---- destinatario ----

    [Fact]
    public async Task Destinatario_UsaValorDaRequisicaoPrimeiro()
    {
        using var ctx = NovoContexto();
        var svc = Service(ctx, Config(("AlertaManutencao:DestinatarioGestor", "config@x.com")));

        var resp = await svc.EnviarAlertaManutencao(Req(r => r.Destinatario = "req@x.com"));

        Assert.Equal("req@x.com", resp.Destinatario);
    }

    [Fact]
    public async Task Destinatario_UsaConfigQuandoRequisicaoOmite()
    {
        using var ctx = NovoContexto();
        var svc = Service(ctx, Config(("AlertaManutencao:DestinatarioGestor", "config@x.com")));

        var resp = await svc.EnviarAlertaManutencao(Req());

        Assert.Equal("config@x.com", resp.Destinatario);
    }

    [Fact]
    public async Task Destinatario_UsaDefaultQuandoNadaConfigurado()
    {
        using var ctx = NovoContexto();
        var svc = Service(ctx);

        var resp = await svc.EnviarAlertaManutencao(Req(r => r.Destinatario = "  "));

        Assert.Equal("gestor@rotalog.com", resp.Destinatario);
    }

    // ---- template ----

    [Fact]
    public async Task Template_EhAplicadoQuandoExiste()
    {
        using var ctx = NovoContexto();
        ctx.Templates.Add(new TemplateNotificacao
        {
            Tipo = "MANUTENCAO_PREVENTIVA",
            Canal = "email",
            AssuntoTemplate = "Assunto {{placa}}",
            CorpoTemplate = "{{placa}}|{{modelo}}|{{quilometragem_atual}}|{{limite_quilometragem}}|{{data_ultima_manutencao}}|{{meses_limite}}|{{motivo}}",
            Ativo = true
        });
        await ctx.SaveChangesAsync();
        var svc = Service(ctx);

        var resp = await svc.EnviarAlertaManutencao(Req());

        var n = await ctx.Notificacoes.SingleAsync(x => x.Id == resp.NotificacaoId);
        Assert.Equal("Assunto ABC1D23", n.Assunto);
        Assert.Equal("ABC1D23|Fiat Fiorino|52000|10000|01/03/2025|6|QUILOMETRAGEM", n.Mensagem);
    }

    [Fact]
    public async Task Template_InativoOuAusente_UsaMensagemFallback()
    {
        using var ctx = NovoContexto();
        ctx.Templates.Add(new TemplateNotificacao
        {
            Tipo = "MANUTENCAO_PREVENTIVA", Canal = "email", CorpoTemplate = "NAO DEVE USAR", Ativo = false
        });
        await ctx.SaveChangesAsync();
        var svc = Service(ctx);

        var resp = await svc.EnviarAlertaManutencao(Req());

        var n = await ctx.Notificacoes.SingleAsync(x => x.Id == resp.NotificacaoId);
        Assert.False(string.IsNullOrEmpty(n.Mensagem));
        Assert.DoesNotContain("NAO DEVE USAR", n.Mensagem);
        Assert.Contains("ABC1D23", n.Mensagem);
        Assert.Contains("QUILOMETRAGEM", n.Mensagem);
        Assert.Contains("ABC1D23", n.Assunto);
    }

    // ---- envio ----

    [Fact]
    public async Task Envio_Sucesso_MarcaEnviadoEPersisteCampos()
    {
        using var ctx = NovoContexto();
        var svc = Service(ctx);

        var resp = await svc.EnviarAlertaManutencao(Req());

        Assert.Equal("ENVIADO", resp.Status);
        Assert.Null(resp.ErroMensagem);
        Assert.NotNull(resp.DataEnvio);
        Assert.Equal(12, resp.VeiculoId);

        var n = await ctx.Notificacoes.SingleAsync();
        Assert.Equal(resp.NotificacaoId, n.Id);
        Assert.Equal("MANUTENCAO_PREVENTIVA", n.Tipo);
        Assert.Equal("email", n.Canal);
        Assert.Equal("api-frotas", n.ServicoOrigem);
        Assert.Equal("veiculo-12", n.ReferenciaId);
        Assert.Equal("ENVIADO", n.Status);
        Assert.Equal(1, n.Tentativas);
        Assert.NotNull(n.DataEnvio);
    }

    [Fact]
    public async Task Envio_Falha_MarcaFalhaComUmaUnicaTentativa()
    {
        using var ctx = NovoContexto();
        var svc = Service(ctx, falha: new Exception("SMTP connection timeout (simulated)"));

        var resp = await svc.EnviarAlertaManutencao(Req());

        Assert.Equal("FALHA", resp.Status);
        Assert.Equal("SMTP connection timeout (simulated)", resp.ErroMensagem);
        Assert.Null(resp.DataEnvio);
        Assert.Equal(1, svc.ChamadasEnvio);

        var n = await ctx.Notificacoes.SingleAsync();
        Assert.Equal("FALHA", n.Status);
        Assert.Equal(1, n.Tentativas);
        Assert.Equal("SMTP connection timeout (simulated)", n.ErroMensagem);
        Assert.Null(n.DataEnvio);
    }

    [Fact]
    public async Task SimularFalhaFalse_NuncaFalhaNoEnvioReal()
    {
        using var ctx = NovoContexto();
        // NotificacaoService real (sem override): usa o EnviarEmail com falha aleatoria
        var svc = new NotificacaoService(ctx, NullLogger<NotificacaoService>.Instance,
            Config(("EmailSettings:SimularFalha", "false")));

        for (var i = 0; i < 25; i++)
        {
            var resp = await svc.EnviarAlertaManutencao(Req(r => r.VeiculoId = i + 1));
            Assert.Equal("ENVIADO", resp.Status);
        }
    }

    // ---- validacao no service ----

    [Theory]
    [InlineData(null, "ABC1D23", "TEMPO")]
    [InlineData(1L, null, "TEMPO")]
    [InlineData(1L, "", "TEMPO")]
    [InlineData(1L, "ABC1D23", null)]
    [InlineData(1L, "ABC1D23", "INVALIDO")]
    public async Task Service_LancaArgumentExceptionParaDadosInvalidos(long? veiculoId, string? placa, string? motivo)
    {
        using var ctx = NovoContexto();
        var svc = Service(ctx);

        await Assert.ThrowsAsync<ArgumentException>(() => svc.EnviarAlertaManutencao(
            Req(r => { r.VeiculoId = veiculoId; r.Placa = placa; r.Motivo = motivo; })));

        Assert.Empty(ctx.Notificacoes);
        Assert.Equal(0, svc.ChamadasEnvio);
    }

    // ---- controller ----

    private static NotificacoesController Controller(NotificacaoService svc) =>
        new(svc, NullLogger<NotificacoesController>.Instance);

    [Theory]
    [InlineData(null, "ABC1D23", "TEMPO")]
    [InlineData(1L, null, "TEMPO")]
    [InlineData(1L, "ABC1D23", null)]
    [InlineData(1L, "ABC1D23", "OUTRO")]
    public async Task Controller_Retorna400ParaDadosInvalidos(long? veiculoId, string? placa, string? motivo)
    {
        using var ctx = NovoContexto();
        var result = await Controller(Service(ctx)).EnviarAlertaManutencao(
            Req(r => { r.VeiculoId = veiculoId; r.Placa = placa; r.Motivo = motivo; }));

        var bad = Assert.IsType<BadRequestObjectResult>(result);
        Assert.Equal(400, bad.StatusCode);
        Assert.NotNull(bad.Value!.GetType().GetProperty("error"));
    }

    [Theory]
    [InlineData("QUILOMETRAGEM")]
    [InlineData("TEMPO")]
    [InlineData("AMBOS")]
    public async Task Controller_Retorna200ParaMotivosValidos(string motivo)
    {
        using var ctx = NovoContexto();
        var result = await Controller(Service(ctx)).EnviarAlertaManutencao(Req(r => r.Motivo = motivo));

        var ok = Assert.IsType<OkObjectResult>(result);
        Assert.Equal("ENVIADO", Assert.IsType<AlertaManutencaoResponse>(ok.Value).Status);
    }

    [Fact]
    public async Task Controller_Retorna200ParaFalhaDeEnvio()
    {
        using var ctx = NovoContexto();
        var result = await Controller(Service(ctx, falha: new Exception("boom"))).EnviarAlertaManutencao(Req());

        var ok = Assert.IsType<OkObjectResult>(result);
        Assert.Equal(200, ok.StatusCode);
        Assert.Equal("FALHA", Assert.IsType<AlertaManutencaoResponse>(ok.Value).Status);
    }

    [Fact]
    public async Task Controller_Retorna500ParaErroInesperado()
    {
        var ctx = NovoContexto();
        var svc = Service(ctx);
        ctx.Dispose(); // forca ObjectDisposedException no service

        var result = await Controller(svc).EnviarAlertaManutencao(Req());

        var erro = Assert.IsType<ObjectResult>(result);
        Assert.Equal(500, erro.StatusCode);
        Assert.NotNull(erro.Value!.GetType().GetProperty("error"));
        Assert.NotNull(erro.Value!.GetType().GetProperty("detalhes"));
    }
}
