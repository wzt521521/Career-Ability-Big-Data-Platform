# Changelog

All notable changes to this project are documented in this file. The format
follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions
are tagged only after the release acceptance process succeeds.

## [Unreleased]

### Added

- Five-service release topology: MySQL, Redis, Spring Boot backend, Vue/Nginx
  frontend, and the long-running Python ETL worker.
- Versioned database upgrade scripts, report-file persistence, release
  verification tooling, and operational documentation.
- Automated Compose release acceptance covering five-role HTTP flows, backup
  and restore, performance thresholds, OpenAPI export, and real-browser
  screenshots.
- A migration runner with `schema_migrations`, checksum validation, advisory
  locking, app/migrator database user provisioning, and a no-`DROP TABLE`
  release guard.
- CI security gates for high-confidence secret scanning, SBOM generation,
  dependency auditing, license inventory, and release image scanning.
- JWT authentication with refresh-token rotation and revocation, RBAC, API key
  management, owner-bound Open API access, call logging, and per-key limits.
- Seven-dimension analytics, reproducible snapshot boundaries, recommendation
  caching, profile-driven TOP 20 recommendations, and skill-gap analysis.
- Asynchronous, durable PDF report generation with status recovery, ownership
  isolation, preview, and download.

### Changed

- Reports are intentionally **PDF-only** for v1.0. Word and Excel export are
  deferred to a later release rather than being advertised as supported.
- Chinese PDF rendering uses the pinned Noto Sans SC OFL font and embeds an
  extractable subset in generated documents.
- The sample file named `kaggle_jobs_500.csv` is documented as a synthetic
  demonstration dataset; it is not presented as a live Kaggle import.

### Security

- Production startup requires a non-default, sufficiently strong JWT secret.
- Open API requests require both a Bearer token and an API key owned by that
  authenticated user.
- Runtime error responses avoid exposing stack traces and detailed binding
  diagnostics.
- Production Compose keeps MySQL and Redis off host ports by default, requires
  Redis authentication, uses non-root backend/ETL containers, and applies
  request correlation, security headers, and regular business API rate limits.

## Release Notes Policy

- `Unreleased` is the only place for work that has not been tagged.
- The release owner creates `v1.0.0-rc.N` only after all P0 gates pass, then
  records the RC validation result here or in the GitHub release notes.
- `v1.0.0` is entered with its tag date only after the final reproducibility
  smoke test succeeds. Do not retroactively claim a release date.
- Upgrade, backup, rollback, known-limitations, and checksum/SBOM artifacts
  belong to the corresponding GitHub Release.

[Unreleased]: https://github.com/wzt521521/Career-Ability-Big-Data-Platform/compare/v1.0.0...HEAD
