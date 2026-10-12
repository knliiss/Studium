#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
set -a
source "$ROOT_DIR/.env"
set +a
compose() {
  docker compose --env-file "$ROOT_DIR/.env" \
    -f "$ROOT_DIR/infra/docker/docker-compose.local.yml" \
    -f "$ROOT_DIR/infra/docker/docker-compose.vps.yml" "$@"
}

echo "Audit of expected tables from every service's versioned Flyway migrations:"
python3 - "$ROOT_DIR" <<'PY' | compose exec -T postgres psql -X -v ON_ERROR_STOP=1 \
  -U "${POSTGRES_USER:-postgres}" -d "${POSTGRES_DB:-postgres}" -f -
import pathlib
import re
import sys

root = pathlib.Path(sys.argv[1])
rows = set()
pattern = re.compile(r"\bCREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?(?:[a-z_]+\.)?([a-z_]+)", re.IGNORECASE)
for directory in sorted((root / "apps").glob("*-service")):
    schema = directory.name.removesuffix("-service").replace("-", "_")
    for migration in (directory / "src/main/resources/db/migration").glob("V*__*.sql"):
        sql = migration.read_text(encoding="utf-8")
        for table in pattern.findall(sql):
            rows.add((schema, table.lower()))
if not rows:
    raise SystemExit("No Flyway migration tables found")
values = ",\n  ".join("('%s', '%s')" % pair for pair in sorted(rows))
print("WITH expected(schema_name, table_name) AS (VALUES\n  " + values + "\n)")
print("""SELECT e.schema_name AS expected_schema, e.table_name,
  COALESCE((
    SELECT string_agg(p.schemaname, ', ' ORDER BY p.schemaname)
    FROM pg_catalog.pg_tables p WHERE p.tablename = e.table_name
  ), 'NOT FOUND') AS actual_schema
FROM expected e
WHERE NOT EXISTS (
  SELECT 1 FROM pg_catalog.pg_tables p
  WHERE p.schemaname = e.schema_name AND p.tablename = e.table_name
)
ORDER BY e.schema_name, e.table_name;""")
PY
echo "Empty result set means all expected Flyway tables are in their correct schemas."
