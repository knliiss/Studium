alter table if exists submissions
    add column if not exists status varchar(40) not null default 'SUBMITTED';

alter table if exists submissions
    add column if not exists attempt_number integer not null default 1;

alter table if exists submissions
    add column if not exists submitted_late boolean not null default false;

alter table if exists submissions
    add column if not exists review_comment varchar(2000);

alter table if exists submissions
    add column if not exists reviewed_at timestamp(6) with time zone;

alter table if exists submissions
    add column if not exists reviewed_by_user_id uuid;

alter table if exists submissions
    add column if not exists returned_for_revision_at timestamp(6) with time zone;

alter table if exists submissions
    add column if not exists revision_deadline timestamp(6) with time zone;

with ranked as (
    select id,
           row_number() over (partition by assignment_id, user_id order by submitted_at asc, id asc) as computed_attempt_number
    from submissions
)
update submissions as target
set attempt_number = ranked.computed_attempt_number
from ranked
where target.id = ranked.id;

update submissions as submission
set status = case
    when exists (
        select 1
        from grades grade
        where grade.submission_id = submission.id
    ) then 'GRADED'
    else 'SUBMITTED'
end,
    reviewed_at = case
        when exists (
            select 1
            from grades grade
            where grade.submission_id = submission.id
        ) then (
            select coalesce(grade.updated_at, grade.created_at)
            from grades grade
            where grade.submission_id = submission.id
        )
        else reviewed_at
    end,
    review_comment = case
        when review_comment is not null then review_comment
        when exists (
            select 1
            from grades grade
            where grade.submission_id = submission.id
        ) then (
            select grade.feedback
            from grades grade
            where grade.submission_id = submission.id
        )
        else review_comment
    end;
