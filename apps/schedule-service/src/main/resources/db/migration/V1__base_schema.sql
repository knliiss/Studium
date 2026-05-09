create table if not exists academic_semesters (
    id uuid primary key,
    name varchar(100) not null,
    start_date date not null,
    end_date date not null,
    week_one_start_date date not null,
    semester_number integer,
    active boolean not null,
    published boolean not null default false,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

alter table if exists academic_semesters
    add column if not exists semester_number integer;

update academic_semesters
set semester_number = case
    when semester_number is not null then semester_number
    when active = true then 1
    when extract(month from start_date) in (9, 10, 11, 12, 1) then 1
    else 2
end
where semester_number is null;

create index if not exists idx_academic_semesters_active on academic_semesters (active);
create index if not exists idx_academic_semesters_published on academic_semesters (published);
create index if not exists idx_academic_semesters_start_date on academic_semesters (start_date);
create index if not exists idx_academic_semesters_end_date on academic_semesters (end_date);
create index if not exists idx_academic_semesters_semester_number on academic_semesters (semester_number);

create table if not exists lesson_slots (
    id uuid primary key,
    number integer not null unique,
    start_time time not null,
    end_time time not null,
    active boolean not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create index if not exists idx_lesson_slots_number on lesson_slots (number);
create index if not exists idx_lesson_slots_active on lesson_slots (active);

create table if not exists rooms (
    id uuid primary key,
    code varchar(50) not null unique,
    building varchar(100) not null,
    floor integer not null,
    capacity integer not null,
    active boolean not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create index if not exists idx_rooms_code on rooms (code);
create index if not exists idx_rooms_active on rooms (active);

create table if not exists room_capabilities (
    id uuid primary key,
    room_id uuid not null,
    lesson_type varchar(50) not null,
    priority integer not null,
    active boolean not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create unique index if not exists idx_room_capabilities_room_lesson_type on room_capabilities (room_id, lesson_type);
create index if not exists idx_room_capabilities_room_id on room_capabilities (room_id);
create index if not exists idx_room_capabilities_active on room_capabilities (active);

create table if not exists schedule_templates (
    id uuid primary key,
    semester_id uuid not null,
    group_id uuid not null,
    subject_id uuid not null,
    teacher_id uuid not null,
    day_of_week varchar(20) not null,
    slot_id uuid not null,
    week_type varchar(10) not null,
    subgroup varchar(20) not null default 'ALL',
    lesson_type varchar(20),
    lesson_format varchar(10) not null,
    room_id uuid,
    online_meeting_url varchar(500),
    notes varchar(2000),
    status varchar(20) not null,
    active boolean not null default true,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create index if not exists idx_schedule_templates_semester_id on schedule_templates (semester_id);
create index if not exists idx_schedule_templates_group_id on schedule_templates (group_id);
create index if not exists idx_schedule_templates_subject_id on schedule_templates (subject_id);
create index if not exists idx_schedule_templates_teacher_id on schedule_templates (teacher_id);
create index if not exists idx_schedule_templates_room_id on schedule_templates (room_id);
create index if not exists idx_schedule_templates_slot_id on schedule_templates (slot_id);
create index if not exists idx_schedule_templates_day_of_week on schedule_templates (day_of_week);
create index if not exists idx_schedule_templates_week_type on schedule_templates (week_type);
create index if not exists idx_schedule_templates_subgroup on schedule_templates (subgroup);
create index if not exists idx_schedule_templates_active on schedule_templates (active);
create index if not exists idx_schedule_templates_status on schedule_templates (status);

create table if not exists schedule_overrides (
    id uuid primary key,
    semester_id uuid not null,
    template_id uuid,
    override_type varchar(10) not null,
    date date not null,
    group_id uuid not null,
    subject_id uuid not null,
    teacher_id uuid not null,
    slot_id uuid not null,
    subgroup varchar(20) not null default 'ALL',
    lesson_type varchar(20),
    lesson_format varchar(10) not null,
    room_id uuid,
    online_meeting_url varchar(500),
    notes varchar(2000),
    created_by_user_id uuid not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create index if not exists idx_schedule_overrides_semester_id on schedule_overrides (semester_id);
create index if not exists idx_schedule_overrides_template_id on schedule_overrides (template_id);
create index if not exists idx_schedule_overrides_date on schedule_overrides (date);
create index if not exists idx_schedule_overrides_group_id on schedule_overrides (group_id);
create index if not exists idx_schedule_overrides_subject_id on schedule_overrides (subject_id);
create index if not exists idx_schedule_overrides_teacher_id on schedule_overrides (teacher_id);
create index if not exists idx_schedule_overrides_room_id on schedule_overrides (room_id);
create index if not exists idx_schedule_overrides_slot_id on schedule_overrides (slot_id);
create index if not exists idx_schedule_overrides_subgroup on schedule_overrides (subgroup);
create index if not exists idx_schedule_overrides_override_type on schedule_overrides (override_type);

create table if not exists teacher_debts (
    id uuid primary key,
    schedule_override_id uuid not null,
    teacher_id uuid not null,
    group_id uuid not null,
    subject_id uuid not null,
    date date not null,
    slot_id uuid not null,
    lesson_type varchar(20),
    subgroup varchar(20) not null default 'ALL',
    reason varchar(2000),
    status varchar(20) not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    resolved_at timestamp(6) with time zone
);

create index if not exists idx_teacher_debts_teacher_id on teacher_debts (teacher_id);
create index if not exists idx_teacher_debts_group_id on teacher_debts (group_id);
create index if not exists idx_teacher_debts_subject_id on teacher_debts (subject_id);
create index if not exists idx_teacher_debts_date on teacher_debts (date);
create index if not exists idx_teacher_debts_slot_id on teacher_debts (slot_id);
create index if not exists idx_teacher_debts_status on teacher_debts (status);

create table if not exists schedule_outbox_events (
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

create index if not exists idx_schedule_outbox_status_next_attempt on schedule_outbox_events (status, next_attempt_at);
create index if not exists idx_schedule_outbox_created_at on schedule_outbox_events (created_at);
create index if not exists idx_schedule_outbox_processing_started_at on schedule_outbox_events (processing_started_at);
