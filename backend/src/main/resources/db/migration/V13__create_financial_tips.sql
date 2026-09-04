create table financial_tips (
    id uuid primary key,
    title varchar(160) not null,
    content text not null,
    source_url varchar(2048),
    publication_date date not null,
    active boolean not null default false,
    archived_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0
);

create index idx_financial_tips_publication_active on financial_tips (publication_date, active);
create index idx_financial_tips_archived_at on financial_tips (archived_at);
