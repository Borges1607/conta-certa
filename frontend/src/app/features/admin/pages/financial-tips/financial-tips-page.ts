import { ChangeDetectionStrategy, Component, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Button } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { Paginator, type PaginatorState } from 'primeng/paginator';
import { Select } from 'primeng/select';
import { TableModule } from 'primeng/table';

import { ApiError } from '../../../../core/api/problem-details';
import { NotificationService } from '../../../../core/notifications/notification.service';
import { createSubmitGuard } from '../../../../core/util/submitting';
import { EmptyStateComponent } from '../../../../shared/components/empty-state/empty-state';
import { ErrorStateComponent } from '../../../../shared/components/error-state/error-state';
import { LoadingSkeletonComponent } from '../../../../shared/components/loading-skeleton/loading-skeleton';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header';
import { createPageState } from '../../../../shared/forms/page-state';
import { LocalDatePipe } from '../../../../shared/pipes/format.pipes';
import { ActiveTagComponent } from '../../components/active-tag/active-tag';
import { TipFormDialogComponent } from '../../components/tip-form-dialog/tip-form-dialog';
import { FinancialTipService } from '../../data/financial-tip.service';
import { createListQuery } from '../../data/list-query';
import type { FinancialTip } from '../../models/financial-tip-dto';
import { scheduleRelation, todayLocalDate } from '../../util/local-date';

/**
 * Dicas financeiras — Parte 6, §6.
 *
 * `publicationDate` é `LocalDate` (`YYYY-MM-DD`), **não** instante: ele entra e
 * sai como texto, sem conversão de fuso. Comparar agendamento com "hoje" também
 * é feito por string, em `scheduleRelation` — usar `Date` aqui deslocaria o dia
 * conforme o fuso do navegador.
 */
@Component({
  selector: 'cc-financial-tips-page',
  imports: [
    FormsModule,
    Button,
    InputText,
    Select,
    Paginator,
    TableModule,
    PageHeaderComponent,
    LoadingSkeletonComponent,
    ErrorStateComponent,
    EmptyStateComponent,
    ActiveTagComponent,
    TipFormDialogComponent,
    LocalDatePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './financial-tips-page.html',
  styleUrl: './financial-tips-page.scss',
})
export class FinancialTipsPage {
  private readonly tips = inject(FinancialTipService);
  private readonly notify = inject(NotificationService);

  protected readonly query = createListQuery();
  protected readonly search = this.query.text('search');
  protected readonly active = this.query.flag('active');

  protected readonly dialogVisible = signal(false);
  protected readonly editing = signal<FinancialTip | null>(null);
  protected readonly busyId = signal<string | null>(null);

  /** "Hoje" como `YYYY-MM-DD`, para destacar a dica do dia. */
  protected readonly today = todayLocalDate();

  private readonly guard = createSubmitGuard();

  protected readonly activeOptions = [
    { value: null, label: 'Todas' },
    { value: 'true', label: 'Ativas' },
    { value: 'false', label: 'Inativas' },
  ];

  protected readonly state = createPageState(() =>
    this.tips.list(
      { search: this.search() || undefined, active: this.active() },
      this.query.pageQuery(),
    ),
  );

  constructor() {
    effect(() => {
      this.query.params();
      void this.state.load();
    });
  }

  protected currentActiveOption(): string | null {
    const value = this.active();
    return value === undefined ? null : String(value);
  }

  protected onSearch(value: string): void {
    this.query.setFilters({ search: value || null }, true);
  }

  protected onActiveChange(value: string | null): void {
    this.query.setFilters({ active: value });
  }

  protected onPage(event: PaginatorState): void {
    this.query.setPage({ page: event.page ?? 0, size: event.rows ?? 20 });
  }

  protected first(): number {
    const page = this.state.data();
    return page ? page.page * page.size : 0;
  }

  /** `past` | `today` | `future` — comparação por string, sem fuso. */
  protected relation(tip: FinancialTip): 'past' | 'today' | 'future' {
    return scheduleRelation(tip.publicationDate, this.today);
  }

  protected openCreate(): void {
    this.editing.set(null);
    this.dialogVisible.set(true);
  }

  protected openEdit(tip: FinancialTip): void {
    this.editing.set(tip);
    this.dialogVisible.set(true);
  }

  protected async onSaved(): Promise<void> {
    this.dialogVisible.set(false);
    await this.state.refresh();
  }

  protected async toggleActive(tip: FinancialTip): Promise<void> {
    const confirmed = tip.active
      ? await this.notify.destructive({
          header: `Desativar "${tip.title}"?`,
          message:
            'A dica deixa de ser sorteada para os alunos. O agendamento é preservado — reative quando quiser.',
          acceptLabel: 'Desativar',
        })
      : await this.notify.confirm({
          header: `Ativar "${tip.title}"?`,
          message: 'A dica volta a poder aparecer no painel dos alunos.',
          acceptLabel: 'Ativar',
          icon: 'pi pi-check-circle',
        });

    if (!confirmed) {
      return;
    }

    await this.run(
      tip.id,
      () => (tip.active ? this.tips.deactivate(tip.id) : this.tips.activate(tip.id)),
      tip.active ? 'Dica desativada' : 'Dica ativada',
    );
  }

  protected async remove(tip: FinancialTip): Promise<void> {
    const confirmed = await this.notify.destructive({
      header: `Excluir "${tip.title}"?`,
      message:
        'Se a dica já tiver sido exibida a alunos, ela será arquivada em vez de removida.',
      acceptLabel: 'Excluir',
    });

    if (!confirmed) {
      return;
    }

    await this.guard.run(async () => {
      this.busyId.set(tip.id);
      try {
        const outcome = await this.tips.remove(tip.id);
        // A API decide entre remover e arquivar; a mensagem segue a resposta.
        this.notify.success(
          outcome.kind === 'archived' ? 'Dica arquivada' : 'Dica excluída',
          outcome.kind === 'archived'
            ? 'Ela já havia sido exibida, então foi apenas desativada.'
            : undefined,
        );
        await this.state.refresh();
      } catch (error) {
        this.notify.error(error instanceof ApiError ? error : 'Não foi possível excluir a dica.');
      } finally {
        this.busyId.set(null);
      }
    });
  }

  private async run(id: string, action: () => Promise<unknown>, success: string): Promise<void> {
    await this.guard.run(async () => {
      this.busyId.set(id);
      try {
        await action();
        this.notify.success(success);
        await this.state.refresh();
      } catch (error) {
        this.notify.error(error instanceof ApiError ? error : 'Não foi possível concluir a ação.');
      } finally {
        this.busyId.set(null);
      }
    });
  }
}
