import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  input,
  signal,
} from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Button } from 'primeng/button';

import { ApiClient } from '../../../core/api/api-client';
import { ApiError } from '../../../core/api/problem-details';
import { NotificationService } from '../../../core/notifications/notification.service';

/**
 * Abre um arquivo privado — Parte 2, §4.3.
 *
 * A §11 da spec de integração exige que arquivos privados sejam acessados
 * "exclusivamente pelos endpoints autorizados". Uma URL da API em `href` ou
 * `src` não carrega o header `Authorization` e, portanto, não funcionaria — ou
 * pior, funcionaria por outro caminho e vazaria o arquivo.
 *
 * Por isso o fluxo é sempre: baixar o blob autorizado, abrir a URL de objeto e
 * revogá-la depois.
 */
@Component({
  selector: 'cc-secure-file-link',
  imports: [Button],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-button
      [label]="label()"
      [icon]="icon()"
      [outlined]="outlined()"
      [text]="text()"
      [severity]="severity()"
      [loading]="busy()"
      [disabled]="busy()"
      (onClick)="open()"
    />
  `,
})
export class SecureFileLinkComponent {
  private readonly api = inject(ApiClient);
  private readonly notify = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);

  readonly fileId = input.required<string>();
  readonly fileName = input<string>('arquivo');
  readonly label = input('Abrir');
  readonly icon = input('pi pi-external-link');
  readonly outlined = input(true);
  readonly text = input(false);
  readonly severity = input<'primary' | 'secondary'>('primary');
  /** `view` abre em nova aba; `download` salva. */
  readonly mode = input<'view' | 'download'>('view');

  protected readonly busy = signal(false);

  private objectUrls: string[] = [];

  constructor() {
    this.destroyRef.onDestroy(() => {
      for (const url of this.objectUrls) {
        URL.revokeObjectURL(url);
      }
      this.objectUrls = [];
    });
  }

  protected async open(): Promise<void> {
    if (this.busy()) {
      return;
    }

    this.busy.set(true);
    try {
      const blob = await firstValueFrom(this.api.download(`/files/${this.fileId()}/download`));
      const url = URL.createObjectURL(blob);
      this.objectUrls.push(url);

      if (this.mode() === 'download') {
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = this.fileName();
        anchor.click();
      } else {
        window.open(url, '_blank', 'noopener,noreferrer');
      }
    } catch (error) {
      this.notify.error(
        error instanceof ApiError ? error : 'Não foi possível abrir o arquivo.',
        'Arquivo indisponível',
      );
    } finally {
      this.busy.set(false);
    }
  }
}
