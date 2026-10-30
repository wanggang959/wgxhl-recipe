#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
db=recipe_catalog_desc_check_20261030
mysql=(docker exec -i test-mysql-1 mysql -uroot -plocal-test-root-only --default-character-set=utf8mb4 -N -B)
"${mysql[@]}" < scripts/catalog/check-descriptions-fixture.sql
"${mysql[@]}" "$db" < deploy/catalog/common-20261030/fill-existing-descriptions.sql
first=$("${mysql[@]}" "$db" -e "SELECT id,ingredient_desc,update_time FROM ingredient ORDER BY id; SELECT id,seasoning_desc,update_time FROM seasoning ORDER BY id")
"${mysql[@]}" "$db" < deploy/catalog/common-20261030/fill-existing-descriptions.sql
second=$("${mysql[@]}" "$db" -e "SELECT id,ingredient_desc,update_time FROM ingredient ORDER BY id; SELECT id,seasoning_desc,update_time FROM seasoning ORDER BY id")
test "$first" = "$second"
for table in ingredient seasoning; do
  filled=$("${mysql[@]}" "$db" -e "SELECT COUNT(*) FROM $table WHERE id IN ('desc-null','desc-space') AND CHAR_LENGTH(TRIM(${table}_desc))>0")
  test "$filled" = 2
  kept=$("${mysql[@]}" "$db" -e "SELECT COUNT(*) FROM $table WHERE id='desc-existing' AND ${table}_desc='keep existing description' AND ${table}_image='keep-existing.webp'")
  test "$kept" = 1
  personal=$("${mysql[@]}" "$db" -e "SELECT COUNT(*) FROM $table WHERE id='desc-personal' AND ${table}_desc IS NULL AND ${table}_image='keep-personal.webp'")
  test "$personal" = 1
  count=$("${mysql[@]}" "$db" -e "SELECT COUNT(*) FROM $table")
  test "$count" = 4
done
echo 'PASS: NULL and whitespace descriptions filled, existing content and personal data preserved, no added rows, rerun unchanged'
