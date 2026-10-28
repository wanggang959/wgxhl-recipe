-- Safe for both fresh installations and previously migrated databases.
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_favorite' AND INDEX_NAME = 'idx_user_favorite_recipe_user') = 0,
    'ALTER TABLE user_favorite ADD INDEX idx_user_favorite_recipe_user (recipe_id, user_id)', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
