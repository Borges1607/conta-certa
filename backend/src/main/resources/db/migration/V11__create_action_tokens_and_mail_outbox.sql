create table action_tokens (
    id uuid primary key,
    user_id uuid not null references users(id),
    type varchar(32) not null,
    token_hash varchar(64) not null unique,
    expires_at timestamptz not null,
    consumed_at timestamptz,
    invalidated_at timestamptz,
    created_at timestamptz not null
);

create index idx_action_tokens_user_type on action_tokens(user_id, type);

create table mail_outbox (
    id uuid primary key,
    type varchar(32) not null,
    recipient varchar(254) not null,
    subject varchar(200) not null,
    text_body text not null,
    html_body text not null,
    status varchar(16) not null,
    attempt_count integer not null default 0,
    next_attempt_at timestamptz not null,
    claimed_at timestamptz,
    sent_at timestamptz,
    last_error varchar(500),
    created_at timestamptz not null,
    version bigint not null default 0
);

create index idx_mail_outbox_due on mail_outbox(status, next_attempt_at);
