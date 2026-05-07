alter table if exists assignment.assignments
    add column if not exists max_points integer;

update assignment.assignments
set max_points = 100
where max_points is null;

alter table if exists assignment.assignments
    alter column max_points set default 100;

alter table if exists assignment.assignments
    alter column max_points set not null;

create table if not exists assignment.assignment_outbox_events (
    id uuid primary key,
    topic varchar(255) not null,
    message_key varchar(255) not null,
    event_type varchar(255) not null,
    payload_type varchar(500) not null,
    payload_json text not null,
    status varchar(20) not null,
    attempt_count integer not null,
    next_attempt_at timestamp(6) with time zone not null,
    processing_started_at timestamp(6) with time zone,
    published_at timestamp(6) with time zone,
    last_error varchar(2000),
    published_partition integer,
    published_offset bigint,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create index if not exists idx_assignment_outbox_status_next_attempt
    on assignment.assignment_outbox_events (status, next_attempt_at);

create index if not exists idx_assignment_outbox_created_at
    on assignment.assignment_outbox_events (created_at);

create index if not exists idx_assignment_outbox_processing_started_at
    on assignment.assignment_outbox_events (processing_started_at);

create table if not exists assignment.assignment_attachments (
    id uuid primary key,
    assignment_id uuid not null,
    file_id uuid not null,
    display_name varchar(255),
    uploaded_by_user_id uuid not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create index if not exists idx_assignment_attachments_assignment_id
    on assignment.assignment_attachments (assignment_id);

create index if not exists idx_assignment_attachments_file_id
    on assignment.assignment_attachments (file_id);

create table if not exists assignment.submission_attachments (
    id uuid primary key,
    submission_id uuid not null,
    file_id uuid not null,
    display_name varchar(255),
    uploaded_by_user_id uuid not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create index if not exists idx_submission_attachments_submission_id
    on assignment.submission_attachments (submission_id);

create index if not exists idx_submission_attachments_file_id
    on assignment.submission_attachments (file_id);
