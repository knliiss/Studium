create table if not exists groups (
    id uuid primary key,
    name varchar(100) not null,
    specialty_id uuid,
    study_year integer,
    stream_id uuid,
    subgroup_mode varchar(50) not null default 'NONE',
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

alter table if exists groups
    add column if not exists specialty_id uuid;

alter table if exists groups
    add column if not exists study_year integer;

alter table if exists groups
    add column if not exists stream_id uuid;

alter table if exists groups
    add column if not exists subgroup_mode varchar(50);

alter table if exists groups
    alter column subgroup_mode set default 'NONE';

update groups
set subgroup_mode = 'NONE'
where subgroup_mode is null;

create index if not exists idx_groups_specialty_id on groups (specialty_id);
create index if not exists idx_groups_study_year on groups (study_year);
create index if not exists idx_groups_stream_id on groups (stream_id);

create table if not exists subjects (
    id uuid primary key,
    name varchar(100) not null,
    group_id uuid,
    description varchar(1000),
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

alter table if exists subjects
    alter column group_id drop not null;

create index if not exists idx_subjects_group_id on subjects (group_id);

create table if not exists topics (
    id uuid primary key,
    subject_id uuid not null,
    title varchar(200) not null,
    order_index integer not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create index if not exists idx_topics_subject_id on topics (subject_id);
create unique index if not exists uk_topics_subject_id_order_index on topics (subject_id, order_index);

create table if not exists group_students (
    id uuid primary key,
    group_id uuid not null,
    user_id uuid not null,
    role varchar(20) not null default 'STUDENT',
    subgroup varchar(20) not null default 'ALL',
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create index if not exists idx_group_students_group_id on group_students (group_id);
create index if not exists idx_group_students_user_id on group_students (user_id);
create unique index if not exists uk_group_students_group_id_user_id on group_students (group_id, user_id);

create table if not exists subject_groups (
    id uuid primary key,
    subject_id uuid not null,
    group_id uuid not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create index if not exists idx_subject_groups_subject_id on subject_groups (subject_id);
create index if not exists idx_subject_groups_group_id on subject_groups (group_id);
create unique index if not exists uk_subject_groups_subject_id_group_id on subject_groups (subject_id, group_id);

create table if not exists subject_teachers (
    id uuid primary key,
    subject_id uuid not null,
    teacher_id uuid not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create index if not exists idx_subject_teachers_subject_id on subject_teachers (subject_id);
create index if not exists idx_subject_teachers_teacher_id on subject_teachers (teacher_id);
create unique index if not exists uk_subject_teachers_subject_id_teacher_id on subject_teachers (subject_id, teacher_id);

create table if not exists specialties (
    id uuid primary key,
    code varchar(50) not null unique,
    name varchar(150) not null,
    description varchar(1000),
    active boolean not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create index if not exists idx_specialties_code on specialties (code);
create index if not exists idx_specialties_active on specialties (active);

create table if not exists streams (
    id uuid primary key,
    name varchar(150) not null,
    specialty_id uuid not null,
    study_year integer not null,
    active boolean not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create index if not exists idx_streams_specialty_id on streams (specialty_id);
create index if not exists idx_streams_study_year on streams (study_year);
create index if not exists idx_streams_active on streams (active);

create table if not exists curriculum_plans (
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
    on curriculum_plans (specialty_id, study_year, semester_number, subject_id);
create index if not exists idx_curriculum_plans_active on curriculum_plans (active);

create table if not exists group_curriculum_overrides (
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
    on group_curriculum_overrides (group_id, subject_id);
create index if not exists idx_group_curriculum_overrides_group_id on group_curriculum_overrides (group_id);
create index if not exists idx_group_curriculum_overrides_subject_id on group_curriculum_overrides (subject_id);

create table if not exists lectures (
    id uuid primary key,
    subject_id uuid not null,
    topic_id uuid not null,
    title varchar(200) not null,
    content varchar(10000),
    status varchar(20) not null,
    order_index integer not null default 0,
    created_by_user_id uuid,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create index if not exists idx_lectures_subject_id on lectures (subject_id);
create index if not exists idx_lectures_topic_id on lectures (topic_id);
create index if not exists idx_lectures_status on lectures (status);
create index if not exists idx_lectures_created_by_user_id on lectures (created_by_user_id);

create table if not exists lecture_attachments (
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

create index if not exists idx_lecture_attachments_lecture_id on lecture_attachments (lecture_id);
create index if not exists idx_lecture_attachments_file_id on lecture_attachments (file_id);
create index if not exists idx_lecture_attachments_uploaded_by_user_id on lecture_attachments (uploaded_by_user_id);
