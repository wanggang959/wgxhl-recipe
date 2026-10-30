CREATE DATABASE IF NOT EXISTS recipe_catalog_extra_check_20261030 CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE recipe_catalog_extra_check_20261030;
SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS ingredient LIKE recipe.ingredient;
CREATE TABLE IF NOT EXISTS seasoning LIKE recipe.seasoning;
INSERT INTO ingredient (id,ingredient_name,ingredient_image,ingredient_desc,scope,owner_user_id)
VALUES ('extra-existing','空心菜','keep-existing.webp','keep existing description','platform','platform'),
('extra-unrelated','土豆','keep-unrelated.webp','keep unrelated','platform','platform'),
('extra-personal','空心菜','keep-personal.webp','keep personal','personal','fixture-user')
ON DUPLICATE KEY UPDATE id=id;
INSERT INTO seasoning (id,seasoning_name,seasoning_image,seasoning_desc,scope,owner_user_id)
VALUES ('extra-existing','椒盐','keep-existing.webp','keep existing description','platform','platform'),
('extra-unrelated','食盐','keep-unrelated.webp','keep unrelated','platform','platform'),
('extra-personal','剁椒','keep-personal.webp','keep personal','personal','fixture-user')
ON DUPLICATE KEY UPDATE id=id;
