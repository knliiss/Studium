alter table if exists education.subjects
    alter column group_id drop not null;

alter table if exists education.groups
    add column if not exists specialty_id uuid;

alter table if exists education.groups
    add column if not exists study_year integer;

alter table if exists education.groups
    add column if not exists stream_id uuid;

alter table if exists education.groups
    add column if not exists subgroup_mode varchar(50) not null default 'NONE';

update education.groups
set subgroup_mode = 'NONE'
where subgroup_mode is null;

create index if not exists idx_groups_specialty_id
    on education.groups (specialty_id);

create index if not exists idx_groups_study_year
    on education.groups (study_year);

create index if not exists idx_groups_stream_id
    on education.groups (stream_id);

create table if not exists education.specialties (
    id uuid primary key,
    code varchar(50) not null unique,
    name varchar(150) not null,
    description varchar(1000),
    active boolean not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create index if not exists idx_specialties_code
    on education.specialties (code);

create index if not exists idx_specialties_active
    on education.specialties (active);

create table if not exists education.streams (
    id uuid primary key,
    name varchar(150) not null,
    specialty_id uuid not null,
    study_year integer not null,
    active boolean not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create index if not exists idx_streams_specialty_id
    on education.streams (specialty_id);

create index if not exists idx_streams_study_year
    on education.streams (study_year);

create index if not exists idx_streams_active
    on education.streams (active);

create table if not exists education.curriculum_plans (
    id uuid primary key,
    specialty_id uuid not null,
    study_year integer not null,
    semester_number integer not null,
    subject_id uuid not null,
    lecture_count integer not null,
    practice_count integer not null,
    lab_count integer not null,
    supports_stream_lecture boolean not null,
    requires_subgroups_for_labs boolean not null,
    active boolean not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create index if not exists idx_curriculum_plans_specialty_year_semester_subject
    on education.curriculum_plans (specialty_id, study_year, semester_number, subject_id);

create index if not exists idx_curriculum_plans_active
    on education.curriculum_plans (active);

create table if not exists education.group_curriculum_overrides (
    id uuid primary key,
    group_id uuid not null,
    subject_id uuid not null,
    enabled boolean not null,
    lecture_count_override integer,
    practice_count_override integer,
    lab_count_override integer,
    notes varchar(1000),
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create unique index if not exists idx_group_curriculum_overrides_group_subject_unique
    on education.group_curriculum_overrides (group_id, subject_id);

create index if not exists idx_group_curriculum_overrides_group_id
    on education.group_curriculum_overrides (group_id);

create index if not exists idx_group_curriculum_overrides_subject_id
    on education.group_curriculum_overrides (subject_id);

create table if not exists education.lectures (
    id uuid primary key,
    subject_id uuid not null,
    topic_id uuid not null,
    title varchar(200) not null,
    content varchar(10000),
    status varchar(20) not null,
    order_index integer not null,
    created_by_user_id uuid,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create index if not exists idx_lectures_subject_id
    on education.lectures (subject_id);

create index if not exists idx_lectures_topic_id
    on education.lectures (topic_id);

create index if not exists idx_lectures_status
    on education.lectures (status);

create index if not exists idx_lectures_created_by_user_id
    on education.lectures (created_by_user_id);

create table if not exists education.lecture_attachments (
    id uuid primary key,
    lecture_id uuid not null,
    file_id uuid not null,
    display_name varchar(255),
    original_file_name varchar(500) not null default '',
    content_type varchar(255),
    size_bytes bigint not null default 0,
    uploaded_by_user_id uuid,
    created_at timestamp(6) with time zone not null
);

alter table if exists education.lecture_attachments
    add column if not exists original_file_name varchar(500) not null default '';

alter table if exists education.lecture_attachments
    add column if not exists content_type varchar(255);

alter table if exists education.lecture_attachments
    add column if not exists size_bytes bigint not null default 0;

create index if not exists idx_lecture_attachments_lecture_id
    on education.lecture_attachments (lecture_id);

create index if not exists idx_lecture_attachments_file_id
    on education.lecture_attachments (file_id);

create index if not exists idx_lecture_attachments_uploaded_by_user_id
    on education.lecture_attachments (uploaded_by_user_id);
