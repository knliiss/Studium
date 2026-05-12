create table if not exists test_result_questions (
    id uuid primary key,
    result_id uuid not null,
    question_id uuid not null,
    question_type varchar(40) not null,
    question_text varchar(2000) not null,
    question_order_index integer not null default 0,
    max_points integer not null,
    submitted_value_json text,
    correct_value_json text,
    auto_score integer not null default 0,
    score integer not null default 0,
    review_comment varchar(2000),
    reviewed_by_user_id uuid,
    reviewed_at timestamp(6) with time zone,
    time_spent_seconds integer,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create index if not exists idx_test_result_questions_result_id on test_result_questions (result_id);
create index if not exists idx_test_result_questions_question_id on test_result_questions (question_id);
create unique index if not exists uk_test_result_questions_result_id_question_id
    on test_result_questions (result_id, question_id);
