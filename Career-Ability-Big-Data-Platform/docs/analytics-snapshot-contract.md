# Analytics Snapshot Contract

The reporting module and versioned Open API use
`AnalyticsService.snapshot(AnalyticsSnapshotRequest)` (the `publicSnapshot` alias has identical
semantics). It returns `AnalyticsSnapshotResponse` and is the only supported bridge from an
analysis request to a report data model.

## Request semantics

| Field | Meaning |
| --- | --- |
| `startDate` / `endDate` | Inclusive `publish_date` boundaries. Both may be absent only when the caller explicitly wants the whole public recruitment history. `startDate > endDate` is rejected. |
| `city` | Exact city filter. |
| `position` | Case-insensitive title substring filter. |
| `industry` | Exact company industry filter. |
| `dimensions` | Any subset of `POSITION`, `SALARY`, `EDUCATION`, `SKILL`, `CITY`, `COMPANY`, and `TREND`. An omitted or empty set means all seven dimensions. |

All non-empty filters are translated into a repository specification before records are loaded for
the dimension calculations. The public dataset has no user or college key; callers must not add
such a filter.

## Response semantics

`AnalyticsSnapshotResponse` contains the requested start/end dates unchanged, `PUBLIC_RECRUITMENT`
scope metadata, selected dimensions, an immutable `data` map, `empty`, and `generatedAt`.

- `data.overview` is always present so a report can state the filtered record count.
- Other keys appear only when selected: `positions`, `salary`, `skills`, `education`, `city`,
  `company`, and `trends`.
- An empty result returns stable zero metrics and empty rankings rather than null chart data.
- A report must persist the request fields and selected dimensions with its record, then render the
  returned snapshot. It must not query all positions after showing a date label.
