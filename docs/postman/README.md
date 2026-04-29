# Postman Verification

## Artifacts

- Collection name: `Studium Backend Verification`
- Collection path: `docs/postman/Studium.postman_collection.json`
- Environment name: `Studium Local`
- Environment path: `docs/postman/Studium.local.postman_environment.json`
- Local base URL: `http://localhost:8080`

## Local Backend Startup

- Linux/macOS: `./infra/scripts/local/start-local.sh`
- Linux/macOS without Gradle build: `./infra/scripts/local/start-local.sh --skip-build`
- Windows: `infra\scripts\local\start-local.bat`
- Windows without Gradle build: `infra\scripts\local\start-local.bat --skip-build`

Demo seed must be enabled so the predefined users, notifications, analytics snapshots, and baseline dashboard data exist.

## Demo Credentials

Login requests use the username-based auth DTO from the backend. The environment keeps email placeholders too, but the collection logs in with usernames.

- `admin.demo` / `DemoPass123!`
- `teacher.alpha` / `DemoPass123!`
- `student.one` / `DemoPass123!`
- `owner` / `ChangeMe123!`

Owner login is optional because many local setups override `AUTH_OWNER_USERNAME` and `AUTH_OWNER_PASSWORD` in `.env`.
If you want to verify the real owner bootstrap account, update `ownerUsername` and `ownerPassword` from your local `.env` and set `verifyOwnerLogin=true`.

## Import Instructions

1. Start the local backend.
2. Import `docs/postman/Studium.local.postman_environment.json`.
3. Import `docs/postman/Studium.postman_collection.json`.
4. Select the `Studium Local` environment in Postman.
5. Run `00 Setup / Auth Validation` first or run the whole collection in the documented order below.

## Runner Order

1. `00 Setup / Auth Validation`
2. `01 Files`
3. `02 Profile`
4. `03 Education`
5. `04 Schedule`
6. `05 Assignments`
7. `06 Testing`
8. `07 Notifications`
9. `08 Analytics`
10. `09 Dashboards`
11. `10 Search`
12. `11 Audit`
13. `12 Security Negative Checks`
14. `13 Optional Cleanup`

The collection is ordered so logins happen first, file-dependent variables are prepared before later flows, and dependent requests appear only after the requests that create their variables.

## Successful Baseline

Current backend verification baseline:

- `149` total tests
- `149` passed
- `0` failed
- `0` errors

This is the expected result for a healthy local stack with demo seed enabled and the collection run in order.

## Why Requests May Be Skipped

The collection uses pre-request guards for all dependent and optional requests.

Requests are skipped when:

- the required token is missing because the matching login request did not succeed yet
- a required id such as `groupId`, `subjectId`, `assignmentId`, `submissionId`, `testId`, `fileId`, `notificationId`, or `auditEventId` is missing
- an optional toggle such as `runOptionalUploadRequests`, `runOptionalCleanup`, or `verifyOwnerLogin` is still `false`

This prevents runner failures such as `/groups/null`, `/subjects/null`, `/assignments/null`, `/files/null`, or `/undefined` URLs.

## Optional Uploads And Cleanup

Optional upload requests are skipped by default because Postman cannot reliably ship a portable local file path inside an imported collection.

- Set `runOptionalUploadRequests=true` if you want to run the upload requests manually in Postman.
- For `Upload Student Attachment File (Optional)`, choose a local `text/plain` file before sending.
- For `Upload Student Avatar File (Optional)`, choose a local image file before sending.
- `Update Avatar From Uploaded AVATAR File (Optional)` only runs when `avatarFileId` exists because the profile service accepts only files with kind `AVATAR`.
- Set `runOptionalCleanup=true` if you want the optional delete requests to run at the end.

The main runner path still works without optional uploads because `01 Files` first tries to reuse seeded student attachment files and only uses uploaded file ids when upload requests actually succeed.

## Active Semester Seed Check

`04 Schedule -> Get Active Semester (Seed Check)` accepts either:

- `200` when the current local data contains an active semester
- `404 ACTIVE_ACADEMIC_SEMESTER_NOT_FOUND` when there is no active semester yet

When the seed check returns `404`, the folder continues by creating a semester and reuses that `semesterId` for the rest of the schedule write/read flow.

## Troubleshooting

- `401` means the request was sent without a valid token or the token is expired.
  Rerun `00 Setup / Auth Validation`.
- `403` means the wrong role token or wrong seed user was used for that request.
  Management writes and admin reads must use `{{adminAccessToken}}`.
- `502` means the gateway cannot reach a downstream service.
  Verify the local stack is up and restart `gateway` if downstream containers were recreated after it started.
- `null` or missing ids mean the setup flow or an upstream create step failed.
  Run `Reset Runtime Variables`, rerun setup, and then rerun the create flow in order.
- `500` on positive admin requests after partial local restarts usually means the gateway still holds stale downstream connections.
  Restart `gateway` and rerun setup.

## Resetting Runtime Variables

Run `00 Setup / Auth Validation -> Reset Runtime Variables` before each runner session.

That request clears tokens, ids, and dependent runtime variables so a failed earlier run does not leave behind stale values.

## Dependency Warning

Dependent requests do not overwrite ids from error responses.
If an upstream create request fails, downstream requests will skip themselves with a clear reason instead of calling invalid `null` or `undefined` URLs.
