alter table if exists subjects
    alter column group_id drop not null;

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
    uploaded_by_user_id uuid,
    created_at timestamp(6) with time zone not null
);

create index if not exists idx_lecture_attachments_lecture_id
    on education.lecture_attachments (lecture_id);

create index if not exists idx_lecture_attachments_file_id
    on education.lecture_attachments (file_id);

create index if not exists idx_lecture_attachments_uploaded_by_user_id
    on education.lecture_attachments (uploaded_by_user_id);
