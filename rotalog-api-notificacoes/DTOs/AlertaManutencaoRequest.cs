namespace api_notificacoes.DTOs;

/// <summary>
/// DTO do alerta de manutenção preventiva (chamado pelo api-frotas)
/// POST /api/notificacoes/alertas-manutencao
/// </summary>
public class AlertaManutencaoRequest
{
    public long? VeiculoId { get; set; }
    public string? Placa { get; set; }
    public string? Modelo { get; set; }
    public long? QuilometragemAtual { get; set; }
    public long? LimiteQuilometragem { get; set; }
    public DateTime? DataUltimaManutencao { get; set; }
    public int? MesesLimite { get; set; }

    // QUILOMETRAGEM | TEMPO | AMBOS
    public string? Motivo { get; set; }

    // Opcional: quando ausente usa AlertaManutencao:DestinatarioGestor
    public string? Destinatario { get; set; }
}
