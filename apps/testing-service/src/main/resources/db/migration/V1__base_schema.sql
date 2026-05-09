create table if not exists tests (
    id uuid primary key,
    topic_id uuid not null,
    created_by_user_id uuid,
    title varchar(200) not null,
    order_index integer not null default 0,
    status varchar(20) not null default 'DRAFT',
    max_attempts integer not null default 1,
    max_points integer not null default 100,
    time_limit_minutes integer,
    available_from timestamp(6) with time zone,
    available_until timestamp(6) with time zone,
    show_correct_answers_after_submit boolean not null default false,
    shuffle_questions boolean not null default false,
    shuffle_answers boolean not null default false,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

alter table if exists tests
    add column if not exists max_points integer;

update tests
set max_points = 100
where max_points is null;

alter table if exists tests
    alter column max_points set default 100;

alter table if exists tests
    alter column max_points set not null;

alter table if exists tests
    add column if not exists order_index integer;

update tests
set order_index = 0
where order_index is null;

alter table if exists tests
    alter column order_index set default 0;

alter table if exists tests
    alter column order_index set not null;

create index if not exists idx_tests_topic_id on tests (topic_id);
create index if not exists idx_tests_status on tests (status);
create index if not exists idx_tests_created_by_user_id on tests (created_by_user_id);

create table if not exists questions (
    id uuid primary key,
    test_id uuid not null,
    text varchar(2000) not null,
    type varchar(40) not null default 'SINGLE_CHOICE',
    description varchar(2000),
    points integer not null default 1,
    order_index integer not null default 0,
    required boolean not null default true,
    feedback varchar(2000),
    configuration_json varchar(8000),
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

alter table if exists questions
    add column if not exists type varchar(40);

update questions
set type = 'SINGLE_CHOICE'
where type is null;

alter table if exists questions
    alter column type set default 'SINGLE_CHOICE';

alter table if exists questions
    alter column type set not null;

alter table if exists questions
    add column if not exists points integer;

update questions
set points = 1
where points is null;

alter table if exists questions
    alter column points set default 1;

alter table if exists questions
    alter column points set not null;

alter table if exists questions
    add column if not exists order_index integer;

update questions
set order_index = 0
where order_index is null;

alter table if exists questions
    alter column order_index set default 0;

alter table if exists questions
    alter column order_index set not null;

alter table if exists questions
    add column if not exists required boolean;

update questions
set required = true
where required is null;

alter table if exists questions
    alter column required set default true;

alter table if exists questions
    alter column required set not null;

alter table if exists questions
    add column if not exists configuration_json varchar(8000);

create index if not exists idx_questions_test_id on questions (test_id);

create table if not exists answers (
    id uuid primary key,
    question_id uuid not null,
    text varchar(2000) not null,
    correct boolean not null default false,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create index if not exists idx_answers_question_id on answers (question_id);

create table if not exists test_attempts (
    id uuid primary key,
    test_id uuid not null,
    user_id uuid not null,
    started_at timestamp(6) with time zone not null,
    completed_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone not null
);

create index if not exists idx_test_attempts_test_id on test_attempts (test_id);
create index if not exists idx_test_attempts_user_id on test_attempts (user_id);

create table if not exists test_results (
    id uuid primary key,
    test_id uuid not null,
    user_id uuid not null,
    attempt_id uuid,
    score integer not null,
    auto_score integer not null default 0,
    manual_override_score integer,
    manual_override_reason varchar(1000),
    reviewed_by_user_id uuid,
    reviewed_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create index if not exists idx_test_results_test_id on test_results (test_id);
create index if not exists idx_test_results_user_id on test_results (user_id);

create table if not exists test_group_availability (
    id uuid primary key,
    test_id uuid not null,
    group_id uuid not null,
    visible boolean not null,
    available_from timestamp(6) with time zone,
    available_until timestamp(6) with time zone,
    deadline timestamp(6) with time zone,
    max_attempts integer not null default 1,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create index if not exists idx_test_group_availability_test_id on test_group_availability (test_id);
create index if not exists idx_test_group_availability_group_id on test_group_availability (group_id);
create unique index if not exists uk_test_group_availability_test_id_group_id
    on test_group_availability (test_id, group_id);

create table if not exists testing_outbox_events (
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

create index if not exists idx_testing_outbox_status_next_attempt on testing_outbox_events (status, next_attempt_at);
create index if not exists idx_testing_outbox_created_at on testing_outbox_events (created_at);
create index if not exists idx_testing_outbox_processing_started_at on testing_outbox_events (processing_started_at);
