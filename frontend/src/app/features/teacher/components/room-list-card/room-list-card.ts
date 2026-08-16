import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MenuItem } from 'primeng/api';
import { Button } from 'primeng/button';
import { Menu } from 'primeng/menu';
import { Tag } from 'primeng/tag';

import { EnumLabelPipe } from '../../../../shared/pipes/format.pipes';
import type { TeacherRoomSummary } from '../../models/room';

/**
 * Cartão de sala na lista do professor — Parte 5, §4.
 *
 * Sala arquivada é somente leitura: o menu de ações some por inteiro, em vez de
 * oferecer opções que a API vai recusar.
 */
@Component({
  selector: 'cc-room-list-card',
  imports: [RouterLink, Button, Menu, Tag, EnumLabelPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './room-list-card.html',
  styleUrl: './room-list-card.scss',
})
export class RoomListCardComponent {
  readonly room = input.required<TeacherRoomSummary>();
  readonly busy = input(false);
  /** Só a API sabe se a sala nunca foi usada. */
  readonly deletable = input(false);

  readonly editRequested = output<TeacherRoomSummary>();
  readonly archiveRequested = output<TeacherRoomSummary>();
  readonly duplicateRequested = output<TeacherRoomSummary>();
  readonly regenerateRequested = output<TeacherRoomSummary>();
  readonly deleteRequested = output<TeacherRoomSummary>();

  protected readonly menuItems = computed<MenuItem[]>(() => {
    const room = this.room();

    const items: MenuItem[] = [
      { label: 'Editar', icon: 'pi pi-pencil', command: () => this.editRequested.emit(room) },
      {
        label: 'Duplicar',
        icon: 'pi pi-copy',
        command: () => this.duplicateRequested.emit(room),
      },
      {
        label: 'Novo código de ingresso',
        icon: 'pi pi-refresh',
        command: () => this.regenerateRequested.emit(room),
      },
      { separator: true },
      {
        label: 'Arquivar',
        icon: 'pi pi-inbox',
        command: () => this.archiveRequested.emit(room),
      },
    ];

    if (this.deletable()) {
      items.push({
        label: 'Excluir',
        icon: 'pi pi-trash',
        styleClass: 'menu-item--danger',
        command: () => this.deleteRequested.emit(room),
      });
    }

    return items;
  });
}
