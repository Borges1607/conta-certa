import { DOCUMENT } from '@angular/common';
import { Injectable, inject, signal } from '@angular/core';

/**
 * Estado da conexão — Parte 2, §3.1.
 *
 * A §9 da spec de integração pede "aviso de tentativa offline ou conexão
 * perdida, **sem inventar tempo local**". Este serviço só reporta o estado; a
 * decisão sobre expiração continua sendo do servidor.
 */
@Injectable({ providedIn: 'root' })
export class ConnectionService {
  private readonly window = inject(DOCUMENT).defaultView;

  readonly isOnline = signal(this.window?.navigator.onLine ?? true);

  constructor() {
    this.window?.addEventListener('online', () => this.isOnline.set(true));
    this.window?.addEventListener('offline', () => this.isOnline.set(false));
  }
}
