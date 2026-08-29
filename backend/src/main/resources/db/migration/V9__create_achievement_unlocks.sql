create table achievement_unlocks (
    id uuid primary key,
    room_id uuid not null references rooms(id),
    student_id uuid not null references users(id),
    achievement_code varchar(32) not null,
    unlocked_at timestamptz not null,
    constraint uk_achievement_unlocks_room_student_code unique (room_id, student_id, achievement_code),
    constraint ck_achievement_unlocks_code check (achievement_code in (
        'FIRST_PASS', 'PERFECT_SCORE', 'XP_100', 'XP_500', 'XP_1000', 'PASSED_5', 'PASSED_10'
    ))
);

create index idx_achievement_unlocks_room_student
    on achievement_unlocks (room_id, student_id, unlocked_at);
