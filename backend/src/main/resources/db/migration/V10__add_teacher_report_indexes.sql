create index idx_attempts_assignment_submitted_finalized
    on attempts (assignment_id, submitted_at, student_id)
    where status in ('SUBMITTED', 'EXPIRED');

create index idx_attempts_student_submitted_finalized
    on attempts (student_id, submitted_at desc, assignment_id)
    where status in ('SUBMITTED', 'EXPIRED');
