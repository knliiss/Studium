create table if not exists assignments (
    id uuid primary key,
    topic_id uuid not null,
    created_by_user_id uuid,
    title varchar(200) not null,
    description varchar(2000),
    deadline timestamp(6) with time zone not null,
    order_index integer not null default 0,
    status varchar(20) not null default 'DRAFT',
    allow_late_submissions boolean not null default false,
    max_submissions integer not null default 1,
    allow_resubmit boolean not null default false,
    max_file_size_mb integer,
    max_points integer not null default 100,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

alter table if exists assignments
    add column if not exists max_points integer;

update assignments
set max_points = 100
where max_points is null;

alter table if exists assignments
    alter column max_points set default 100;

alter table if exists assignments
    alter column max_points set not null;

create index if not exists idx_assignments_topic_id on assignments (topic_id);
create index if not exists idx_assignments_status on assignments (status);
create index if not exists idx_assignments_created_by_user_id on assignments (created_by_user_id);

create table if not exists assignment_accepted_file_types (
    assignment_id uuid not null,
    content_type varchar(100) not null,
    primary key (assignment_id, content_type)
);

create index if not exists idx_assignment_accepted_file_types_assignment_id
    on assignment_accepted_file_types (assignment_id);

create table if not exists assignment_group_availability (
    id uuid primary key,
    assignment_id uuid not null,
    group_id uuid not null,
    visible boolean not null,
    available_from timestamp(6) with time zone,
    deadline timestamp(6) with time zone not null,
    allow_late_submissions boolean not null,
    max_submissions integer not null default 1,
    allow_resubmit boolean not null default false,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create index if not exists idx_assignment_group_availability_assignment_id on assignment_group_availability (assignment_id);
create index if not exists idx_assignment_group_availability_group_id on assignment_group_availability (group_id);
create unique index if not exists uk_assignment_group_availability_assignment_id_group_id
    on assignment_group_availability (assignment_id, group_id);

create table if not exists submissions (
    id uuid primary key,
    assignment_id uuid not null,
    user_id uuid not null,
    file_id uuid not null,
    submitted_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create index if not exists idx_submissions_assignment_id on submissions (assignment_id);
create index if not exists idx_submissions_user_id on submissions (user_id);
create index if not exists idx_submissions_file_id on submissions (file_id);

create table if not exists submission_comments (
    id uuid primary key,
    submission_id uuid not null,
    author_user_id uuid not null,
    body varchar(2000) not null,
    deleted boolean not null default false,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create index if not exists idx_submission_comments_submission_id on submission_comments (submission_id);
create index if not exists idx_submission_comments_author_user_id on submission_comments (author_user_id);

create table if not exists grades (
    id uuid primary key,
    submission_id uuid not null unique,
    score integer not null,
    feedback varchar(2000),
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create unique index if not exists uk_grades_submission_id on grades (submission_id);

create table if not exists assignment_attachments (
    id uuid primary key,
    assignment_id uuid not null,
    file_id uuid not null,
    display_name varchar(255),
    original_file_name varchar(500) not null default '',
    content_type varchar(255),
    size_bytes bigint not null default 0,
    uploaded_by_user_id uuid not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

alter table if exists assignment_attachments
    add column if not exists original_file_name varchar(500) not null default '';

alter table if exists assignment_attachments
    add column if not exists content_type varchar(255);

alter table if exists assignment_attachments
    add column if not exists size_bytes bigint not null default 0;

create index if not exists idx_assignment_attachments_assignment_id on assignment_attachments (assignment_id);
create index if not exists idx_assignment_attachments_file_id on assignment_attachments (file_id);

create table if not exists submission_attachments (
    id uuid primary key,
    submission_id uuid not null,
    file_id uuid not null,
    display_name varchar(255),
    original_file_name varchar(500) not null default '',
    content_type varchar(255),
    size_bytes bigint not null default 0,
    uploaded_by_user_id uuid not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

alter table if exists submission_attachments
    add column if not exists original_file_name varchar(500) not null default '';

alter table if exists submission_attachments
    add column if not exists content_type varchar(255);

alter table if exists submission_attachments
    add column if not exists size_bytes bigint not null default 0;

create index if not exists idx_submission_attachments_submission_id on submission_attachments (submission_id);
create index if not exists idx_submission_attachments_file_id on submission_attachments (file_id);

create table if not exists assignment_outbox_events (
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

create index if not exists idx_assignment_outbox_status_next_attempt on assignment_outbox_events (status, next_attempt_at);
create index if not exists idx_assignment_outbox_created_at on assignment_outbox_events (created_at);
create index if not exists idx_assignment_outbox_processing_started_at on assignment_outbox_events (processing_started_at);
