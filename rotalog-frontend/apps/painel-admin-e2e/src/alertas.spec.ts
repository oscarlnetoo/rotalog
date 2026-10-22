import { test, expect, Page, APIRequestContext } from '@playwright/test';

/**
 * E2E das telas de alertas de manutencao preventiva (/alertas).
 *
 * 1. Teste com mock (sempre roda): page.route serve linhas com os tres status.
 * 2. @fullstack (so roda com E2E_FULLSTACK definido): fluxo completo contra
 *    api-frotas (8080), api-notificacoes (5000, EmailSettings__SimularFalha=false)
 *    e PostgreSQL. Cria um veiculo dedicado (placa unica por execucao), dispara
 *    /verificar e espera ENVIADO. Assertivas apenas sobre a placa criada, nunca
 *    sobre contagens (o seed deixa muitos veiculos elegiveis).
 * 3. Caso PENDENTE, em duas fases orquestradas por variaveis de ambiente. Quem
 *    sobe/derruba a api-frotas entre as fases e o operador (ou o script de CI):
 *
 *    Fase 1: frotas iniciada com ROTALOG_API_NOTIFICACOES_URL=http://localhost:59999
 *      E2E_FULLSTACK=1 E2E_PENDING_PHASE=1 E2E_PENDING_PLATE=<7 chars> npx nx e2e painel-admin-e2e
 *      -> cria o veiculo <placa>, roda verificar e espera o alerta PENDENTE.
 *    Fase 2: frotas reiniciada SEM essa variavel (URL real).
 *      E2E_FULLSTACK=1 E2E_PENDING_PHASE=2 E2E_PENDING_PLATE=<mesma placa> npx nx e2e painel-admin-e2e
 *      -> roda verificar de novo e espera o MESMO alerta (mesmo id) ENVIADO.
 *
 *    Quando E2E_PENDING_PHASE esta definido, apenas o teste da fase correspondente
 *    roda (o fluxo normal esperaria ENVIADO e falharia com a URL errada).
 *    A placa tem de ser unica a cada par de fases (veiculos nao sao apagados).
 */

const FROTAS = 'http://localhost:8080/api';
const NOTIFICACOES = 'http://localhost:5000/api';

const fullstack = !!process.env['E2E_FULLSTACK'];
const fase = process.env['E2E_PENDING_PHASE'];
const placaPendente = process.env['E2E_PENDING_PLATE'] ?? '';

interface AlertaApi {
  id: number;
  placa: string;
  status: string;
  tentativas: number;
  notificacaoId: number | null;
}

const alertaBase = {
  veiculoId: 1,
  modelo: 'Fiat Fiorino',
  motivo: 'QUILOMETRAGEM',
  quilometragemAtual: 52000,
  limiteQuilometragem: 10000,
  dataUltimaManutencao: null,
  mesesDesdeUltimaManutencao: null,
  mesesLimite: 6,
  notificacaoId: null,
  destinatario: 'gestor@rotalog.com',
  erroMensagem: null,
  tentativas: 0,
  dataCriacao: '2026-10-01T10:00:00',
  dataEnvio: null,
  dataAtualizacao: '2026-10-01T10:00:00',
};

const alertasMock = [
  { ...alertaBase, id: 1, placa: 'MCK0001', status: 'PENDENTE', tentativas: 2 },
  { ...alertaBase, id: 2, placa: 'MCK0002', status: 'ENVIADO', motivo: 'TEMPO', notificacaoId: 10 },
  { ...alertaBase, id: 3, placa: 'MCK0003', status: 'FALHA', motivo: 'AMBOS', erroMensagem: 'SMTP falhou' },
];

function placaUnica(): string {
  // 'E2E' + 4 caracteres base36 do relogio: 7 chars, unica na pratica por execucao
  return 'E2E' + (Date.now() % 36 ** 4).toString(36).toUpperCase().padStart(4, '0');
}

async function criarVeiculoAcimaDoLimite(request: APIRequestContext, placa: string): Promise<number> {
  // POST /veiculos aceita placa (exatamente 7 chars), modelo e anoFabricacao (1900-2100);
  // a quilometragem nasce 0 e so e gravada via PUT /veiculos/{id}.
  const criado = await request.post(`${FROTAS}/veiculos`, {
    data: { placa, modelo: 'E2E Teste Preventiva', anoFabricacao: 2024 },
  });
  expect(criado.status(), await criado.text()).toBe(201);
  const veiculo = await criado.json();

  // 15.000 km sem manutencao concluida > limite de 10.000 (e < 50.000 do alerta legado)
  const atualizado = await request.put(`${FROTAS}/veiculos/${veiculo.id}`, {
    data: { quilometragem: 15000 },
  });
  expect(atualizado.status(), await atualizado.text()).toBe(200);
  return veiculo.id;
}

async function verificar(request: APIRequestContext) {
  const resp = await request.post(`${FROTAS}/alertas-manutencao/verificar`);
  expect(resp.status(), await resp.text()).toBe(200);
  return resp.json();
}

async function buscarAlertasDaPlaca(request: APIRequestContext, placa: string) {
  const resp = await request.get(`${FROTAS}/alertas-manutencao`);
  expect(resp.status()).toBe(200);
  const todos: AlertaApi[] = await resp.json();
  return todos.filter((a) => a.placa === placa);
}

function linhaDaPlaca(page: Page, placa: string) {
  return page.getByTestId('alerta-row').filter({ hasText: placa });
}

test.describe('Alertas de manutencao preventiva (UI com mock)', () => {
  test('lista os tres status com badges e filtra FALHA no servidor', async ({ page }) => {
    const urls: string[] = [];
    await page.route(/localhost:8080\/api\/alertas-manutencao/, async (route) => {
      const url = new URL(route.request().url());
      urls.push(url.toString());
      const status = url.searchParams.get('status');
      const body = status ? alertasMock.filter((a) => a.status === status) : alertasMock;
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        headers: { 'access-control-allow-origin': '*' },
        body: JSON.stringify(body),
      });
    });

    await page.goto('/alertas');
    await expect(page.getByTestId('alertas-page')).toBeVisible();

    const linhas = page.getByTestId('alerta-row');
    await expect(linhas).toHaveCount(3);
    await expect(linhas.nth(0).getByTestId('alerta-placa')).toHaveText('MCK0001');
    await expect(linhas.nth(0).getByTestId('alerta-status')).toHaveClass(/status-PENDENTE/);
    await expect(linhas.nth(1).getByTestId('alerta-status')).toHaveClass(/status-ENVIADO/);
    await expect(linhas.nth(1).getByTestId('alerta-motivo')).toHaveText('TEMPO');
    await expect(linhas.nth(2).getByTestId('alerta-status')).toHaveClass(/status-FALHA/);
    await expect(linhas.nth(2).getByTestId('alerta-motivo')).toHaveText('AMBOS');
    expect(urls[0]).not.toContain('status=');

    const requisicaoFiltro = page.waitForRequest(
      (r) => r.url().includes('/api/alertas-manutencao') && r.url().includes('status=FALHA'),
    );
    await page.getByTestId('alertas-filtro-status').selectOption('FALHA');
    await requisicaoFiltro;

    await expect(linhas).toHaveCount(1);
    await expect(linhas.first().getByTestId('alerta-placa')).toHaveText('MCK0003');
    await expect(linhas.first().getByTestId('alerta-status')).toHaveText('FALHA');
  });
});

test.describe('@fullstack alertas de manutencao preventiva', () => {
  test.skip(!fullstack, 'defina E2E_FULLSTACK=1 com a stack no ar');

  test('veiculo acima do limite gera alerta ENVIADO', async ({ page, request }) => {
    test.skip(!!fase, 'E2E_PENDING_PHASE definido: fluxo normal desativado');
    const placa = placaUnica();
    await criarVeiculoAcimaDoLimite(request, placa);

    const resultado = await verificar(request);
    const alerta = resultado.alertas.find((a: AlertaApi) => a.placa === placa);
    expect(alerta, `alerta para ${placa} na resposta de verificar`).toBeTruthy();
    console.log(`[fullstack] placa=${placa} alertaId=${alerta.id} notificacaoId=${alerta.notificacaoId}`);

    await page.goto('/alertas');
    const linha = linhaDaPlaca(page, placa);
    await expect(linha).toHaveCount(1);
    await expect(linha.getByTestId('alerta-motivo')).toHaveText('QUILOMETRAGEM');
    await expect(linha.getByTestId('alerta-status')).toHaveText('ENVIADO');
    await expect(linha.getByTestId('alerta-status')).toHaveClass(/status-ENVIADO/);

    // Confirma o registro do email em api-notificacoes
    expect(alerta.notificacaoId).toBeTruthy();
    const notif = await request.get(`${NOTIFICACOES}/notificacoes/${alerta.notificacaoId}`);
    expect(notif.status()).toBe(200);
    const corpo = await notif.json();
    expect(corpo.tipo).toBe('MANUTENCAO_PREVENTIVA');
    expect(corpo.status).toBe('ENVIADO');
  });

  test('pendente fase 1: api-notificacoes inalcancavel gera PENDENTE', async ({ page, request }) => {
    test.skip(fase !== '1', 'requer E2E_PENDING_PHASE=1 (frotas com ROTALOG_API_NOTIFICACOES_URL=http://localhost:59999)');
    expect(placaPendente, 'E2E_PENDING_PLATE obrigatoria').toMatch(/^[A-Z0-9]{7}$/);
    await criarVeiculoAcimaDoLimite(request, placaPendente);

    await verificar(request);
    const alertas = await buscarAlertasDaPlaca(request, placaPendente);
    expect(alertas).toHaveLength(1);
    expect(alertas[0].status).toBe('PENDENTE');
    console.log(`[fullstack fase 1] placa=${placaPendente} alertaId=${alertas[0].id} tentativas=${alertas[0].tentativas}`);

    await page.goto('/alertas');
    const linha = linhaDaPlaca(page, placaPendente);
    await expect(linha).toHaveCount(1);
    await expect(linha.getByTestId('alerta-status')).toHaveText('PENDENTE');
    await expect(linha.getByTestId('alerta-status')).toHaveClass(/status-PENDENTE/);
  });

  test('pendente fase 2: com a URL real o mesmo alerta vira ENVIADO', async ({ page, request }) => {
    test.skip(fase !== '2', 'requer E2E_PENDING_PHASE=2 (frotas reiniciada com a URL real)');
    expect(placaPendente, 'E2E_PENDING_PLATE obrigatoria').toMatch(/^[A-Z0-9]{7}$/);
    const antes = await buscarAlertasDaPlaca(request, placaPendente);
    expect(antes).toHaveLength(1);
    expect(antes[0].status).toBe('PENDENTE');

    await verificar(request);
    const depois = await buscarAlertasDaPlaca(request, placaPendente);
    expect(depois).toHaveLength(1);
    expect(depois[0].id).toBe(antes[0].id);
    expect(depois[0].status).toBe('ENVIADO');
    console.log(`[fullstack fase 2] placa=${placaPendente} alertaId=${depois[0].id} notificacaoId=${depois[0].notificacaoId}`);

    await page.goto('/alertas');
    const linha = linhaDaPlaca(page, placaPendente);
    await expect(linha).toHaveCount(1);
    await expect(linha.getByTestId('alerta-status')).toHaveText('ENVIADO');
  });
});
