import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Button } from 'primeng/button';
import { UIChart } from 'primeng/chart';
import { DatePicker } from 'primeng/datepicker';
import { Select } from 'primeng/select';
import { SelectButton } from 'primeng/selectbutton';
import { TableModule } from 'primeng/table';

import { ApiError } from '../../../../core/api/problem-details';
import type { PageQuery } from '../../../../core/models/page';
import { NotificationService } from '../../../../core/notifications/notification.service';
import { createSubmitGuard } from '../../../../core/util/submitting';
import { EmptyStateComponent } from '../../../../shared/components/empty-state/empty-state';
import { ErrorStateComponent } from '../../../../shared/components/error-state/error-state';
import { LoadingSkeletonComponent } from '../../../../shared/components/loading-skeleton/loading-skeleton';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header';
import { ProgressBarComponent } from '../../../../shared/components/progress-bar/progress-bar';
import { createPageState } from '../../../../shared/forms/page-state';
import { DateTimePipe, RelativeTimePipe } from '../../../../shared/pipes/format.pipes';
import { ReportService } from '../../data/report.service';
import { TeacherRoomService } from '../../data/teacher-room.service';
import type { ReportFilters, ReportPeriod } from '../../models/report';
import { pickedDateToInstant } from '../../util/brasilia-time';

type TabId = 'visao-geral' | 'alunos' | 'ranking';

/**
 * Relatórios — Parte 5, §9.
 *
 * **Nenhum número desta tela é calculado aqui.** Métricas, séries, distribuição
 * e ranking chegam prontos; o CSV é gerado pelo backend; e a versão de
 * impressão usa exatamente os mesmos dados já carregados (§11 da spec).
 *
 * O padrão de período é 30 dias e isso é **exibido**, não implícito: um
 * relatório cujo recorte o leitor não conhece é um relatório enganoso.
 */
@Component({
  selector: 'cc-reports-page',
  imports: [
    FormsModule,
    Button,
    UIChart,
    DatePicker,
    Select,
    SelectButton,
    TableModule,
    PageHeaderComponent,
    LoadingSkeletonComponent,
    ErrorStateComponent,
    EmptyStateComponent,
    ProgressBarComponent,
    DateTimePipe,
    RelativeTimePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './reports.html',
  styleUrl: './reports.scss',
})
export class ReportsPage {
  private readonly reports = inject(ReportService);
  private readonly rooms = inject(TeacherRoomService);
  private readonly notify = inject(NotificationService);

  protected readonly activeTab = signal<TabId>('visao-geral');

  protected readonly roomId = signal<string | null>(null);
  protected readonly period = signal<ReportPeriod>('LAST_30_DAYS');
  protected readonly customRange = signal<Date[] | null>(null);
  protected readonly studentQuery = signal<PageQuery>({ page: 0, size: 20 });

  protected readonly exportGuard = createSubmitGuard();

  protected readonly periodOptions = [
    { value: 'LAST_30_DAYS' as const, label: 'Últimos 30 dias' },
    { value: 'CUSTOM' as const, label: 'Período' },
    { value: 'ALL' as const, label: 'Todo o histórico' },
  ];

  protected readonly roomOptions = createPageState(() => this.rooms.options());

  /** Filtros efetivos. `ALL` remove `from`/`to`, como a §7.5 da spec pede. */
  protected readonly filters = computed<ReportFilters>(() => {
    const period = this.period();
    const range = this.customRange();

    if (period === 'ALL') {
      return { roomId: this.roomId(), lessonId: null, period, from: null, to: null };
    }

    if (period === 'CUSTOM' && range?.[0]) {
      return {
        roomId: this.roomId(),
        lessonId: null,
        period,
        from: pickedDateToInstant(range[0]),
        to: pickedDateToInstant(range[1] ?? range[0]),
      };
    }

    const to = new Date();
    const from = new Date(to.getTime() - 30 * 86_400_000);
    return {
      roomId: this.roomId(),
      lessonId: null,
      period: 'LAST_30_DAYS',
      from: from.toISOString(),
      to: to.toISOString(),
    };
  });

  protected readonly overview = createPageState(() => this.reports.overview(this.filters()));
  protected readonly students = createPageState(() =>
    this.reports.students(this.filters(), this.studentQuery()),
  );
  protected readonly ranking = createPageState(() => this.reports.ranking(this.filters()));

  /** Rótulo do recorte, exibido na tela e na impressão. */
  protected readonly periodLabel = computed(() => {
    const filters = this.filters();
    if (filters.period === 'ALL') {
      return 'Todo o histórico';
    }
    if (filters.period === 'CUSTOM' && filters.from && filters.to) {
      return `De ${formatShort(filters.from)} a ${formatShort(filters.to)}`;
    }
    return 'Últimos 30 dias';
  });

  protected readonly attemptsChart = computed(() => {
    const data = this.overview.data();
    if (!data) {
      return null;
    }
    return {
      labels: data.attemptsOverTime.map((point) => point.date),
      datasets: [
        {
          label: 'Tentativas iniciadas',
          data: data.attemptsOverTime.map((point) => point.attempts),
          borderColor: '#7c3aed',
          backgroundColor: 'rgba(124, 58, 237, 0.15)',
          tension: 0.3,
          fill: true,
        },
        {
          label: 'Finalizadas',
          data: data.attemptsOverTime.map((point) => point.submitted),
          borderColor: '#16a34a',
          backgroundColor: 'rgba(22, 163, 74, 0.12)',
          tension: 0.3,
          fill: true,
        },
      ],
    };
  });

  protected readonly scoreChart = computed(() => {
    const data = this.overview.data();
    if (!data) {
      return null;
    }
    return {
      labels: data.scoreDistribution.map((bucket) => bucket.label),
      datasets: [
        {
          label: 'Tentativas',
          data: data.scoreDistribution.map((bucket) => bucket.count),
          backgroundColor: '#2563eb',
        },
      ],
    };
  });

  protected readonly chartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { position: 'bottom' as const } },
    scales: { y: { beginAtZero: true, ticks: { precision: 0 } } },
  };

  constructor() {
    void this.roomOptions.load();

    effect(() => {
      this.filters();
      void this.overview.load();
      void this.students.load();
      void this.ranking.load();
    });
  }

  protected selectTab(tab: TabId): void {
    this.activeTab.set(tab);
  }

  /** O CSV vem do backend: o frontend não monta arquivo de relatório. */
  protected async exportCsv(): Promise<void> {
    await this.exportGuard.run(async () => {
      try {
        const blob = await this.reports.exportCsv(this.filters());
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = 'relatorio-conta-certa.csv';
        anchor.click();
        URL.revokeObjectURL(url);
      } catch (error) {
        this.notify.error(error instanceof ApiError ? error : 'Não foi possível exportar o CSV.');
      }
    });
  }

  /** A impressão usa os dados já carregados — nada é recalculado. */
  protected print(): void {
    window.print();
  }
}

function formatShort(iso: string): string {
  const date = new Date(iso);
  return date.toLocaleDateString('pt-BR', { timeZone: 'America/Sao_Paulo' });
}
