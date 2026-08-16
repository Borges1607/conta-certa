import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { Skeleton } from 'primeng/skeleton';

export type SkeletonKind = 'list' | 'card-grid' | 'table' | 'form' | 'detail' | 'stats';

/**
 * Esqueleto de carga inicial — Parte 2, §4.1.
 *
 * Usado **apenas** na primeira carga. Atualização em segundo plano mantém o
 * conteúdo na tela (Parte 2, §6).
 */
@Component({
  selector: 'cc-loading-skeleton',
  imports: [Skeleton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="skeleton" [attr.aria-busy]="true" aria-live="polite">
      <span class="cc-sr-only">Carregando…</span>

      @switch (kind()) {
        @case ('card-grid') {
          <div class="cc-grid">
            @for (i of items(); track i) {
              <p-skeleton height="9rem" borderRadius="12px" />
            }
          </div>
        }
        @case ('stats') {
          <div class="cc-grid">
            @for (i of items(); track i) {
              <p-skeleton height="5.5rem" borderRadius="12px" />
            }
          </div>
        }
        @case ('table') {
          <p-skeleton height="2.5rem" borderRadius="8px" />
          @for (i of items(); track i) {
            <p-skeleton height="3rem" borderRadius="8px" />
          }
        }
        @case ('form') {
          @for (i of items(); track i) {
            <div class="field">
              <p-skeleton width="8rem" height="1rem" />
              <p-skeleton height="2.6rem" borderRadius="8px" />
            </div>
          }
        }
        @case ('detail') {
          <p-skeleton width="60%" height="2rem" />
          <p-skeleton width="40%" height="1rem" />
          <p-skeleton height="12rem" borderRadius="12px" />
        }
        @default {
          @for (i of items(); track i) {
            <p-skeleton height="4rem" borderRadius="10px" />
          }
        }
      }
    </div>
  `,
  styleUrl: './loading-skeleton.scss',
})
export class LoadingSkeletonComponent {
  readonly kind = input<SkeletonKind>('list');
  readonly count = input(4);

  protected readonly items = computed(() =>
    Array.from({ length: Math.max(1, this.count()) }, (_, i) => i),
  );
}
