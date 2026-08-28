create table videos (
    id uuid primary key,
    teacher_id uuid not null references users(id),
    title varchar(160) not null,
    description varchar(1000),
    category varchar(120),
    url varchar(2048) not null,
    status varchar(16) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0,
    constraint ck_videos_status check (status in ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);

create table stored_files (
    id uuid primary key,
    owner_teacher_id uuid not null references users(id),
    file_name varchar(255) not null,
    content_type varchar(150) not null,
    size_bytes bigint not null,
    sha256 varchar(64) not null,
    content bytea not null,
    created_at timestamptz not null,
    constraint ck_stored_files_size check (size_bytes >= 0)
);

create table materials (
    id uuid primary key,
    teacher_id uuid not null references users(id),
    title varchar(160) not null,
    description varchar(1000),
    category varchar(120),
    kind varchar(24) not null,
    external_url varchar(2048),
    file_id uuid unique references stored_files(id),
    status varchar(16) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0,
    constraint ck_materials_kind check (kind in ('FILE', 'EXTERNAL_LINK')),
    constraint ck_materials_status check (status in ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    constraint ck_materials_source check (
        (kind = 'FILE' and file_id is not null and external_url is null)
        or (kind = 'EXTERNAL_LINK' and external_url is not null and file_id is null)
    )
);

create table media_assignments (
    id uuid primary key,
    room_id uuid not null references rooms(id),
    media_type varchar(16) not null,
    video_id uuid references videos(id),
    material_id uuid references materials(id),
    lesson_assignment_id uuid references lesson_assignments(id),
    position integer not null,
    created_at timestamptz not null,
    version bigint not null default 0,
    constraint ck_media_assignments_type check (media_type in ('VIDEO', 'MATERIAL')),
    constraint ck_media_assignments_position check (position > 0),
    constraint ck_media_assignments_target check (
        (media_type = 'VIDEO' and video_id is not null and material_id is null)
        or (media_type = 'MATERIAL' and material_id is not null and video_id is null)
    )
);

create table media_views (
    id uuid primary key,
    student_id uuid not null references users(id),
    room_id uuid not null references rooms(id),
    media_type varchar(16) not null,
    video_id uuid references videos(id),
    material_id uuid references materials(id),
    first_viewed_at timestamptz not null,
    last_viewed_at timestamptz not null,
    view_count bigint not null,
    constraint ck_media_views_type check (media_type in ('VIDEO', 'MATERIAL')),
    constraint ck_media_views_count check (view_count > 0),
    constraint ck_media_views_target check (
        (media_type = 'VIDEO' and video_id is not null and material_id is null)
        or (media_type = 'MATERIAL' and material_id is not null and video_id is null)
    )
);

create index idx_videos_teacher_status_title on videos(teacher_id, status, title);
create index idx_materials_teacher_status_title on materials(teacher_id, status, title);
create index idx_stored_files_owner on stored_files(owner_teacher_id, created_at);
create unique index uk_media_assignments_room_video
    on media_assignments(room_id, video_id) where video_id is not null;
create unique index uk_media_assignments_room_material
    on media_assignments(room_id, material_id) where material_id is not null;
create unique index uk_media_assignments_room_position on media_assignments(room_id, position);
create index idx_media_assignments_lesson on media_assignments(lesson_assignment_id);
create unique index uk_media_views_student_room_video
    on media_views(student_id, room_id, video_id) where video_id is not null;
create unique index uk_media_views_student_room_material
    on media_views(student_id, room_id, material_id) where material_id is not null;
create index idx_media_views_room_student on media_views(room_id, student_id);
create index idx_media_views_video on media_views(video_id, first_viewed_at) where video_id is not null;
create index idx_media_views_material on media_views(material_id, first_viewed_at) where material_id is not null;
