create schema if not exists content;

create table if not exists content.topic_materials (
    id uuid primary key,
    topic_id uuid not null,
    file_id uuid not null,
    title varchar(200) not null,
    description varchar(1000),
    visible boolean not null default true,
    created_by_user_id uuid not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index if not exists idx_topic_materials_topic_id on content.topic_materials (topic_id);
create index if not exists idx_topic_materials_visible on content.topic_materials (visible);

create table if not exists content.lectures (
    id uuid primary key,
    topic_id uuid not null,
    title varchar(200) not null,
    summary varchar(1000),
    content text not null,
    order_index integer not null,
    status varchar(32) not null,
    created_by_user_id uuid not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index if not exists idx_lectures_topic_id on content.lectures (topic_id);
create index if not exists idx_lectures_status on content.lectures (status);

create table if not exists content.lecture_materials (
    id uuid primary key,
    lecture_id uuid not null,
    file_id uuid not null,
    title varchar(200) not null,
    description varchar(1000),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index if not exists idx_lecture_materials_lecture_id on content.lecture_materials (lecture_id);

