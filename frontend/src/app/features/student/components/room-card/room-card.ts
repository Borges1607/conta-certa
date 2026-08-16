import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Tag } from 'primeng/tag';

import type { RoomSummary } from '../../../../core/models/room';
import { ProgressBarComponent } from '../../../../shared/components/progress-bar/progress-bar';
import { EnumLabelPipe } from '../../../../shared/pipes/format.pipes';

/**
 * Cartão de uma sala do aluno — Parte 4, §3.
 *
 * O cartão inteiro é um link para `/aluno/salas/{id}`: `roomId` na URL é o que
 * garante o isolamento por sala em recarga e no histórico do navegador.
 *
 * Nenhuma ação de sair da sala existe aqui — o aluno não pode sair (Parte 4,
 * §3), e a ausência é proposital.
 */
@Component({
  selector: 'cc-room-card',
  imports: [RouterLink, Tag, ProgressBarComponent, EnumLabelPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './room-card.html',
  styleUrl: './room-card.scss',
})
export class RoomCardComponent {
  readonly room = input.required<RoomSummary>();
}
