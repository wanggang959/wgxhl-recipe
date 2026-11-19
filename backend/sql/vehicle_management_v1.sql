CREATE TABLE IF NOT EXISTS vehicle (
  id VARCHAR(36) NOT NULL PRIMARY KEY,
  household_id VARCHAR(36) NOT NULL,
  name VARCHAR(80) NOT NULL,
  vehicle_type VARCHAR(16) NOT NULL,
  model VARCHAR(120) DEFAULT NULL,
  owned_since DATE DEFAULT NULL,
  created_by VARCHAR(36) NOT NULL,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL,
  KEY idx_vehicle_household (household_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS vehicle_fuel_record (
  id VARCHAR(36) NOT NULL PRIMARY KEY,
  household_id VARCHAR(36) NOT NULL,
  vehicle_id VARCHAR(36) NOT NULL,
  occurred_at DATETIME NOT NULL,
  mileage DECIMAL(12,2) NOT NULL,
  unit_price DECIMAL(12,4) DEFAULT NULL,
  liters DECIMAL(12,3) NOT NULL,
  displayed_amount DECIMAL(12,2) DEFAULT NULL,
  paid_amount DECIMAL(12,2) NOT NULL,
  fuel_grade VARCHAR(32) DEFAULT NULL,
  full_tank TINYINT(1) NOT NULL,
  warning_light TINYINT(1) NOT NULL,
  missed_previous TINYINT(1) NOT NULL,
  station VARCHAR(160) DEFAULT NULL,
  remark VARCHAR(1000) DEFAULT NULL,
  import_fingerprint CHAR(64) DEFAULT NULL,
  created_by VARCHAR(36) NOT NULL,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL,
  UNIQUE KEY uk_fuel_import (household_id, vehicle_id, import_fingerprint),
  KEY idx_fuel_vehicle_time (household_id, vehicle_id, occurred_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS vehicle_expense_record (
  id VARCHAR(36) NOT NULL PRIMARY KEY,
  household_id VARCHAR(36) NOT NULL,
  vehicle_id VARCHAR(36) NOT NULL,
  occurred_at DATETIME NOT NULL,
  category VARCHAR(80) NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  remark VARCHAR(1000) DEFAULT NULL,
  import_fingerprint CHAR(64) DEFAULT NULL,
  created_by VARCHAR(36) NOT NULL,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL,
  UNIQUE KEY uk_expense_import (household_id, vehicle_id, import_fingerprint),
  KEY idx_expense_vehicle_time (household_id, vehicle_id, occurred_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
