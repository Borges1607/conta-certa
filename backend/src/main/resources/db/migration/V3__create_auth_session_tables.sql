create table auth_sessions (
    id uuid primary key,
    user_id uuid not null references users(id),
    expires_at timestamptz not null,
    revoked_at timestamptz,
    created_at timestamptz not null,
    last_used_at timestamptz not null,
    version bigint not null default 0,
    constraint ck_auth_sessions_expiry check (expires_at > created_at),
    constraint ck_auth_sessions_last_used check (last_used_at >= created_at),
    constraint ck_auth_sessions_revoked check (revoked_at is null or revoked_at >= created_at)
);

create index idx_auth_sessions_user_revoked on auth_sessions (user_id, revoked_at);
create index idx_auth_sessions_expires_at on auth_sessions (expires_at);

create table refresh_tokens (
    id uuid primary key,
    session_id uuid not null references auth_sessions(id),
    token_hash varchar(64) not null unique,
    issued_at timestamptz not null,
    expires_at timestamptz not null,
    rotated_at timestamptz,
    revoked_at timestamptz,
    replaced_by_id uuid unique references refresh_tokens(id),
    version bigint not null default 0,
    constraint ck_refresh_tokens_hash check (length(token_hash) = 64),
    constraint ck_refresh_tokens_expiry check (expires_at > issued_at),
    constraint ck_refresh_tokens_rotated check (rotated_at is null or rotated_at >= issued_at),
    constraint ck_refresh_tokens_revoked check (revoked_at is null or revoked_at >= issued_at),
    constraint ck_refresh_tokens_replacement check (replaced_by_id is null or replaced_by_id <> id)
);

create index idx_refresh_tokens_session_revoked on refresh_tokens (session_id, revoked_at);
