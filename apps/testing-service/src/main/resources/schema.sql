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

create table if not exists testing.testing_outbox_events (
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

create index if not exists idx_testing_outbox_status_next_attempt
    on testing.testing_outbox_events (status, next_attempt_at);

create index if not exists idx_testing_outbox_created_at
    on testing.testing_outbox_events (created_at);

create index if not exists idx_testing_outbox_processing_started_at
    on testing.testing_outbox_events (processing_started_at);
