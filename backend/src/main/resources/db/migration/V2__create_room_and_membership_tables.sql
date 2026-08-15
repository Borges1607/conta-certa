create table rooms (
    id uuid primary key,
    teacher_id uuid not null references users(id),
    institution_id uuid not null references institutions(id),
    name varchar(160) not null,
    description varchar(1000),
    grade varchar(24) not null,
    passing_score_percent integer not null default 50,
    join_code varchar(6) not null unique,
    archived_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0,
    constraint ck_rooms_grade check (grade in ('HIGH_SCHOOL_1', 'HIGH_SCHOOL_2', 'HIGH_SCHOOL_3')),
    constraint ck_rooms_passing_score check (passing_score_percent between 0 and 100),
    constraint ck_rooms_join_code check (join_code ~ '^[A-Z0-9]{6}$')
);

create table room_topics (
    room_id uuid not null references rooms(id) on delete cascade,
    position integer not null,
    topic varchar(120) not null,
    primary key (room_id, position)
);

create table room_memberships (
    id uuid primary key,
    room_id uuid not null references rooms(id),
    student_id uuid not null references users(id),
    status varchar(16) not null,
    joined_at timestamptz not null,
    removed_at timestamptz,
    removed_by uuid references users(id),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0,
    constraint uk_room_memberships_room_student unique (room_id, student_id),
    constraint ck_room_memberships_status check (status in ('ACTIVE', 'REMOVED'))
);

create index idx_rooms_teacher_id on rooms (teacher_id);
create index idx_rooms_institution_id on rooms (institution_id);
create index idx_room_memberships_student_status on room_memberships (student_id, status);
create index idx_room_memberships_room_status on room_memberships (room_id, status);
