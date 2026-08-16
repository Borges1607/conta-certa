import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { Button } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';

import { ApiError } from '../../../../core/api/problem-details';
import { NotificationService } from '../../../../core/notifications/notification.service';
import { createSubmitGuard } from '../../../../core/util/submitting';
import { EmptyStateComponent } from '../../../../shared/components/empty-state/empty-state';
import { ErrorStateComponent } from '../../../../shared/components/error-state/error-state';
import { FormFieldComponent } from '../../../../shared/components/form-field/form-field';
import { LoadingSkeletonComponent } from '../../../../shared/components/loading-skeleton/loading-skeleton';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header';
import { SubmitButtonComponent } from '../../../../shared/components/submit-button/submit-button';
import { RoomCardComponent } from '../../components/room-card/room-card';
import { RoomContextStore } from '../../data/room-context.store';
import { StudentRoomService } from '../../data/student-room.service';

/**
 * Minhas salas — Parte 4, §3.
 *
 * Nenhuma ação de sair da sala existe nesta tela: o aluno não pode sair (§6.1
 * da spec de integração). Reingresso após remoção é feito pelo mesmo código, e
 * a API restaura o histórico sozinha.
 */
@Component({
  selector: 'cc-student-rooms-page',
  imports: [
    ReactiveFormsModule,
    Button,
    Dialog,
    InputText,
    Message,
    PageHeaderComponent,
    LoadingSkeletonComponent,
    ErrorStateComponent,
    EmptyStateComponent,
    FormFieldComponent,
    SubmitButtonComponent,
    RoomCardComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './rooms.html',
  styleUrl: './rooms.scss',
})
export class StudentRoomsPage {
  private readonly fb = inject(FormBuilder);
  private readonly rooms = inject(StudentRoomService);
  private readonly router = inject(Router);
  private readonly notify = inject(NotificationService);

  protected readonly context = inject(RoomContextStore);
  protected readonly state = this.context.rooms;

  protected readonly guard = createSubmitGuard();
  protected readonly joinVisible = signal(false);
  protected readonly joinError = signal('');

  protected readonly form = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.minLength(6), Validators.maxLength(6)]],
  });

  protected openJoin(): void {
    this.joinError.set('');
    this.form.reset({ code: '' });
    this.joinVisible.set(true);
  }

  /** O código é sempre maiúsculo; digitar minúsculo não deve falhar. */
  protected onCodeInput(value: string): void {
    this.form.controls.code.setValue(value.toUpperCase().replace(/\s/g, ''), {
      emitEvent: false,
    });
  }

  protected async join(): Promise<void> {
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      return;
    }

    this.joinError.set('');

    await this.guard.run(async () => {
      try {
        const room = await this.rooms.join(this.form.controls.code.value);
        this.joinVisible.set(false);
        this.notify.success('Você entrou na sala', room.name);
        await this.context.reloadRooms();
        await this.router.navigate(['/aluno/salas', room.id]);
      } catch (error) {
        await this.handleJoinFailure(error);
      }
    });
  }

  /** Cada status tem uma explicação diferente — §6.1 da spec e Parte 4, §3. */
  private async handleJoinFailure(error: unknown): Promise<void> {
    if (!(error instanceof ApiError)) {
      this.joinError.set('Algo deu errado. Tente novamente.');
      return;
    }

    switch (error.status) {
      case 404:
        this.joinError.set('Código não encontrado. Confira com seu professor.');
        break;
      case 403:
        this.joinError.set('Esta sala pertence a outra instituição.');
        break;
      case 409:
        // Já participa: não é falha do aluno. Fechamos o diálogo e atualizamos
        // a lista — a sala está lá. Não navegamos porque o corpo do `409` não
        // identifica qual sala é: `instance` é o caminho da requisição, não o
        // recurso. Adivinhar levaria o aluno para a sala errada.
        this.joinVisible.set(false);
        this.notify.info('Você já participa desta sala', 'Ela está na sua lista abaixo.');
        await this.context.reloadRooms();
        break;
      case 410:
        this.joinError.set('Esta sala está arquivada e não aceita novos alunos.');
        break;
      default:
        this.joinError.set(error.detail);
    }
  }
}
