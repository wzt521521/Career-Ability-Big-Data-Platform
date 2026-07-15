# Data Scope Contract

## Public recruitment records

`JobPosition` and its derived statistics do not contain a student, user, or college owner.
Their real scope is `PUBLIC_RECRUITMENT`, not a simulated `ALL`, `COLLEGE`, or `SELF`
filter. The following endpoints intentionally return the same public recruitment dataset to
any authorized caller, subject to their ordinary feature permission:

- `/api/positions/**`
- `/api/dashboard/**` and `/api/stats/**`
- `/api/open/v1/positions`, `/skills/hot`, `/cities/ranking`, and `/salary/trends`

The only supported filters for this public domain are documented business filters such as
date, city, title, industry, salary, education, sort, and pagination. A college id or user id
must not be accepted and silently ignored.

## User-owned records

| Domain | Effective scope | Enforcement contract |
| --- | --- | --- |
| Profile | `SELF` | Profile APIs derive user id from the authenticated JWT only. |
| Recommendation | `SELF` | Recommendation APIs derive user id from the authenticated JWT only; no user id query parameter exists. |
| Report record/file | `SELF` | List, status, preview, download, and delete resolve ownership server-side. A cross-user API is not part of v1. |
| API Key and API call history | `SELF` | Management and statistics use the authenticated key owner's user id. |

Self-service API key management requires `api:key:manage`. `api:view` is reserved for API
call audit and aggregate statistics, so it is not required merely to create or rotate a caller's
own key.

`ALL` is reserved for explicitly privileged administrative workflows. `COLLEGE` is not
implemented for public recruitment records because there is no source field from which it can
be truthfully derived. A future college-owned student or report workflow must add a persisted
college relation and enforce it in the repository query; it must not reuse a public data query.

## Open API caller binding

Every `/api/open/v1/**` request requires both `Authorization: Bearer <JWT>` and
`X-API-Key`. The API key filter runs after JWT authentication and rejects a missing JWT with
`401` and a key owned by another user with `403`. Controllers may obtain only the non-secret
`OpenApiCaller` through `OpenApiRequestContext.requireCaller(request)`.
