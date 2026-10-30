CREATE DATABASE IF NOT EXISTS recipe_catalog_desc_check_20261030 CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE recipe_catalog_desc_check_20261030;
SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS ingredient LIKE recipe.ingredient;
CREATE TABLE IF NOT EXISTS seasoning LIKE recipe.seasoning;
INSERT INTO ingredient (id,ingredient_name,ingredient_image,ingredient_desc,scope,owner_user_id)
VALUES ('desc-null','上海青','keep-null.webp',NULL,'platform','platform'),
('desc-space','圆青椒','keep-space.webp','   ','platform','platform'),
('desc-existing','土豆','keep-existing.webp','keep existing description','platform','platform'),
('desc-personal','上海青','keep-personal.webp',NULL,'personal','fixture-user')
ON DUPLICATE KEY UPDATE id=id;
INSERT INTO seasoning (id,seasoning_name,seasoning_image,seasoning_desc,scope,owner_user_id)
VALUES ('desc-null','鸡精','keep-null.webp',NULL,'platform','platform'),
('desc-space','蚝油','keep-space.webp','   ','platform','platform'),
('desc-existing','生抽','keep-existing.webp','keep existing description','platform','platform'),
('desc-personal','鸡精','keep-personal.webp',NULL,'personal','fixture-user')
ON DUPLICATE KEY UPDATE id=id;
