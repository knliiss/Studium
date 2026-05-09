delete from student_progress_snapshots older
using student_progress_snapshots newer
where older.user_id = newer.user_id
  and older.group_id is null
  and newer.group_id is null
  and (
    older.updated_at < newer.updated_at
    or (older.updated_at = newer.updated_at and older.id < newer.id)
  );

create unique index if not exists uk_student_progress_user_global
    on student_progress_snapshots (user_id)
    where group_id is null;
