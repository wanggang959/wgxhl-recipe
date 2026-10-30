# Reminder delivery recovery

Before deploying, back up the database and apply
`backend/sql/add_reminder_retry_time.sql`. The migration adds only a nullable
`next_retry_time` column and is safe to execute repeatedly.

Only the configured platform administrator can access the recovery endpoint.
The admin UI lists unknown results, interrupted claims older than five minutes,
explicit permanent rejections, and exhausted automatic retries.

- CLOSE records the operator in the send log and closes only that delivery. It
  does not complete or delete the task and does not affect future reminder rules.
- RETRY requires a warning confirmation and a matching row timestamp. It sends
  only the selected channel and recipient, using the current task content.
  Current task/rule dates, recipient membership/status, and preferences are
  checked again. Deleted or changed task cycles cannot be resent.
- A durable PENDING audit row is committed together with the manual claim before
  any network request. Success or failure updates that audit row. Manual failures
  return to review and are never automatically resent.
- Scanning and recovery use the same MySQL named lock. No admin operation takes
  over a live scanner, and stale pages cannot repeat a completed action. Receipt
  at an external service remains different from final delivery to a device.

Provider errors with an explicit request ID are classified conservatively:
rate limits wait at least one minute, and SES TemporaryBlocked waits ten minutes.
Both remain limited to three automatic attempts. Known parameter, template,
sender/authentication, quota, and unsubscribe rejections require operator action.
Uncorrelated errors and network timeouts remain unknown; no blanket 5xx retry is
enabled. Pending retries retain their scan window and log at INFO rather than
being reported as scanner exceptions.

The existing 24-hour catch-up limit and newest-rule-only coalescing still apply.
Recovery is explicit; it does not send all historical missed rules on startup.

Older eligible rules not already recorded as accepted are now recorded once in
the notification history as channel `coalesced`, status `SKIP`. A separate durable
claim prevents repeated catch-up scans from duplicating these explanatory rows.
This audit channel sends nothing and cannot be manually resent. Already accepted
rules are not incorrectly labeled as skipped. Operators can filter by rule
coalescing in the admin notification records.

The admin login link preserves `/admin` as the return destination. Login returns
to that standalone document instead of trying to navigate to a nonexistent SPA
route. Expired admin sessions use the same login URL; other pages retain their
existing internal navigation.
