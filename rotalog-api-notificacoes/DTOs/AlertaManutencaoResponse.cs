namespace api_notificacoes.DTOs;

/// <summary>
/// Resposta do alerta de manutenção preventiva: status ENVIADO ou FALHA
/// </summary>
public class AlertaManutencaoResponse
{
    public long NotificacaoId { get; set; }
    public string Status { get; set; } = string.Empty;
    public string Destinatario { get; set; } = string.Empty;
    public string? ErroMensagem { get; set; }
    public DateTime? DataEnvio { get; set; }
    public long VeiculoId { get; set; }
}
