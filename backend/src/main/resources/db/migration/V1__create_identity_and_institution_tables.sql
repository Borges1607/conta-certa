create table institutions (
    id uuid primary key,
    name varchar(160) not null,
    cnpj varchar(14) not null unique,
    contact_email varchar(254) not null,
    contact_phone varchar(24) not null,
    active boolean not null default true,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0
);

create table users (
    id uuid primary key,
    role varchar(16) not null,
    status varchar(16) not null,
    full_name varchar(160) not null,
    email varchar(254) not null,
    password_hash varchar(255),
    registration_number varchar(80),
    institution_id uuid references institutions(id),
    email_verified_at timestamptz,
    must_change_password boolean not null default false,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0,
    constraint ck_users_role check (role in ('ADMIN', 'TEACHER', 'STUDENT')),
    constraint ck_users_status check (status in ('PENDING', 'ACTIVE', 'INACTIVE')),
    constraint ck_users_institution check (
        (role = 'ADMIN' and institution_id is null and registration_number is null)
        or
        (role in ('TEACHER', 'STUDENT') and institution_id is not null and registration_number is not null)
    )
);

create unique index uk_users_email_lower on users (lower(email));
create index idx_users_institution_id on users (institution_id);
create index idx_users_role_status on users (role, status);
