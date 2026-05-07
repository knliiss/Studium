create table if not exists schedule.schedule_outbox_events (
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

create index if not exists idx_schedule_outbox_status_next_attempt
    on schedule.schedule_outbox_events (status, next_attempt_at);

create index if not exists idx_schedule_outbox_created_at
    on schedule.schedule_outbox_events (created_at);

create index if not exists idx_schedule_outbox_processing_started_at
    on schedule.schedule_outbox_events (processing_started_at);
