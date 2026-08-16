import { Pipe, PipeTransform } from '@angular/core';

import { formatCnpj } from './cnpj';
import { formatPhone } from './phone';

/**
 * Apresentação de CNPJ e telefone — Parte 6, §4.
 *
 * Existem para que nenhum template invente a própria máscara. O par
 * "normalizado no dado, mascarado na tela" só se sustenta se houver um único
 * lugar que formata e um único lugar que normaliza.
 *
 * Se a Parte 5 vier a precisar dos mesmos formatos, estes pipes sobem para
 * `shared/pipes/` — o documento da parte já prevê isso.
 */

/** 14 dígitos → `12.345.678/0001-90`. */
@Pipe({ name: 'ccCnpj' })
export class CnpjPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    return formatCnpj(value) || '—';
  }
}

/** `+5548999999999` → `(48) 99999-9999`. */
@Pipe({ name: 'ccPhone' })
export class PhonePipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    return formatPhone(value) || '—';
  }
}

export const CC_ADMIN_PIPES = [CnpjPipe, PhonePipe] as const;
