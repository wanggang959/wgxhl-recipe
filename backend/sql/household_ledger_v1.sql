-- Household ledger records are scoped by household_id. Existing data is not imported.
CREATE TABLE IF NOT EXISTS household_ledger_entry (
  id VARCHAR(36) NOT NULL,
  household_id VARCHAR(36) NOT NULL,
  entry_type VARCHAR(16) NOT NULL,
  category VARCHAR(64) NOT NULL,
  description VARCHAR(200) NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  occurred_on DATE NOT NULL,
  payer_id VARCHAR(36) NOT NULL,
  created_by VARCHAR(36) NOT NULL,
  remark VARCHAR(500) DEFAULT NULL,
  source_type VARCHAR(32) DEFAULT NULL,
  source_id VARCHAR(36) DEFAULT NULL,
  source_cycle VARCHAR(64) DEFAULT NULL,
  source_cycle_label VARCHAR(100) DEFAULT NULL,
  related_expense_id VARCHAR(36) DEFAULT NULL,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ledger_source_cycle (household_id, source_type, source_id, source_cycle),
  UNIQUE KEY uk_ledger_expense (household_id, related_expense_id),
  KEY idx_ledger_household_date (household_id, occurred_on, id),
  KEY idx_ledger_household_category (household_id, category, occurred_on)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS todo_ledger_setting (
  todo_id VARCHAR(36) NOT NULL,
  household_id VARCHAR(36) NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 0,
  category VARCHAR(64) DEFAULT NULL,
  description VARCHAR(200) DEFAULT NULL,
  estimated_amount DECIMAL(12,2) DEFAULT NULL,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL,
  PRIMARY KEY (todo_id),
  KEY idx_todo_ledger_household (household_id, todo_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
