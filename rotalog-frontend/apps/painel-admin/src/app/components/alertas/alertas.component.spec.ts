import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AlertasComponent } from './alertas.component';
import { FrotasService } from '../../services/frotas.service';
import { AlertaManutencao } from '../../models';

function alerta(id: number, placa: string, status: AlertaManutencao['status']): AlertaManutencao {
  return {
    id,
    veiculoId: id,
    placa,
    modelo: 'Fiat Fiorino',
    motivo: 'QUILOMETRAGEM',
    quilometragemAtual: 52000,
    limiteQuilometragem: 10000,
    dataUltimaManutencao: '2025-03-01',
    mesesDesdeUltimaManutencao: 7,
    mesesLimite: 6,
    status,
    notificacaoId: null,
    destinatario: 'gestor@rotalog.com',
    erroMensagem: status === 'FALHA' ? 'SMTP indisponível' : null,
    tentativas: 1,
    dataCriacao: '2025-10-01T10:00:00',
    dataEnvio: null,
    dataAtualizacao: '2025-10-01T10:00:00'
  };
}

describe('AlertasComponent', () => {
  let fixture: ComponentFixture<AlertasComponent>;
  let el: HTMLElement;
  let service: { getAlertas: jest.Mock; verificarAlertas: jest.Mock };

  const lista = [
    alerta(1, 'AAA1A11', 'PENDENTE'),
    alerta(2, 'BBB2B22', 'ENVIADO'),
    alerta(3, 'CCC3C33', 'FALHA')
  ];

  async function criar(): Promise<void> {
    fixture = TestBed.createComponent(AlertasComponent);
    el = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(async () => {
    service = {
      getAlertas: jest.fn().mockResolvedValue(lista),
      verificarAlertas: jest.fn().mockResolvedValue({
        veiculosVerificados: 7,
        criados: 2,
        enviados: 1,
        pendentes: 1,
        falhas: 0,
        reenviados: 0,
        ignoradosPorDuplicidade: 3,
        alertas: []
      })
    };
    await TestBed.configureTestingModule({
      imports: [AlertasComponent],
      providers: [{ provide: FrotasService, useValue: service }]
    }).compileComponents();
  });

  it('exibe o estado de carregamento antes da resposta', () => {
    service.getAlertas.mockReturnValue(new Promise(() => undefined));
    fixture = TestBed.createComponent(AlertasComponent);
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('[data-testid="alertas-loading"]')).not.toBeNull();
    expect(root.querySelector('[data-testid="alertas-tabela"]')).toBeNull();
  });

  it('renderiza uma linha por alerta', async () => {
    await criar();

    const rows = el.querySelectorAll('[data-testid="alerta-row"]');
    expect(rows.length).toBe(3);
    expect(rows[0].textContent).toContain('AAA1A11');
    expect(el.querySelector('[data-testid="alertas-loading"]')).toBeNull();
    expect(service.getAlertas).toHaveBeenCalledWith(undefined);
  });

  it('aplica a classe do badge conforme o status', async () => {
    await criar();

    const badges = Array.from(el.querySelectorAll('[data-testid="alerta-status"]'));
    expect(badges.map(b => b.className)).toEqual([
      expect.stringContaining('status-PENDENTE'),
      expect.stringContaining('status-ENVIADO'),
      expect.stringContaining('status-FALHA')
    ]);
  });

  it('repassa o valor do filtro ao serviço', async () => {
    await criar();
    service.getAlertas.mockClear();
    service.getAlertas.mockResolvedValue([lista[2]]);

    const select = el.querySelector('[data-testid="alertas-filtro-status"]') as HTMLSelectElement;
    select.value = 'FALHA';
    select.dispatchEvent(new Event('change'));
    await fixture.whenStable();
    fixture.detectChanges();

    expect(service.getAlertas).toHaveBeenCalledWith('FALHA');
    expect(el.querySelectorAll('[data-testid="alerta-row"]').length).toBe(1);
  });

  it('voltar para Todos chama o serviço sem status', async () => {
    await criar();
    const select = el.querySelector('[data-testid="alertas-filtro-status"]') as HTMLSelectElement;
    select.value = 'FALHA';
    select.dispatchEvent(new Event('change'));
    await fixture.whenStable();
    service.getAlertas.mockClear();

    select.value = '';
    select.dispatchEvent(new Event('change'));
    await fixture.whenStable();

    expect(service.getAlertas).toHaveBeenCalledWith(undefined);
  });

  it('exibe o estado vazio quando não há alertas', async () => {
    service.getAlertas.mockResolvedValue([]);
    await criar();

    expect(el.querySelector('[data-testid="alertas-vazio"]')).not.toBeNull();
    expect(el.querySelector('[data-testid="alertas-tabela"]')).toBeNull();
  });

  it('Verificar agora chama verificar, mostra o resumo e recarrega a lista', async () => {
    await criar();
    service.getAlertas.mockClear();

    (el.querySelector('[data-testid="alertas-verificar"]') as HTMLButtonElement).click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(service.verificarAlertas).toHaveBeenCalledTimes(1);
    expect(service.getAlertas).toHaveBeenCalledTimes(1);
    expect(el.querySelector('[data-testid="alertas-resumo"]')?.textContent).toContain('Criados: 2');
  });

  it('mostra erro quando a verificação falha', async () => {
    service.verificarAlertas.mockResolvedValue(null);
    await criar();

    (el.querySelector('[data-testid="alertas-verificar"]') as HTMLButtonElement).click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(el.querySelector('[data-testid="alertas-erro"]')).not.toBeNull();
    expect(el.querySelector('[data-testid="alertas-resumo"]')).toBeNull();
  });
});
