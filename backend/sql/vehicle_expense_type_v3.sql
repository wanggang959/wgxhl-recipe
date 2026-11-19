CREATE TABLE IF NOT EXISTS vehicle_expense_type (
  household_id VARCHAR(36) NOT NULL,
  type_key VARCHAR(36) NOT NULL,
  name VARCHAR(80) NOT NULL,
  color CHAR(7) NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (household_id, type_key),
  UNIQUE KEY uk_vehicle_expense_type_name (household_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
