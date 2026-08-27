create table attempts (
    id uuid primary key,
    assignment_id uuid not null references lesson_assignments(id),
    student_id uuid not null references users(id),
    sequence integer not null,
    status varchar(16) not null,
    started_at timestamptz not null,
    expires_at timestamptz,
    submitted_at timestamptz,
    total_questions integer not null default 0,
    answered_questions integer not null default 0,
    correct_answers integer not null default 0,
    score_percent integer,
    passed boolean,
    stars integer,
    xp_credited integer,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0,
    constraint uk_attempts_assignment_student_sequence unique (assignment_id, student_id, sequence),
    constraint ck_attempts_status check (status in ('IN_PROGRESS', 'SUBMITTED', 'EXPIRED')),
    constraint ck_attempts_sequence check (sequence > 0),
    constraint ck_attempts_totals check (total_questions >= 0 and answered_questions >= 0 and correct_answers >= 0
        and answered_questions <= total_questions and correct_answers <= total_questions),
    constraint ck_attempts_score check (score_percent is null or score_percent between 0 and 100),
    constraint ck_attempts_stars check (stars is null or stars between 0 and 3),
    constraint ck_attempts_xp check (xp_credited is null or xp_credited >= 0)
);

create unique index uk_attempts_one_in_progress on attempts (assignment_id, student_id) where status = 'IN_PROGRESS';
create index idx_attempts_student_assignment on attempts (student_id, assignment_id, sequence desc);
create index idx_attempts_expiration on attempts (expires_at) where status = 'IN_PROGRESS' and expires_at is not null;

create table attempt_question_snapshots (
    id uuid primary key,
    attempt_id uuid not null references attempts(id) on delete cascade,
    question_id uuid not null references questions(id),
    type varchar(24) not null,
    prompt text not null,
    explanation text,
    position integer not null,
    correct_boolean boolean,
    correct_numeric_value numeric(19, 6),
    absolute_tolerance numeric(19, 6),
    unit varchar(16),
    decimal_places integer,
    constraint uk_attempt_question_snapshots_position unique (attempt_id, position),
    constraint uk_attempt_question_snapshots_question unique (attempt_id, question_id),
    constraint ck_attempt_question_snapshots_position check (position > 0),
    constraint ck_attempt_question_snapshots_type check (type in ('SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE', 'NUMERIC')),
    constraint ck_attempt_question_snapshots_unit check (unit is null or unit in ('BRL', 'PERCENT', 'NONE'))
);

create table attempt_option_snapshots (
    id uuid primary key,
    question_snapshot_id uuid not null references attempt_question_snapshots(id) on delete cascade,
    source_option_id uuid not null references question_options(id),
    text varchar(500) not null,
    correct boolean not null,
    position integer not null,
    constraint uk_attempt_option_snapshots_position unique (question_snapshot_id, position),
    constraint ck_attempt_option_snapshots_position check (position > 0)
);

create table attempt_answers (
    id uuid primary key,
    question_snapshot_id uuid not null references attempt_question_snapshots(id) on delete cascade,
    boolean_value boolean,
    numeric_value numeric(19, 6),
    correct boolean not null,
    answered_at timestamptz not null,
    constraint uk_attempt_answers_snapshot unique (question_snapshot_id),
    constraint ck_attempt_answers_payload check (not (boolean_value is not null and numeric_value is not null))
);

create table attempt_answer_selected_options (
    answer_id uuid not null references attempt_answers(id) on delete cascade,
    option_snapshot_id uuid not null references attempt_option_snapshots(id),
    primary key (answer_id, option_snapshot_id)
);

create table extra_attempt_grants (
    id uuid primary key,
    assignment_id uuid not null references lesson_assignments(id),
    student_id uuid not null references users(id),
    teacher_id uuid not null references users(id),
    quantity integer not null,
    created_at timestamptz not null,
    constraint ck_extra_attempt_grants_quantity check (quantity > 0)
);

create table room_student_progress (
    id uuid primary key,
    room_id uuid not null references rooms(id),
    student_id uuid not null references users(id),
    total_xp integer not null default 0,
    level integer not null default 1,
    total_best_stars integer not null default 0,
    completed_assignment_count integer not null default 0,
    passed_assignment_count integer not null default 0,
    last_activity_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0,
    constraint uk_room_student_progress_room_student unique (room_id, student_id),
    constraint ck_room_student_progress_values check (total_xp >= 0 and level >= 1 and total_best_stars >= 0
        and completed_assignment_count >= 0 and passed_assignment_count >= 0)
);

create table idempotency_records (
    id uuid primary key,
    user_id uuid not null references users(id),
    method varchar(16) not null,
    route_scope varchar(500) not null,
    key varchar(255) not null,
    request_hash varchar(64) not null,
    response_status integer not null,
    response_content_type varchar(100) not null,
    response_location varchar(500),
    response_body text not null,
    attempt_id uuid references attempts(id),
    created_at timestamptz not null,
    expires_at timestamptz not null,
    constraint uk_idempotency_records_user_key unique (user_id, key)
);

create index idx_extra_attempt_grants_assignment_student on extra_attempt_grants (assignment_id, student_id);
create index idx_room_student_progress_room on room_student_progress (room_id, total_xp desc);
create index idx_idempotency_records_expiration on idempotency_records (expires_at);
