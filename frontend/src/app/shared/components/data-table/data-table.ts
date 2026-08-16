import {
  ChangeDetectionStrategy,
  Component,
  contentChild,
  effect,
  input,
  output,
  signal,
  TemplateRef,
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { TableModule, TableLazyLoadEvent } from 'primeng/table';

import { ApiError } from '../../../core/api/problem-details';
import type { Page, PageQuery } from '../../../core/models/page';
import { DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE } from '../../../core/models/page';
import { EmptyStateComponent } from '../empty-state/empty-state';
import { ErrorStateComponent } from '../error-state/error-state';
import { LoadingSkeletonComponent } from '../loading-skeleton/loading-skeleton';

/**
 * Tabela com paginação e ordenação de servidor — Parte 2, §4.4.
 *
 * Encapsula o par `Page<T>` + `PageQuery` e delega vazio, erro e carregamento
 * aos componentes de estado, para nenhuma página reimplementá-los.
 *
 * As colunas e as células entram por `ng-template`, então a tabela não conhece
 * nada do domínio.
 */
@Component({
  selector: 'cc-data-table',
  imports: [
    TableModule,
    NgTemplateOutlet,
    EmptyStateComponent,
    ErrorStateComponent,
    LoadingSkeletonComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (error(); as apiError) {
      <cc-error-state [error]="apiError" (retry)="retry.emit()" (back)="retry.emit()" />
    } @else if (loading() && !page()) {
      <cc-loading-skeleton kind="table" [count]="4" />
    } @else if (isEmpty()) {
      <cc-empty-state
        [title]="emptyTitle()"
        [message]="emptyMessage()"
        [actionLabel]="emptyActionLabel()"
        [icon]="emptyIcon()"
        (action)="emptyAction.emit()"
      />
    } @else {
      <div class="cc-scroll-x">
        <p-table
          [value]="rows()"
          [lazy]="true"
          [paginator]="showPaginator()"
          [rows]="pageSize()"
          [totalRecords]="totalElements()"
          [rowsPerPageOptions]="rowsPerPageOptions()"
          [loading]="loading()"
          [sortField]="sortField()"
          [sortOrder]="sortOrder()"
          [first]="first()"
          [dataKey]="dataKey()"
          [rowHover]="true"
          currentPageReportTemplate="{first}–{last} de {totalRecords}"
          [showCurrentPageReport]="true"
          (onLazyLoad)="onLazyLoad($event)"
        >
          <ng-template #header>
            <ng-container *ngTemplateOutlet="headerTemplate() ?? null" />
          </ng-template>

          <ng-template #body let-row let-rowIndex="rowIndex">
            <ng-container
              *ngTemplateOutlet="bodyTemplate() ?? null; context: { $implicit: row, index: rowIndex }"
            />
          </ng-template>
        </p-table>
      </div>
    }
  `,
})
export class DataTableComponent<T> {
  readonly page = input<Page<T> | null>(null);
  readonly loading = input(false);
  readonly error = input<ApiError | null>(null);
  readonly dataKey = input('id');

  readonly emptyTitle = input('Nada por aqui ainda');
  readonly emptyMessage = input('');
  readonly emptyActionLabel = input('Atualizar');
  readonly emptyIcon = input('pi pi-inbox');

  readonly rowsPerPageOptions = input<number[]>([10, 20, 50]);

  /** Emitido sempre que paginação ou ordenação mudam. */
  readonly queryChange = output<PageQuery>();
  readonly retry = output<void>();
  readonly emptyAction = output<void>();

  protected readonly headerTemplate = contentChild<TemplateRef<unknown>>('tableHeader');
  protected readonly bodyTemplate = contentChild<TemplateRef<unknown>>('tableBody');

  protected readonly sortField = signal<string | undefined>(undefined);
  protected readonly sortOrder = signal(1);
  protected readonly first = signal(0);

  constructor() {
    // Mantém o cursor da tabela em sincronia quando a página vem de fora
    // (navegação direta com parâmetros na URL, por exemplo).
    effect(() => {
      const current = this.page();
      if (current) {
        this.first.set(current.page * current.size);
      }
    });
  }

  protected rows(): T[] {
    return this.page()?.content ?? [];
  }

  protected totalElements(): number {
    return this.page()?.totalElements ?? 0;
  }

  protected pageSize(): number {
    return Math.min(this.page()?.size ?? DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE);
  }

  protected isEmpty(): boolean {
    const current = this.page();
    return current !== null && current.totalElements === 0 && !this.loading();
  }

  protected showPaginator(): boolean {
    return this.totalElements() > this.pageSize();
  }

  protected onLazyLoad(event: TableLazyLoadEvent): void {
    const size = Math.min(event.rows ?? DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE);
    const page = Math.floor((event.first ?? 0) / Math.max(1, size));

    const field = typeof event.sortField === 'string' ? event.sortField : undefined;
    this.sortField.set(field);
    this.sortOrder.set(event.sortOrder ?? 1);

    this.queryChange.emit({
      page,
      size,
      sort: field ? `${field},${event.sortOrder === -1 ? 'desc' : 'asc'}` : undefined,
    });
  }
}
