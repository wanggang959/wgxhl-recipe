SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='recipe' AND COLUMN_NAME='random_key')=0,
    'ALTER TABLE recipe ADD COLUMN random_key bigint NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
UPDATE recipe SET random_key = FLOOR(RAND() * 1000000000) + 1 WHERE random_key = 0;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='recipe' AND INDEX_NAME='idx_recipe_random_key')=0,
    'ALTER TABLE recipe ADD KEY idx_recipe_random_key (random_key, id)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
