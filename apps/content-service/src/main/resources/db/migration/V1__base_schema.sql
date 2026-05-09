create table if not exists topic_materials (
    id uuid primary key,
    topic_id uuid not null,
    file_id uuid not null,
    title varchar(200) not null,
    description varchar(1000),
    visible boolean not null default true,
    created_by_user_id uuid not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create index if not exists idx_topic_materials_topic_id on topic_materials (topic_id);
create index if not exists idx_topic_materials_visible on topic_materials (visible);

create table if not exists lectures (
    id uuid primary key,
    topic_id uuid not null,
    title varchar(200) not null,
    summary varchar(1000),
    content text not null,
    order_index integer not null,
    status varchar(32) not null,
    created_by_user_id uuid not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create index if not exists idx_lectures_topic_id on lectures (topic_id);
create index if not exists idx_lectures_status on lectures (status);

create table if not exists lecture_materials (
    id uuid primary key,
    lecture_id uuid not null,
    file_id uuid not null,
    title varchar(200) not null,
    description varchar(1000),
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create index if not exists idx_lecture_materials_lecture_id on lecture_materials (lecture_id);
