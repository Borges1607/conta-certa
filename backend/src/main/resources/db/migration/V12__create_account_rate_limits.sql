create table account_rate_limits (
    id uuid primary key,
    operation varchar(32) not null,
    subject_hash varchar(64) not null,
    window_started_at timestamptz not null,
    request_count integer not null,
    version bigint not null default 0,
    constraint uk_account_rate_limits_operation_subject unique(operation, subject_hash)
);
