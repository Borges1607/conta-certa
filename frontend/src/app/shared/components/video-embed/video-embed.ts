import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';

/**
 * Player de vídeo externo — Parte 2, §4.3.
 *
 * Vídeos são links externos (§7.4 da spec). Só reconhecemos provedores
 * conhecidos e montamos a URL de incorporação **nós mesmos**, a partir do id
 * extraído. Nada que o professor digitar entra num `iframe` sem passar por
 * essa reconstrução.
 */
@Component({
  selector: 'cc-video-embed',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (embedUrl(); as url) {
      <div class="video">
        <iframe
          [src]="url"
          [title]="title()"
          loading="lazy"
          referrerpolicy="strict-origin-when-cross-origin"
          allow="accelerometer; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
          allowfullscreen
        ></iframe>
      </div>
    } @else {
      <div class="video__fallback">
        <i class="pi pi-video" aria-hidden="true"></i>
        <p>Não foi possível incorporar este vídeo.</p>
        <a [href]="url()" target="_blank" rel="noopener noreferrer nofollow">Abrir em nova aba</a>
      </div>
    }
  `,
  styleUrl: './video-embed.scss',
})
export class VideoEmbedComponent {
  private readonly sanitizer = inject(DomSanitizer);

  readonly url = input.required<string>();
  readonly title = input('Videoaula');

  protected readonly embedUrl = computed<SafeResourceUrl | null>(() => {
    const embed = toEmbedUrl(this.url());
    if (!embed) {
      return null;
    }
    // Único uso autorizado de bypassSecurityTrust* no projeto. O valor NÃO é a
    // URL digitada pelo professor: `toEmbedUrl` só devolve algo quando o host
    // está na lista de permissão e o id casa com o formato do provedor, e
    // então a URL de incorporação é remontada aqui a partir desse id. O que
    // chega ao iframe é sempre uma string construída por nós.
    // eslint-disable-next-line no-restricted-syntax
    return this.sanitizer.bypassSecurityTrustResourceUrl(embed);
  });
}

const YOUTUBE_ID = /^[a-zA-Z0-9_-]{11}$/;
const VIMEO_ID = /^\d+$/;

function toEmbedUrl(raw: string): string | null {
  let parsed: URL;
  try {
    parsed = new URL(raw);
  } catch {
    return null;
  }

  if (parsed.protocol !== 'https:' && parsed.protocol !== 'http:') {
    return null;
  }

  const host = parsed.hostname.replace(/^www\./, '');

  if (host === 'youtube.com' || host === 'm.youtube.com') {
    const id = parsed.searchParams.get('v') ?? pathId(parsed, ['embed', 'shorts', 'live']);
    return id && YOUTUBE_ID.test(id) ? `https://www.youtube-nocookie.com/embed/${id}` : null;
  }

  if (host === 'youtu.be') {
    const id = parsed.pathname.slice(1);
    return YOUTUBE_ID.test(id) ? `https://www.youtube-nocookie.com/embed/${id}` : null;
  }

  if (host === 'vimeo.com' || host === 'player.vimeo.com') {
    const id = parsed.pathname.split('/').filter(Boolean).pop() ?? '';
    return VIMEO_ID.test(id) ? `https://player.vimeo.com/video/${id}` : null;
  }

  return null;
}

function pathId(url: URL, prefixes: readonly string[]): string | null {
  const parts = url.pathname.split('/').filter(Boolean);
  return parts.length >= 2 && prefixes.includes(parts[0]) ? parts[1] : null;
}
