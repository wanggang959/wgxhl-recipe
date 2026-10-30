CREATE DATABASE IF NOT EXISTS recipe_catalog_check_20261030 CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE recipe_catalog_check_20261030;
SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS ingredient LIKE recipe.ingredient;
CREATE TABLE IF NOT EXISTS seasoning LIKE recipe.seasoning;
CREATE TABLE IF NOT EXISTS recipe_seasoning_rel LIKE recipe.recipe_seasoning_rel;
INSERT INTO ingredient (id,ingredient_name,ingredient_image,ingredient_desc,scope,owner_user_id)
VALUES ('fixture-existing-eggplant','茄子','keep-existing.webp','keep existing description','platform','platform'),
('fixture-existing-potato','土豆','keep-potato.webp','keep potato','platform','platform'),
('fixture-personal','南瓜','keep-personal.webp','keep personal','personal','fixture-user')
ON DUPLICATE KEY UPDATE id=id;
INSERT INTO seasoning (id,seasoning_name,seasoning_image,scope,owner_user_id)
VALUES ('fixture-oyster','耗油','keep-oyster.webp','platform','platform') ON DUPLICATE KEY UPDATE id=id;
INSERT INTO recipe_seasoning_rel (id,recipe_id,seasoning_id,seasoning_name)
VALUES ('fixture-relation','fixture-recipe','fixture-oyster','耗油') ON DUPLICATE KEY UPDATE id=id;
