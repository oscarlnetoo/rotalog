-- RotaLog - Seed: template de e-mail MANUTENCAO_PREVENTIVA (api-notificacoes)
-- Este script é idempotente (pode ser executado múltiplas vezes)
-- Executar após os scripts 01-09
-- Mesmo INSERT deve constar em rotalog-api-notificacoes/Data/seed.sql

SET search_path TO notificacoes;

INSERT INTO templates_notificacao (tipo, canal, assunto_template, corpo_template, ativo)
SELECT 'MANUTENCAO_PREVENTIVA', 'email',
       'Manutenção preventiva necessária - Veículo {{placa}}',
       E'O veículo {{placa}} ({{modelo}}) precisa de manutenção preventiva.\n\nMotivo: {{motivo}}\nQuilometragem atual: {{quilometragem_atual}} km\nLimite de quilometragem desde a última manutenção: {{limite_quilometragem}} km\nData da última manutenção: {{data_ultima_manutencao}}\nLimite de tempo: {{meses_limite}} meses\n\nAgende a manutenção o quanto antes.\n\nEquipe RotaLog',
       true
WHERE NOT EXISTS (
    SELECT 1 FROM templates_notificacao WHERE tipo = 'MANUTENCAO_PREVENTIVA' AND canal = 'email'
);
