import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FrotasService } from '../../services/frotas.service';
import { AlertaManutencao, VerificacaoAlertas } from '../../models';

@Component({
  selector: 'app-alertas',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="alertas-page" data-testid="alertas-page">
      <div class="page-header">
        <h1>Alertas de Manutenção Preventiva</h1>
        <div class="header-actions">
          <select data-testid="alertas-filtro-status" [value]="statusFiltro" (change)="filtrar($any($event.target).value)">
            <option value="">Todos</option>
            <option value="PENDENTE">PENDENTE</option>
            <option value="ENVIADO">ENVIADO</option>
            <option value="FALHA">FALHA</option>
          </select>
          <button class="btn-primary" data-testid="alertas-verificar" [disabled]="verificando" (click)="verificar()">
            {{ verificando ? 'Verificando...' : 'Verificar agora' }}
          </button>
        </div>
      </div>

      <div *ngIf="resumo" class="resumo" data-testid="alertas-resumo">
        Verificados: {{ resumo.veiculosVerificados }} |
        Criados: {{ resumo.criados }} |
        Enviados: {{ resumo.enviados }} |
        Pendentes: {{ resumo.pendentes }} |
        Falhas: {{ resumo.falhas }} |
        Reenviados: {{ resumo.reenviados }} |
        Ignorados por duplicidade: {{ resumo.ignoradosPorDuplicidade }}
      </div>

      <div *ngIf="erro" class="error" data-testid="alertas-erro">{{ erro }}</div>

      <div *ngIf="loading" class="loading" data-testid="alertas-loading">Carregando alertas...</div>

      <table class="data-table" data-testid="alertas-tabela" *ngIf="!loading && alertas.length > 0">
        <thead>
          <tr>
            <th>Placa</th>
            <th>Modelo</th>
            <th>Motivo</th>
            <th>Km Atual</th>
            <th>Última Manutenção</th>
            <th>Status</th>
            <th>Tentativas</th>
            <th>Criado em</th>
            <th>Erro</th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let alerta of alertas" data-testid="alerta-row">
            <td data-testid="alerta-placa">{{ alerta.placa }}</td>
            <td>{{ alerta.modelo }}</td>
            <td data-testid="alerta-motivo">{{ alerta.motivo }}</td>
            <td>{{ alerta.quilometragemAtual | number }}</td>
            <td>{{ alerta.dataUltimaManutencao || '-' }}</td>
            <td>
              <span class="status-badge" data-testid="alerta-status" [ngClass]="'status-' + alerta.status">
                {{ alerta.status }}
              </span>
            </td>
            <td>{{ alerta.tentativas }}</td>
            <td>{{ alerta.dataCriacao | date:'dd/MM/yyyy HH:mm' }}</td>
            <td>{{ alerta.erroMensagem }}</td>
          </tr>
        </tbody>
      </table>

      <div *ngIf="!loading && !erro && alertas.length === 0" class="empty-state" data-testid="alertas-vazio">
        Nenhum alerta encontrado.
      </div>
    </div>
  `,
  styles: [`
    .alertas-page { padding: 20px; }
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
    .page-header h1 { margin: 0; color: #333; font-size: 24px; }
    .header-actions { display: flex; gap: 12px; align-items: center; }
    .header-actions select { padding: 9px 12px; border: 1px solid #ccc; border-radius: 6px; }
    .btn-primary { background: #1565c0; color: white; border: none; padding: 10px 20px; border-radius: 6px; cursor: pointer; }
    .btn-primary:disabled { opacity: 0.6; cursor: default; }
    .resumo { background: #e3f2fd; color: #1565c0; padding: 12px; border-radius: 6px; margin-bottom: 16px; font-size: 14px; }
    .error { background: #ffebee; color: #c62828; padding: 12px; border-radius: 6px; margin-bottom: 16px; }
    .loading { text-align: center; padding: 40px; color: #666; }
    .data-table { width: 100%; border-collapse: collapse; background: white; border-radius: 12px; overflow: hidden; box-shadow: 0 2px 8px rgba(0,0,0,0.1); }
    .data-table th { text-align: left; padding: 14px; background: #f5f5f5; color: #666; font-size: 13px; text-transform: uppercase; }
    .data-table td { padding: 14px; border-bottom: 1px solid #f5f5f5; font-size: 14px; }
    .data-table tr:hover { background: #f9f9f9; }
    .status-badge { padding: 4px 10px; border-radius: 12px; font-size: 12px; font-weight: 500; }
    .status-PENDENTE { background: #fff3e0; color: #e65100; }
    .status-ENVIADO { background: #e8f5e9; color: #2e7d32; }
    .status-FALHA { background: #ffebee; color: #c62828; }
    .empty-state { text-align: center; padding: 40px; color: #999; background: white; border-radius: 12px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); }
  `]
})
export class AlertasComponent implements OnInit {
  alertas: AlertaManutencao[] = [];
  statusFiltro = '';
  loading = true;
  verificando = false;
  erro = '';
  resumo: VerificacaoAlertas | null = null;

  constructor(private frotasService: FrotasService) {}

  ngOnInit(): void {
    this.carregarAlertas();
  }

  async carregarAlertas(): Promise<void> {
    this.loading = true;
    this.alertas = await this.frotasService.getAlertas(this.statusFiltro || undefined);
    this.loading = false;
  }

  async filtrar(status: string): Promise<void> {
    this.statusFiltro = status;
    await this.carregarAlertas();
  }

  async verificar(): Promise<void> {
    this.verificando = true;
    this.erro = '';
    const resultado = await this.frotasService.verificarAlertas();
    if (resultado) {
      this.resumo = resultado;
    } else {
      this.erro = 'Erro ao verificar alertas de manutenção.';
    }
    this.verificando = false;
    await this.carregarAlertas();
  }
}
