# Backend Verification

This document captures the current local verification baseline before frontend development.

## Verification Baseline

- Collection: `docs/postman/Studium.postman_collection.json`
- Environment: `docs/postman/Studium.local.postman_environment.json`
- Local gateway base URL: `http://localhost:8080`
- Expected successful result:
  - `149` total tests
  - `149` passed
  - `0` failed
  - `0` errors

## Local Startup

- Linux/macOS: `./infra/scripts/local/start-local.sh`
- Linux/macOS without Gradle build: `./infra/scripts/local/start-local.sh --skip-build`
- Windows: `infra\scripts\local\start-local.bat`
- Windows without Gradle build: `infra\scripts\local\start-local.bat --skip-build`

The startup flow ensures `.env`, RSA keys, service build artifacts, Docker Compose startup, and demo seed when `DEMO_SEED_ENABLED=true`.

## Verification Flow

1. Start the backend locally with one of the commands above.
2. Wait until the local stack and demo seed finish.
3. Import `docs/postman/Studium.local.postman_environment.json` into Postman.
4. Import `docs/postman/Studium.postman_collection.json` into Postman.
5. Select the `Studium Local` environment.
6. Run `00 Setup / Auth Validation` first or run the full collection in order.
7. Confirm the final runner result is:
   - `149` passed
   - `0` failed
   - `0` errors

## Common Failure Causes

- `401 UNAUTHORIZED`
  Usually means the request used a missing or expired token.
  Rerun `00 Setup / Auth Validation`.
- `403 ACCESS_DENIED`
  Usually means the wrong role token was used or demo seed users are missing/wrong.
  Management and admin flows must use `{{adminAccessToken}}`.
- `502 DOWNSTREAM_ERROR`
  Usually means the gateway cannot reach a downstream service.
  Confirm the full local stack is running. If downstream containers were recreated after `gateway` started, restart `gateway`.
- `null` or missing runtime ids
  Usually means setup or an upstream create request failed earlier in the run.
  Run `Reset Runtime Variables`, rerun setup, and then rerun the dependent folder in order.
- `404 ACTIVE_ACADEMIC_SEMESTER_NOT_FOUND`
  This now indicates schedule foundation bootstrap or demo seed did not run as expected. The local stack should expose an active semester.
- `Database schema is missing ...` or `Database schema is not migrated. Run ... Flyway migrations.`
  Schema guards are validation-only now. They do not apply DDL.
  Start services with Flyway enabled (default) and check logs for successful migration.
  If a local schema is inconsistent, inspect the service schema history:
  `SELECT version, description, success FROM <service_schema>.flyway_schema_history ORDER BY installed_rank;`
  Then apply a corrective Flyway migration (preferred) instead of ad-hoc schema.sql changes.

## Reset Local State

If the local database or seed state must be rebuilt from scratch:

1. Stop the stack and remove named volumes:
   `docker compose -f infra/docker/docker-compose.local.yml down -v`
2. Start the stack again:
   `./infra/scripts/local/start-local.sh`

If a full rebuild is unnecessary and you only want to rerun the verification against the current local data:

- `./infra/scripts/local/start-local.sh --skip-build`

## Deterministic Academic QA Seed

The demo seed now includes deterministic academic management data for manual/browser QA:

- Specialty: `SE` (`Software Engineering / Інженерія програмного забезпечення`)
- Streams: `SE-2Y-STREAM-A` (year 2), `SE-3Y-STREAM-B` (year 3)
- Groups: `SE-21`, `SE-22` (specialty/year-bound), `TEST-00` (unassigned fallback)
- Curriculum plans: year-2 semester-1 plans for `Programming`, `Databases`, `Computer Networks`, plus one year-3 plan
- Group override: `SE-22` disables `Computer Networks`
- Rooms/capabilities:
  - `A-101`: `LECTURE=100`, `PRACTICAL=40`
  - `B-202`: `LABORATORY=100`, `PRACTICAL=60`
  - `C-303`: `LECTURE=60`, `PRACTICAL=80`, `LABORATORY=30`

You can rerun seed safely against an existing local DB without duplicate rows:

- `./infra/scripts/local/seed-demo.sh`

## Flyway Baseline and Repair

- Runtime schema evolution is Flyway-first across DB-owning services.
- `spring.jpa.hibernate.ddl-auto` is `validate`, not `update`.
- Existing local DB volumes are handled by `baseline-on-migrate=true` and additive `V1__base_schema.sql` scripts.
- If migration fails on a dirty local schema:
  1. inspect `flyway_schema_history` in the affected service schema;
  2. add a new additive migration (`V2__...`) to reconcile drift;
  3. rerun the service. Avoid manual table resets unless you intentionally want a full rebuild.

## Rerun Notes

- Use `00 Setup / Auth Validation -> Reset Runtime Variables` before each new Postman runner session.
- If you manually rebuild or recreate downstream service containers, restart `gateway` before rerunning the collection:
  `docker compose -f infra/docker/docker-compose.local.yml restart gateway`
- The Postman collection is gateway-only. No request should point directly to downstream service ports.
