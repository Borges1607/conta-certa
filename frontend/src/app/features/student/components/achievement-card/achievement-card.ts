import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { formatDate } from '../../../../core/util/format';
import type { StudentAchievement } from '../../models/achievement';

/**
 * Cartão de conquista — Parte 4, §8.
 *
 * Conquistas bloqueadas aparecem esmaecidas **com o critério visível**: saber o
 * que falta é o que transforma o cartão em objetivo em vez de enfeite.
 *
 * O progresso é mostrado como texto (`3 de 5`) e não como barra: uma barra
 * exigiria dividir um número pelo outro, e isso seria cálculo do frontend sobre
 * dado de gamificação.
 */
@Component({
  selector: 'cc-achievement-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <article class="badge cc-card" [class.badge--locked]="!achievement().unlocked">
      <i class="badge__icon {{ icon() }}" aria-hidden="true"></i>

      <div class="badge__body">
        <strong class="badge__title">{{ achievement().title }}</strong>
        <span class="badge__description cc-small">{{ achievement().description }}</span>

        @if (achievement().unlocked) {
          <span class="badge__meta cc-xs">
            Desbloqueada
            @if (achievement().unlockedAt; as at) {
              em {{ formatted(at) }}
            }
          </span>
        } @else if (progress(); as text) {
          <span class="badge__meta cc-xs">{{ text }}</span>
        }
      </div>
    </article>
  `,
  styleUrl: './achievement-card.scss',
})
export class AchievementCardComponent {
  readonly achievement = input.required<StudentAchievement>();

  protected readonly icon = computed(
    () => this.achievement().icon ?? (this.achievement().unlocked ? 'pi pi-star-fill' : 'pi pi-lock'),
  );

  protected readonly progress = computed(() => {
    const { progressCurrent, progressTarget } = this.achievement();
    return progressCurrent !== null && progressTarget !== null
      ? `${progressCurrent} de ${progressTarget}`
      : null;
  });

  protected formatted(iso: string): string {
    return formatDate(iso);
  }
}
