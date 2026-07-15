# Contributing

## Repository layout

This repository has a wrapper directory and a runnable project directory.
Run application commands from:

```text
Career-Ability-Big-Data-Platform/
```

The runnable directory contains `backend/`, `frontend/`, `data-pipeline/`,
`sql/`, `data/`, and `docker-compose.yml`. Release and architecture materials
outside it are part of the repository deliverable as well.

## Before starting

1. Create a focused branch from the current integration or release branch.
2. Read the applicable contract in `docs/` and preserve public API, migration,
   and data-scope compatibility unless the change explicitly revises it.
3. Never add `.env`, access tokens, API keys, database dumps, production logs,
   generated report files, or personal data to Git.
4. Keep generated artifacts out of source changes unless a release task asks
   for a verified, reviewable artifact such as an SBOM.

## Change and review process

- Use conventional commits: `type(scope): concise description`. Typical types
  are `feat`, `fix`, `docs`, `test`, `build`, and `chore`.
- Keep each pull request independently reviewable. Explain behavior changes,
  migrations, configuration changes, rollback impact, and test evidence.
- A repository owner makes the final merge decision under the active branch
  protection rules. Required checks must pass; do not bypass them.
- Do not rewrite another contributor's work or alter unrelated files to make a
  local test pass. Resolve conflicts in a follow-up commit with clear context.

## Local quality gates

From the runnable project directory, use the same clean-install entry points
that CI uses where the required tooling is available:

```powershell
cd backend
.\mvnw.cmd -B verify

cd ..\frontend
npm ci
npm run test:coverage
npm run lint
npm run build

cd ..\data-pipeline
py -3 -m pytest -m "not integration"

cd ..
docker compose config --quiet
git diff --check
```

The Python integration suite is opt-in because it uses dedicated MySQL and
Redis test resources. Configure `PIPELINE_TEST_MYSQL_DATABASE`,
`PIPELINE_TEST_REDIS_DB`, and a unique `PIPELINE_TEST_REDIS_PREFIX`; it must
never target the production database or shared Redis keys.

## Database and deployment changes

- `sql/init.sql` is for a new, empty database volume only. Do not use it to
  upgrade an existing environment.
- Add a numbered, forward-only migration for every schema or seed-data change.
  Migrations must not drop business tables and must have an upgrade and rollback
  note in the pull request.
- Compose, Dockerfile, environment-variable, and volume changes require a
  clean-volume smoke test and an update to `docs/operations.md` when operator
  behavior changes.
- Preserve the five-service boundary. The frontend talks to the backend over
  the internal Docker network; MySQL and Redis are not public application APIs.

## Security and data rules

- Treat all imported CSV/Excel data as untrusted. Keep the current path,
  content-size, and file-type validation boundaries intact.
- Open API endpoints require both `Authorization: Bearer <access-token>` and
  `X-API-Key: <key>`; never log either credential.
- Reports and profile data are user-owned. Any new endpoint must preserve
  current-user ownership checks and RBAC authorization.
- Report suspected vulnerabilities through the private process in
  [`SECURITY.md`](SECURITY.md), not in a public issue.

## Documentation expectations

Update the relevant documentation in the same change when behavior changes:

- `docs/operations.md` for operators and recovery procedures.
- `docs/data-and-third-party-notices.md` for dataset or dependency changes.
- `项目概要浏览/3.架构设计/` for service, flow, storage, or cache changes.
- `CHANGELOG.md` for user-visible behavior planned for the next release.

Documentation must describe what actually runs. Do not leave references to
obsolete paths, unavailable compose files, or unsupported output formats.
