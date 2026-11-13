# List synchronization

startup and hourly scheduled runs fetch only list statuses whose
`sync_status.last_synced_at` is absent, null, or at least one hour old. Fresh
statuses skip both list requests and token refresh; missing authorization leaves
local snapshots untouched. The explicit `sync` command forces all five statuses
to refresh, bypassing the user-list response cache and its offline fallback.
Only complete successful statuses replace their local rows and advance timestamps.
Missing data, malformed entries, and responses with a `paging.next` link retain
that status's snapshot and timestamp; pagination is not yet supported by the list
client. Exceptions abort the run and propagate without marking failed work fresh.
Cleanup applies only to each successful status, including successful empty lists.
The command reports success only when every status refreshes. Otherwise it reports
incomplete synchronization and names the statuses whose local snapshots were
retained, including when authorization is missing or token refresh returns no token.
