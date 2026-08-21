create table lessons (
    id uuid primary key,
    teacher_id uuid not null references users(id),
    title varchar(160) not null,
    summary varchar(500),
    theory_markdown text not null,
    status varchar(16) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0,
    constraint ck_lessons_status check (status in ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);

create table questions (
    id uuid primary key,
    lesson_id uuid not null references lessons(id) on delete cascade,
    type varchar(24) not null,
    prompt text not null,
    explanation text,
    position integer not null,
    active boolean not null default true,
    correct_boolean boolean,
    correct_numeric_value numeric(19, 6),
    absolute_tolerance numeric(19, 6),
    unit varchar(16),
    decimal_places integer,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0,
    constraint uk_questions_lesson_position unique (lesson_id, position),
    constraint ck_questions_type check (type in ('SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE', 'NUMERIC')),
    constraint ck_questions_unit check (unit is null or unit in ('BRL', 'PERCENT', 'NONE')),
    constraint ck_questions_tolerance check (absolute_tolerance is null or absolute_tolerance >= 0),
    constraint ck_questions_decimal_places check (decimal_places is null or decimal_places >= 0)
);

create table question_options (
    id uuid primary key,
    question_id uuid not null references questions(id) on delete cascade,
    text varchar(500) not null,
    correct boolean not null,
    position integer not null,
    constraint uk_question_options_position unique (question_id, position)
);

create index idx_lessons_teacher_status on lessons (teacher_id, status);
create index idx_questions_lesson_active on questions (lesson_id, active);
