import type { Grade } from '../../../core/models/enums';

/** `GET /teacher/dashboard` — §7.1 da spec de integração. */
export interface TeacherDashboard {
  roomCount: number;
  activeRoomCount: number;
  archivedRoomCount: number;
  studentCount: number;
  lessonCount: number;
  publishedLessonCount: number;
  draftLessonCount: number;
  /** Tentativas enviadas nos últimos 7 dias, contadas pela API. */
  recentAttemptCount: number;
  recentRooms: DashboardRoomCard[];
}

export interface DashboardRoomCard {
  id: string;
  name: string;
  grade: Grade;
  studentCount: number;
  archived: boolean;
  lastActivityAt: string | null;
}
