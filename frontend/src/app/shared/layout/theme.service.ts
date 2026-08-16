import { DOCUMENT } from '@angular/common';
import { Injectable, effect, inject, signal } from '@angular/core';

export type ThemeMode = 'light' | 'dark' | 'system';

const STORAGE_KEY = 'cc.theme';
const DARK_CLASS = 'cc-dark';

/**
 * Modo claro e escuro — Parte 2, §2.
 *
 * Três estados, não dois: a preferência explícita do usuário ou o padrão do
 * sistema. Enquanto ele nunca escolheu, `prefers-color-scheme` manda — e muda
 * junto com o sistema operacional durante a sessão.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);
  private readonly window = this.document.defaultView;

  readonly mode = signal<ThemeMode>(this.readStoredMode());
  readonly isDark = signal(false);

  constructor() {
    const media = this.window?.matchMedia('(prefers-color-scheme: dark)');

    effect(() => {
      const mode = this.mode();
      const dark = mode === 'dark' || (mode === 'system' && (media?.matches ?? false));
      this.isDark.set(dark);
      this.document.documentElement.classList.toggle(DARK_CLASS, dark);
    });

    media?.addEventListener('change', () => {
      if (this.mode() === 'system') {
        // Reatribuir o mesmo valor não dispara o effect; forçamos a releitura.
        this.mode.set('system');
        this.isDark.set(media.matches);
        this.document.documentElement.classList.toggle(DARK_CLASS, media.matches);
      }
    });
  }

  set(mode: ThemeMode): void {
    this.mode.set(mode);
    try {
      this.window?.localStorage.setItem(STORAGE_KEY, mode);
    } catch {
      // Sem storage a preferência vale só para esta sessão.
    }
  }

  toggle(): void {
    this.set(this.isDark() ? 'light' : 'dark');
  }

  private readStoredMode(): ThemeMode {
    try {
      const stored = this.window?.localStorage.getItem(STORAGE_KEY);
      return stored === 'light' || stored === 'dark' ? stored : 'system';
    } catch {
      return 'system';
    }
  }
}
