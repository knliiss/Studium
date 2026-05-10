create table if not exists topic_materials (
    id uuid primary key,
    topic_id uuid not null,
    title varchar(200) not null,
    description varchar(5000),
    type varchar(20) not null,
    url varchar(2000),
    file_id uuid,
    original_file_name varchar(500),
    content_type varchar(255),
    size_bytes bigint,
    visible boolean not null default true,
    archived boolean not null default false,
    order_index integer not null default 0,
    created_by_user_id uuid,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create index if not exists idx_topic_materials_topic_id on topic_materials (topic_id);
create index if not exists idx_topic_materials_visible on topic_materials (visible);
create index if not exists idx_topic_materials_archived on topic_materials (archived);
create index if not exists idx_topic_materials_order_index on topic_materials (order_index);
create index if not exists idx_topic_materials_file_id on topic_materials (file_id);
