# Reminder history retention

Apply `backend/sql/add_reminder_history_retention_indexes.sql` before deploying.
It adds indexes only and is safe to run repeatedly. Back up data before enabling
the cleanup task. No cleanup runs merely because the service starts.

`ReminderHistoryCleanupTask` runs daily at 03:55 Asia/Shanghai. Each table has
up to ten independent batches of at most 1,000 deletions; failures are logged
and do not prevent the other table from being processed.

- Notifications: only read rows with both creation and last-update timestamps
  older than 90 days are eligible. Unread, recently read, and incomplete rows
  remain. There is no absolute cap on unread notifications.
- Send logs: creation, last update (when present), and send time must be older
  than 90 days. Logs for deleted tasks are eligible. For existing tasks, a due
  date and at least one matching household rule are required. Both send time
  and planned target (when present) must precede every current reminder target.
  Legacy rows without a planned target are evaluated by send time.
- Current-cycle evidence is retained even for overdue or completed tasks.
  Unknown dates or missing rules are retained conservatively. Tasks, rules,
  durable delivery claims, and scan checkpoints are never modified by this task.

Changing a task back to a historical cycle after its history was removed is not
a supported way to restore that cycle's delivery evidence. Archive or restore
from a backup if historical delivery records are needed beyond this policy.

Tests: `ReminderHistoryCleanupTaskTest` checks bounded batches and independent
failure handling; `ReminderHistoryCleanupIntegrationTest` checks the policy on
isolated MySQL, including legacy records and retention boundaries.
