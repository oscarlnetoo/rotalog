-- RotaLog - Migration: tabela de alertas de manutenção preventiva (api-frotas)
-- Este script é idempotente (pode ser executado múltiplas vezes)
-- Executar após os scripts 01-08
-- Deve permanecer idêntico ao Flyway V3__Alerta_Manutencao.sql e à entidade AlertaManutencao

SET search_path TO frotas;

CREATE TABLE IF NOT EXISTS alertas_manutencao (
    id BIGSERIAL PRIMARY KEY,
    veiculo_id BIGINT NOT NULL,
    placa VARCHAR(7) NOT NULL,
    modelo VARCHAR(100),
    motivo VARCHAR(20) NOT NULL,
    quilometragem_atual BIGINT,
    limite_quilometragem BIGINT,
    data_ultima_manutencao DATE,
    meses_desde_ultima_manutencao INTEGER,
    meses_limite INTEGER,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    notificacao_id BIGINT,
    destinatario VARCHAR(255),
    erro_mensagem TEXT,
    tentativas INTEGER NOT NULL DEFAULT 0,
    data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_envio TIMESTAMP,
    data_atualizacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_alertas_manutencao_status ON alertas_manutencao (status);
CREATE INDEX IF NOT EXISTS idx_alertas_manutencao_veiculo ON alertas_manutencao (veiculo_id);
