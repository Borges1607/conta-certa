create table lesson_assignments (
    id uuid primary key,
    room_id uuid not null references rooms(id),
    lesson_id uuid not null references lessons(id),
    position integer not null,
    status varchar(16) not null,
    available_from timestamptz,
    due_at timestamptz,
    time_limit_minutes integer,
    max_attempts integer,
    question_count integer,
    shuffle_questions boolean not null,
    shuffle_options boolean not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0,
    constraint uk_lesson_assignments_room_lesson unique (room_id, lesson_id),
    constraint uk_lesson_assignments_room_position unique (room_id, position),
    constraint ck_lesson_assignments_position check (position > 0),
    constraint ck_lesson_assignments_status check (status in ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    constraint ck_lesson_assignments_dates check (
        available_from is null or due_at is null or due_at > available_from
    ),
    constraint ck_lesson_assignments_time_limit check (time_limit_minutes is null or time_limit_minutes > 0),
    constraint ck_lesson_assignments_max_attempts check (max_attempts is null or max_attempts > 0),
    constraint ck_lesson_assignments_question_count check (question_count is null or question_count > 0)
);

create index idx_lesson_assignments_lesson on lesson_assignments (lesson_id);
create index idx_lesson_assignments_room_status_available
    on lesson_assignments (room_id, status, available_from);
