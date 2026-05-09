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

alter table if exists schedule.academic_semesters
    add column if not exists semester_number integer;

update schedule.academic_semesters
set semester_number = case
    when semester_number is not null then semester_number
    when active = true then 1
    when extract(month from start_date) in (9, 10, 11, 12, 1) then 1
    else 2
end
where semester_number is null;

create index if not exists idx_academic_semesters_semester_number
    on schedule.academic_semesters (semester_number);

create table if not exists schedule.room_capabilities (
    id uuid primary key,
    room_id uuid not null,
    lesson_type varchar(50) not null,
    priority integer not null,
    active boolean not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create unique index if not exists idx_room_capabilities_room_lesson_type
    on schedule.room_capabilities (room_id, lesson_type);

create index if not exists idx_room_capabilities_room_id
    on schedule.room_capabilities (room_id);

create index if not exists idx_room_capabilities_active
    on schedule.room_capabilities (active);
