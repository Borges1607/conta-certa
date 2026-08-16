import { ChangeDetectionStrategy, Component, effect, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { Button } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { InputText } from 'primeng/inputtext';
import { SelectButton } from 'primeng/selectbutton';
import { TableModule } from 'primeng/table';
import { Textarea } from 'primeng/textarea';

import { ApiError } from '../../../../core/api/problem-details';
import type { MaterialKind } from '../../../../core/models/enums';
import type { PageQuery } from '../../../../core/models/page';
import { NotificationService } from '../../../../core/notifications/notification.service';
import { createSubmitGuard } from '../../../../core/util/submitting';
import { EmptyStateComponent } from '../../../../shared/components/empty-state/empty-state';
import { ErrorStateComponent } from '../../../../shared/components/error-state/error-state';
import {
  FileUploadComponent,
  MATERIAL_ACCEPT,
} from '../../../../shared/components/file-upload/file-upload';
import { FormFieldComponent } from '../../../../shared/components/form-field/form-field';
import { LoadingSkeletonComponent } from '../../../../shared/components/loading-skeleton/loading-skeleton';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header';
import { SecureFileLinkComponent } from '../../../../shared/components/secure-file-link/secure-file-link';
import { SubmitButtonComponent } from '../../../../shared/components/submit-button/submit-button';
import {
  applyFieldErrors,
  autoClearServerErrors,
} from '../../../../shared/forms/apply-field-errors';
import { createPageState } from '../../../../shared/forms/page-state';
import { FileSizePipe } from '../../../../shared/pipes/format.pipes';
import { VersionConflictNoticeComponent } from '../../components/version-conflict-notice/version-conflict-notice';
import { MaterialService } from '../../data/material.service';
import type { MaterialFile, TeacherMaterial } from '../../models/media';

/**
 * Acervo de materiais — Parte 5, §8.
 *
 * O upload usa `cc-file-upload`, que valida tipo e tamanho **antes** de enviar
 * e mostra progresso real. `413` e `415` do servidor mantêm o arquivo
 * selecionado, para o professor corrigir sem reescolher (§9 da spec).
 */
@Component({
  selector: 'cc-teacher-materials-page',
  imports: [
    ReactiveFormsModule,
    FormsModule,
    Button,
    Dialog,
    InputText,
    Textarea,
    SelectButton,
    TableModule,
    PageHeaderComponent,
    LoadingSkeletonComponent,
    ErrorStateComponent,
    EmptyStateComponent,
    FormFieldComponent,
    SubmitButtonComponent,
    FileUploadComponent,
    SecureFileLinkComponent,
    VersionConflictNoticeComponent,
    FileSizePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './materials.html',
  styleUrl: './materials.scss',
})
export class TeacherMaterialsPage {
  private readonly fb = inject(FormBuilder);
  private readonly materials = inject(MaterialService);
  private readonly notify = inject(NotificationService);

  protected readonly accept = MATERIAL_ACCEPT;

  protected readonly query = signal<PageQuery>({ page: 0, size: 50 });
  protected readonly state = createPageState(() => this.materials.list(this.query()));

  protected readonly dialogVisible = signal(false);
  protected readonly editing = signal<TeacherMaterial | null>(null);
  protected readonly conflict = signal(false);
  protected readonly busyId = signal<string | null>(null);

  protected readonly kind = signal<MaterialKind>('EXTERNAL_LINK');
  protected readonly uploadedFile = signal<MaterialFile | null>(null);
  protected readonly uploading = signal(false);
  protected readonly uploadPercent = signal<number | null>(null);

  protected readonly guard = createSubmitGuard();

  protected readonly kindOptions = [
    { value: 'EXTERNAL_LINK' as const, label: 'Link externo' },
    { value: 'FILE' as const, label: 'Arquivo' },
  ];

  protected readonly form = this.fb.nonNullable.group({
    title: ['', [Validators.required, Validators.minLength(3)]],
    description: [''],
    category: [''],
    url: [''],
  });

  constructor() {
    autoClearServerErrors(this.form);

    effect(() => {
      this.query();
      void this.state.load();
    });
  }

  protected openCreate(): void {
    this.editing.set(null);
    this.conflict.set(false);
    this.kind.set('EXTERNAL_LINK');
    this.uploadedFile.set(null);
    this.form.reset({ title: '', description: '', category: '', url: '' });
    this.dialogVisible.set(true);
  }

  protected openEdit(material: TeacherMaterial): void {
    this.editing.set(material);
    this.conflict.set(false);
    this.kind.set(material.kind);
    this.uploadedFile.set(material.file);
    this.form.reset({
      title: material.title,
      description: material.description ?? '',
      category: material.category ?? '',
      url: material.url ?? '',
    });
    this.dialogVisible.set(true);
  }

  /** Envia o arquivo e guarda o id devolvido para salvar junto do material. */
  protected onFileSelected(file: File): void {
    this.uploading.set(true);
    this.uploadPercent.set(0);

    this.materials.uploadFile(file).subscribe({
      next: (event) => {
        if (event.kind === 'progress') {
          this.uploadPercent.set(event.percent);
        } else {
          this.uploadedFile.set(event.body);
          this.uploading.set(false);
          this.notify.success('Arquivo enviado');
        }
      },
      error: (error: unknown) => {
        this.uploading.set(false);
        this.handleUploadFailure(error);
      },
    });
  }

  protected async save(): Promise<void> {
    this.form.markAllAsTouched();

    const isFile = this.kind() === 'FILE';
    if (isFile && !this.uploadedFile()) {
      this.notify.warn('Envie o arquivo', 'Escolha um PDF, PPT ou PPTX antes de salvar.');
      return;
    }
    if (!isFile && !this.form.controls.url.value.trim()) {
      this.form.controls.url.setErrors({ required: true });
      return;
    }
    if (this.form.invalid) {
      return;
    }

    const value = this.form.getRawValue();
    const current = this.editing();

    const payload = {
      title: value.title,
      description: value.description || null,
      category: value.category || null,
      kind: this.kind(),
      url: isFile ? null : value.url,
      fileId: isFile ? (this.uploadedFile()?.id ?? null) : null,
    };

    await this.guard.run(async () => {
      try {
        if (current) {
          await this.materials.update(current.id, { version: current.version, ...payload });
        } else {
          await this.materials.create(payload);
        }
        this.notify.success(current ? 'Material atualizado' : 'Material criado');
        this.dialogVisible.set(false);
        await this.state.refresh();
      } catch (error) {
        this.handleFailure(error);
      }
    });
  }

  protected async remove(material: TeacherMaterial): Promise<void> {
    const confirmed = await this.notify.destructive({
      header: `Arquivar "${material.title}"?`,
      message:
        'O material sai do acervo ativo. Onde ele já estiver publicado, continua acessível aos alunos até você retirá-lo da sala.',
      acceptLabel: 'Arquivar',
    });

    if (!confirmed) {
      return;
    }

    await this.guard.run(async () => {
      this.busyId.set(material.id);
      try {
        await this.materials.remove(material.id);
        this.notify.success('Material arquivado');
        await this.state.refresh();
      } catch (error) {
        this.notify.error(error instanceof ApiError ? error : 'Não foi possível arquivar.');
      } finally {
        this.busyId.set(null);
      }
    });
  }

  protected async reload(): Promise<void> {
    await this.state.refresh();
    this.conflict.set(false);
    this.dialogVisible.set(false);
    this.notify.info('Lista recarregada', 'Abra o material de novo para editar a versão atual.');
  }

  /** `413` e `415` merecem texto próprio — §9 da spec de integração. */
  private handleUploadFailure(error: unknown): void {
    if (!(error instanceof ApiError)) {
      this.notify.error('Não foi possível enviar o arquivo.');
      return;
    }

    if (error.status === 413) {
      this.notify.error('Arquivo grande demais. O limite é 10 MB.', 'Envio recusado');
      return;
    }
    if (error.status === 415) {
      this.notify.error('Tipo não aceito. Envie PDF, PPT ou PPTX.', 'Envio recusado');
      return;
    }

    this.notify.error(error, 'Envio recusado');
  }

  private handleFailure(error: unknown): void {
    if (!(error instanceof ApiError)) {
      this.notify.error('Não foi possível salvar o material.');
      return;
    }

    if (error.isVersionConflict) {
      this.conflict.set(true);
      return;
    }

    applyFieldErrors(this.form, error);
    if (error.fieldErrors.length === 0) {
      this.notify.error(error);
    }
  }
}
