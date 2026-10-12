#!/usr/bin/env bash
set -euo pipefail

# Repair only the precisely identified misplaced service tables.
# Flyway's migrations were recorded in the correct schemas while PgBouncer
# transaction pooling placed the actual tables in "content".
# Existing tables, indexes, sequences, constraints and data are preserved.
# Each service is recovered in one transaction and guarded by exact checks.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"
[[ -f "$ENV_FILE" ]] || { echo "Missing .env" >&2; exit 1; }
set -a
source "$ENV_FILE"
set +a

compose() {
  docker compose --env-file "$ENV_FILE" \
    -f "$ROOT_DIR/infra/docker/docker-compose.local.yml" \
    -f "$ROOT_DIR/infra/docker/docker-compose.vps.yml" "$@"
}
psql_cmd() {
  compose exec -T postgres psql -X -v ON_ERROR_STOP=1 \
    -U "${POSTGRES_USER:-postgres}" -d "${POSTGRES_DB:-postgres}" "$@"
}

echo "Recovering assignment and testing schemas (non-destructive, guarded)..."
psql_cmd <<'SQL'
BEGIN;
DO $migration$
DECLARE
  expected text[] := ARRAY[
    'assignments','assignment_accepted_file_types','assignment_group_availability',
    'submissions','submission_comments','grades','assignment_attachments',
    'submission_attachments','assignment_outbox_events'
  ];
  source_count integer;
  target_count integer;
  history_count integer;
  other_count integer;
  rel text;
BEGIN
  SELECT count(*) INTO source_count FROM pg_tables
    WHERE schemaname='content' AND tablename = ANY(expected);
  SELECT count(*) INTO target_count FROM pg_tables
    WHERE schemaname='assignment' AND tablename = ANY(expected);
  SELECT count(*) INTO other_count FROM pg_tables
    WHERE schemaname NOT IN ('content','assignment') AND tablename = ANY(expected);
  SELECT count(*) INTO history_count FROM assignment.flyway_schema_history
    WHERE version IN ('1','2') AND success=true;
  RAISE NOTICE 'Assignment: content=%/9, assignment=%/9, other=%/9, Flyway=%/2',
    source_count,target_count,other_count,history_count;

  IF target_count=9 AND source_count=0 AND other_count=0 THEN
    RAISE NOTICE 'Assignment schema already repaired.';
  ELSIF source_count=9 AND target_count=0 AND other_count=0 AND history_count=2 THEN
    FOREACH rel IN ARRAY expected LOOP
      EXECUTE format('ALTER TABLE content.%I SET SCHEMA assignment',rel);
    END LOOP;
    RAISE NOTICE 'Recovered 9 assignment tables.';
  ELSE
    RAISE EXCEPTION 'Unexpected assignment schema layout; transaction rolled back.';
  END IF;
END
$migration$;
COMMIT;

BEGIN;
DO $migration$
DECLARE
  expected text[] := ARRAY[
    'tests','questions','answers','test_attempts','test_results',
    'test_group_availability','testing_outbox_events','test_result_questions'
  ];
  source_count integer;
  target_count integer;
  history_count integer;
  other_count integer;
  rel text;
BEGIN
  SELECT count(*) INTO source_count FROM pg_tables
    WHERE schemaname='content' AND tablename = ANY(expected);
  SELECT count(*) INTO target_count FROM pg_tables
    WHERE schemaname='testing' AND tablename = ANY(expected);
  SELECT count(*) INTO other_count FROM pg_tables
    WHERE schemaname NOT IN ('content','testing') AND tablename = ANY(expected);
  SELECT count(*) INTO history_count FROM testing.flyway_schema_history
    WHERE version IN ('1','2') AND success=true;
  RAISE NOTICE 'Testing: content=%/8, testing=%/8, other=%/8, Flyway=%/2',
    source_count,target_count,other_count,history_count;

  IF target_count=8 AND source_count=0 AND other_count=0 THEN
    RAISE NOTICE 'Testing schema already repaired.';
  ELSIF source_count=8 AND target_count=0 AND other_count=0 AND history_count=2 THEN
    FOREACH rel IN ARRAY expected LOOP
      EXECUTE format('ALTER TABLE content.%I SET SCHEMA testing',rel);
    END LOOP;
    RAISE NOTICE 'Recovered 8 testing tables.';
  ELSE
    RAISE EXCEPTION 'Unexpected testing schema layout; transaction rolled back.';
  END IF;
END
$migration$;
COMMIT;
SQL

echo "Schema repair completed. Validating table placement..."
psql_cmd -c "
SELECT schemaname, count(*) AS application_tables
FROM pg_tables
WHERE schemaname IN ('assignment','testing','schedule','content')
  AND tablename <> 'flyway_schema_history'
GROUP BY schemaname ORDER BY schemaname;
"
echo "Do not delete volumes or reset Flyway histories."
