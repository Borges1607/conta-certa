import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { Tag } from 'primeng/tag';

import { ENUM_LABELS, type EnumLabelKind } from '../../../core/models/labels';

type Severity = 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast';

/**
 * Cores por valor de enum. Nenhum estado é comunicado só por cor — o rótulo
 * textual vai junto, sempre (Parte 2, §2).
 */
const SEVERITY_BY_VALUE: Readonly<Record<string, Severity>> = {
  // ContentStatus
  DRAFT: 'warn',
  PUBLISHED: 'success',
  ARCHIVED: 'secondary',
  // AccountStatus
  PENDING: 'warn',
  ACTIVE: 'success',
  INACTIVE: 'secondary',
  // AttemptStatus
  IN_PROGRESS: 'info',
  SUBMITTED: 'success',
  EXPIRED: 'danger',
  // MembershipStatus
  REMOVED: 'danger',
  // MaterialKind
  FILE: 'info',
  EXTERNAL_LINK: 'secondary',
};

/**
 * Etiqueta de status a partir de qualquer enum do contrato — Parte 2, §4.4.
 *
 * O rótulo vem de `core/models/labels.ts`, a única fonte de tradução.
 */
@Component({
  selector: 'cc-status-tag',
  imports: [Tag],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<p-tag [value]="label()" [severity]="severity()" [rounded]="rounded()" />`,
})
export class StatusTagComponent {
  readonly value = input.required<string>();
  readonly kind = input.required<EnumLabelKind>();
  readonly rounded = input(false);

  protected readonly label = computed(() => {
    const map = ENUM_LABELS[this.kind()] as Record<string, string>;
    return map[this.value()] ?? this.value();
  });

  protected readonly severity = computed<Severity>(
    () => SEVERITY_BY_VALUE[this.value()] ?? 'secondary',
  );
}
