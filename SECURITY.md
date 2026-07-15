# Security Policy

## Supported release line

Security fixes are accepted for the current `1.0.x` release line and for the
active unreleased integration branch. Older snapshots are not supported unless
the repository owner explicitly declares otherwise in a release notice.

## Reporting a vulnerability

Do not open a public issue for a suspected vulnerability, exposed credential,
or reproducible authorization bypass.

1. Use GitHub private vulnerability reporting for this repository when it is
   enabled. If it is not available, contact the repository owner through an
   existing private collaboration channel.
2. Provide the affected revision or image tag, a minimal reproduction, impact,
   prerequisites, and a safe proof of concept. Redact secrets and personal
   data.
3. Do not access data that does not belong to the test account, do not disrupt
   shared services, and do not publish the issue before a fix is available.

The maintainer target is acknowledgement within three business days, initial
triage within seven business days, and a coordinated disclosure plan based on
severity and release feasibility. These are targets, not an authorization to
test systems outside the project environment.

## Security boundaries in v1.0

- Authentication is stateless JWT access-token authentication with refresh
  token rotation and revocation. A disabled account or revoked access token is
  rejected.
- Authorization is role/permission based. Report, profile, recommendation, and
  personal Open API data are scoped to the authenticated owner.
- Open API calls require a Bearer token and an `X-API-Key` whose owner matches
  that token. API key limits are applied per key and calls are logged without
  storing the credential value.
- Regular `/api/**` business endpoints have a bounded per-client rate limiter.
  `/api/open/**` keeps the stricter API-key-specific limiter.
- The backend exposes health probes for orchestration. Detailed error
  information, stack traces, and binding errors are not returned to callers in
  production.
- Production deployment keeps MySQL and Redis on the internal Compose network.
  TLS terminates at a trusted ingress or reverse proxy in front of the frontend
  service; credentials must not be sent over an untrusted clear-text network.
- PDF reports are stored beneath the configured report root and file resolution
  rejects paths outside that root.

## Secrets and configuration

- Copy the runnable project's `.env.example` to `.env`; do not commit `.env`.
- Set a unique `JWT_SECRET`/`SECURITY_JWT_SECRET` of at least 32 random bytes
  for production. The production profile rejects the development fallback.
- Use unique database and Redis credentials per environment. Rotate bootstrap
  administrator and API-key material after a demonstration or incident.
- Store deployment secrets in the platform secret store or protected CI/CD
  variables, not in Compose files, source code, shell history, screenshots, or
  support tickets.

## Dependency and container hygiene

The release process performs dependency, secret, and container-image scans and
records SBOM/checksum artifacts with the GitHub Release. A finding is triaged
by exploitability, reachable code path, data exposure, and available patch.
Critical and high findings in a production-reachable dependency block a
release unless the owner records a time-bounded exception with compensating
controls.

Spring Boot 2.7.x is a maintenance-bound dependency line. v1.0 keeps it for
compatibility; the release owner must track its supported-life status and plan
the Spring Boot 3 / Jakarta migration before the support boundary is reached.

## Scope

This policy covers this repository's code, Compose deployment, generated
reports, CI configuration, and release artifacts. It does not grant permission
to test third-party infrastructure, unrelated accounts, or external datasets.
