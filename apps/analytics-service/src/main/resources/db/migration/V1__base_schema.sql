create table if not exists raw_academic_events (
    id uuid primary key,
    event_id uuid not null unique,
    event_type varchar(150) not null,
    user_id uuid,
    teacher_id uuid,
    group_id uuid,
    subject_id uuid,
    topic_id uuid,
    assignment_id uuid,
    submission_id uuid,
    test_id uuid,
    payload_json text not null,
    occurred_at timestamp(6) with time zone not null,
    created_at timestamp(6) with time zone not null
);

create unique index if not exists uk_raw_academic_events_event_id on raw_academic_events (event_id);
create index if not exists idx_raw_academic_events_event_type on raw_academic_events (event_type);
create index if not exists idx_raw_academic_events_user_id on raw_academic_events (user_id);
create index if not exists idx_raw_academic_events_group_id on raw_academic_events (group_id);
create index if not exists idx_raw_academic_events_subject_id on raw_academic_events (subject_id);
create index if not exists idx_raw_academic_events_teacher_id on raw_academic_events (teacher_id);
create index if not exists idx_raw_academic_events_occurred_at on raw_academic_events (occurred_at);

create table if not exists student_progress_snapshots (
    id uuid primary key,
    user_id uuid not null,
    group_id uuid,
    average_score double precision,
    assignments_created_count integer not null default 0,
    assignments_submitted_count integer not null default 0,
    assignments_late_count integer not null default 0,
    tests_completed_count integer not null default 0,
    missed_deadlines_count integer not null default 0,
    lecture_open_count integer not null default 0,
    topic_open_count integer not null default 0,
    last_activity_at timestamp(6) with time zone,
    activity_score integer not null default 0,
    discipline_score integer not null default 100,
    risk_level varchar(20) not null default 'LOW',
    performance_trend varchar(20) not null default 'UNKNOWN',
    assignment_opened_count integer not null default 0,
    test_started_count integer not null default 0,
    score_total double precision not null default 0,
    score_count integer not null default 0,
    updated_at timestamp(6) with time zone not null
);

create unique index if not exists uk_student_progress_user_group on student_progress_snapshots (user_id, group_id);
create index if not exists idx_student_progress_user_id on student_progress_snapshots (user_id);
create index if not exists idx_student_progress_group_id on student_progress_snapshots (group_id);
create index if not exists idx_student_progress_risk_level on student_progress_snapshots (risk_level);

create table if not exists subject_analytics_snapshots (
    id uuid primary key,
    subject_id uuid not null,
    group_id uuid,
    average_score double precision,
    completion_rate double precision not null default 0,
    late_submission_rate double precision not null default 0,
    missed_deadline_rate double precision not null default 0,
    active_students_count bigint not null default 0,
    at_risk_students_count bigint not null default 0,
    lecture_open_count integer not null default 0,
    test_completion_count integer not null default 0,
    assignment_opened_count integer not null default 0,
    assignments_submitted_count_value integer not null default 0,
    late_submission_count_value integer not null default 0,
    missed_deadline_count_value integer not null default 0,
    test_started_count integer not null default 0,
    score_total double precision not null default 0,
    score_count integer not null default 0,
    updated_at timestamp(6) with time zone not null
);

create unique index if not exists uk_subject_analytics_subject_group on subject_analytics_snapshots (subject_id, group_id);
create index if not exists idx_subject_analytics_subject_id on subject_analytics_snapshots (subject_id);
create index if not exists idx_subject_analytics_group_id on subject_analytics_snapshots (group_id);

create table if not exists teacher_analytics_snapshots (
    id uuid primary key,
    teacher_id uuid not null unique,
    published_assignments_count integer not null default 0,
    published_tests_count integer not null default 0,
    assigned_grades_count integer not null default 0,
    average_review_time_hours double precision,
    average_student_score double precision,
    failing_rate double precision not null default 0,
    score_total double precision not null default 0,
    score_count integer not null default 0,
    failing_score_count integer not null default 0,
    review_time_hours_total double precision not null default 0,
    review_time_sample_count integer not null default 0,
    updated_at timestamp(6) with time zone not null
);

create unique index if not exists idx_teacher_analytics_teacher_id on teacher_analytics_snapshots (teacher_id);
