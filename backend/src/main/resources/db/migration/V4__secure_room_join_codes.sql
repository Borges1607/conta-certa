create extension if not exists pgcrypto;

alter table rooms add column join_code_display varchar(6);
alter table rooms add column join_code_hash char(64);

update rooms
set join_code_display = join_code,
    join_code_hash = encode(digest(join_code, 'sha256'), 'hex');

alter table rooms alter column join_code_display set not null;
alter table rooms alter column join_code_hash set not null;
alter table rooms add constraint uk_rooms_join_code_hash unique (join_code_hash);
alter table rooms drop column join_code;
