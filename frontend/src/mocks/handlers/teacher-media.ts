import { db, type DbMaterial, type DbUser, type DbVideo } from '../db';
import {
  NO_CONTENT,
  forbidden,
  notFound,
  problem,
  unprocessable,
  versionConflict,
  type MockContext,
  type MockRoute,
} from '../router';
import { matchesSearch, mutate, newId, nowIso, paginate, requireRole } from '../support';

/**
 * Acervo de mídias e publicação em sala — §7.4 da spec de integração.
 *
 * Separado de `teacher.ts` só por tamanho: são as mesmas regras de autoria,
 * `version` e confirmação.
 */

function teacher(context: MockContext): DbUser {
  return requireRole(context, 'TEACHER');
}

function body<T>(context: MockContext): T {
  return (context.body ?? {}) as T;
}

function checkVersion(current: number, sent: unknown): void {
  if (typeof sent !== 'number' || sent !== current) {
    throw versionConflict();
  }
}

function ownVideo(videoId: string, teacherId: string): DbVideo {
  const video = db().videos.find((v) => v.id === videoId);
  if (!video) {
    throw notFound('Videoaula não encontrada.');
  }
  if (video.teacherId !== teacherId) {
    throw forbidden('Esta videoaula não é sua.');
  }
  return video;
}

function ownMaterial(materialId: string, teacherId: string): DbMaterial {
  const material = db().materials.find((m) => m.id === materialId);
  if (!material) {
    throw notFound('Material não encontrado.');
  }
  if (material.teacherId !== teacherId) {
    throw forbidden('Este material não é seu.');
  }
  return material;
}

function toVideo(video: DbVideo) {
  return {
    id: video.id,
    title: video.title,
    description: video.description || null,
    category: video.category || null,
    url: video.url,
    status: video.status,
    createdAt: video.createdAt,
    updatedAt: video.createdAt,
    version: video.version,
  };
}

function toMaterial(material: DbMaterial) {
  const file = material.fileId ? db().files.find((f) => f.id === material.fileId) : undefined;

  return {
    id: material.id,
    title: material.title,
    description: material.description || null,
    category: material.category || null,
    kind: material.kind,
    url: material.url,
    file: file
      ? { id: file.id, fileName: file.name, contentType: file.mimeType, sizeBytes: file.size }
      : null,
    status: material.status,
    createdAt: material.createdAt,
    updatedAt: material.createdAt,
    version: material.version,
  };
}

export const teacherMediaRoutes: MockRoute[] = [
  {
    method: 'GET',
    path: '/teacher/videos',
    handler: (context) => {
      const user = teacher(context);
      const search = context.query.get('search');
      const category = context.query.get('category');

      const videos = db()
        .videos.filter((v) => v.teacherId === user.id && v.status !== 'ARCHIVED')
        .filter((v) => matchesSearch(search, v.title, v.description))
        .filter((v) => (category ? v.category === category : true))
        .map(toVideo);

      return paginate(videos, context);
    },
  },

  {
    method: 'POST',
    path: '/teacher/videos',
    handler: (context) => {
      const user = teacher(context);
      const payload = body<{
        title: string;
        description: string | null;
        category: string | null;
        url: string;
      }>(context);

      if (!/^https?:\/\/.+/.test(payload.url ?? '')) {
        throw unprocessable('Verifique os campos destacados.', [
          { field: 'url', message: 'Informe uma URL válida começando com http ou https.' },
        ]);
      }

      return mutate(() => {
        const video: DbVideo = {
          id: newId('video'),
          teacherId: user.id,
          title: payload.title,
          description: payload.description ?? '',
          url: payload.url,
          category: payload.category ?? '',
          status: 'PUBLISHED',
          version: 1,
          createdAt: nowIso(),
        };
        db().videos.push(video);
        return toVideo(video);
      });
    },
  },

  {
    method: 'GET',
    path: '/teacher/videos/:videoId',
    handler: (context) => toVideo(ownVideo(context.params['videoId'], teacher(context).id)),
  },

  {
    method: 'PATCH',
    path: '/teacher/videos/:videoId',
    handler: (context) => {
      const video = ownVideo(context.params['videoId'], teacher(context).id);
      const payload = body<Record<string, unknown>>(context);
      checkVersion(video.version, payload['version']);

      return mutate(() => {
        if (typeof payload['title'] === 'string') {
          video.title = payload['title'];
        }
        if ('description' in payload) {
          video.description = String(payload['description'] ?? '');
        }
        if ('category' in payload) {
          video.category = String(payload['category'] ?? '');
        }
        if (typeof payload['url'] === 'string') {
          video.url = payload['url'];
        }
        video.version++;
        return toVideo(video);
      });
    },
  },

  {
    method: 'DELETE',
    path: '/teacher/videos/:videoId',
    handler: (context) => {
      const video = ownVideo(context.params['videoId'], teacher(context).id);
      return mutate(() => {
        // Arquivamento lógico: onde já foi publicado continua acessível.
        video.status = 'ARCHIVED';
        video.version++;
        return NO_CONTENT;
      });
    },
  },

  {
    method: 'GET',
    path: '/teacher/materials',
    handler: (context) => {
      const user = teacher(context);
      const search = context.query.get('search');

      const materials = db()
        .materials.filter((m) => m.teacherId === user.id && m.status !== 'ARCHIVED')
        .filter((m) => matchesSearch(search, m.title, m.description))
        .map(toMaterial);

      return paginate(materials, context);
    },
  },

  {
    method: 'POST',
    path: '/teacher/materials',
    handler: (context) => {
      const user = teacher(context);
      const payload = body<{
        title: string;
        description: string | null;
        category: string | null;
        kind: DbMaterial['kind'];
        url: string | null;
        fileId: string | null;
      }>(context);

      if (payload.kind === 'EXTERNAL_LINK' && !/^https?:\/\/.+/.test(payload.url ?? '')) {
        throw unprocessable('Verifique os campos destacados.', [
          { field: 'url', message: 'Informe uma URL válida.' },
        ]);
      }
      if (payload.kind === 'FILE' && !payload.fileId) {
        throw unprocessable('Verifique os campos destacados.', [
          { field: 'fileId', message: 'Envie o arquivo antes de salvar.' },
        ]);
      }

      return mutate(() => {
        const material: DbMaterial = {
          id: newId('material'),
          teacherId: user.id,
          title: payload.title,
          description: payload.description ?? '',
          kind: payload.kind,
          url: payload.url,
          fileId: payload.fileId,
          category: payload.category ?? '',
          status: 'PUBLISHED',
          version: 1,
          createdAt: nowIso(),
        };
        db().materials.push(material);
        return toMaterial(material);
      });
    },
  },

  {
    method: 'GET',
    path: '/teacher/materials/:materialId',
    handler: (context) =>
      toMaterial(ownMaterial(context.params['materialId'], teacher(context).id)),
  },

  {
    method: 'PATCH',
    path: '/teacher/materials/:materialId',
    handler: (context) => {
      const material = ownMaterial(context.params['materialId'], teacher(context).id);
      const payload = body<Record<string, unknown>>(context);
      checkVersion(material.version, payload['version']);

      return mutate(() => {
        if (typeof payload['title'] === 'string') {
          material.title = payload['title'];
        }
        if ('description' in payload) {
          material.description = String(payload['description'] ?? '');
        }
        if ('category' in payload) {
          material.category = String(payload['category'] ?? '');
        }
        if (typeof payload['kind'] === 'string') {
          material.kind = payload['kind'] as DbMaterial['kind'];
        }
        if ('url' in payload) {
          material.url = (payload['url'] as string | null) ?? null;
        }
        if ('fileId' in payload) {
          material.fileId = (payload['fileId'] as string | null) ?? null;
        }
        material.version++;
        return toMaterial(material);
      });
    },
  },

  {
    method: 'DELETE',
    path: '/teacher/materials/:materialId',
    handler: (context) => {
      const material = ownMaterial(context.params['materialId'], teacher(context).id);
      return mutate(() => {
        material.status = 'ARCHIVED';
        material.version++;
        return NO_CONTENT;
      });
    },
  },

  {
    method: 'POST',
    path: '/teacher/materials/files',
    handler: (context) => {
      teacher(context);

      const form = context.request.body;
      if (!(form instanceof FormData)) {
        throw unprocessable('Envio inválido.');
      }

      const file = form.get('file');
      if (!(file instanceof File)) {
        throw unprocessable('Nenhum arquivo recebido.');
      }

      // O limite de 10 MB é do servidor — o cliente também valida antes, mas
      // quem recusa de verdade é aqui (§7.4 da spec).
      if (file.size > 10 * 1024 * 1024) {
        throw problem(413, 'PAYLOAD_TOO_LARGE', 'Arquivo acima do limite de 10 MB.');
      }

      const accepted = [
        'application/pdf',
        'application/vnd.ms-powerpoint',
        'application/vnd.openxmlformats-officedocument.presentationml.presentation',
      ];
      if (file.type && !accepted.includes(file.type)) {
        throw problem(415, 'UNSUPPORTED_MEDIA_TYPE', 'Envie um arquivo PDF, PPT ou PPTX.');
      }

      return mutate(() => {
        const stored = {
          id: newId('file'),
          name: file.name,
          mimeType: file.type || 'application/octet-stream',
          size: file.size,
          content: `Conteúdo simbólico de ${file.name}`,
        };
        db().files.push(stored);
        return {
          id: stored.id,
          fileName: stored.name,
          contentType: stored.mimeType,
          sizeBytes: stored.size,
        };
      });
    },
  },

  {
    method: 'GET',
    path: '/teacher/rooms/:roomId/media-assignments',
    handler: (context) => {
      const user = teacher(context);
      const roomId = context.params['roomId'];
      const room = db().rooms.find((r) => r.id === roomId && r.teacherId === user.id);
      if (!room) {
        throw notFound('Sala não encontrada.');
      }

      return db()
        .mediaAssignments.filter((m) => m.roomId === roomId)
        .map((assignment, index) => {
          const title =
            assignment.mediaType === 'VIDEO'
              ? db().videos.find((v) => v.id === assignment.mediaId)?.title
              : db().materials.find((m) => m.id === assignment.mediaId)?.title;

          const lesson = assignment.lessonId
            ? db().lessons.find((l) => l.id === assignment.lessonId)
            : undefined;

          const lessonAssignment = assignment.lessonId
            ? db().assignments.find(
                (a) => a.roomId === roomId && a.lessonId === assignment.lessonId,
              )
            : undefined;

          return {
            id: assignment.id,
            roomId,
            mediaType: assignment.mediaType,
            mediaId: assignment.mediaId,
            title: title ?? 'Mídia',
            lessonAssignmentId: lessonAssignment?.id ?? null,
            lessonTitle: lesson?.title ?? null,
            position: index + 1,
            createdAt: assignment.publishedAt,
            version: 1,
          };
        });
    },
  },

  {
    method: 'POST',
    path: '/teacher/rooms/:roomId/media-assignments',
    handler: (context) => {
      const user = teacher(context);
      const roomId = context.params['roomId'];
      const room = db().rooms.find((r) => r.id === roomId && r.teacherId === user.id);
      if (!room) {
        throw notFound('Sala não encontrada.');
      }

      const payload = body<{
        mediaType: 'VIDEO' | 'MATERIAL';
        mediaId: string;
        lessonAssignmentId: string | null;
      }>(context);

      const lessonId = payload.lessonAssignmentId
        ? (db().assignments.find((a) => a.id === payload.lessonAssignmentId)?.lessonId ?? null)
        : null;

      return mutate(() => {
        const assignment = {
          id: newId('ma'),
          roomId,
          mediaType: payload.mediaType,
          mediaId: payload.mediaId,
          lessonId,
          publishedAt: nowIso(),
        };
        db().mediaAssignments.push(assignment);

        const title =
          payload.mediaType === 'VIDEO'
            ? db().videos.find((v) => v.id === payload.mediaId)?.title
            : db().materials.find((m) => m.id === payload.mediaId)?.title;

        return {
          id: assignment.id,
          roomId,
          mediaType: assignment.mediaType,
          mediaId: assignment.mediaId,
          title: title ?? 'Mídia',
          lessonAssignmentId: payload.lessonAssignmentId,
          lessonTitle: lessonId
            ? (db().lessons.find((l) => l.id === lessonId)?.title ?? null)
            : null,
          position: db().mediaAssignments.filter((m) => m.roomId === roomId).length,
          createdAt: assignment.publishedAt,
          version: 1,
        };
      });
    },
  },

  {
    method: 'DELETE',
    path: '/teacher/rooms/:roomId/media-assignments/:assignmentId',
    handler: (context) => {
      teacher(context);
      const assignmentId = context.params['assignmentId'];

      return mutate(() => {
        db().mediaAssignments = db().mediaAssignments.filter((m) => m.id !== assignmentId);
        return NO_CONTENT;
      });
    },
  },

  {
    method: 'GET',
    path: '/teacher/media/:mediaType/:mediaId/views',
    handler: (context) => {
      teacher(context);
      const mediaType = context.params['mediaType'] as 'VIDEO' | 'MATERIAL';
      const mediaId = context.params['mediaId'];

      const rows = db()
        .mediaViews.filter((view) => view.mediaType === mediaType && view.mediaId === mediaId)
        .map((view) => {
          const student = db().users.find((u) => u.id === view.studentId);
          return {
            studentId: view.studentId,
            fullName: student?.fullName ?? 'Aluno',
            registrationNumber: student?.registrationNumber ?? null,
            firstViewedAt: view.firstViewedAt,
            lastViewedAt: view.lastViewedAt,
          };
        });

      return { ...paginate(rows, context), totalViewers: rows.length };
    },
  },
];
