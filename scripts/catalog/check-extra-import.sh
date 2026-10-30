#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
db=recipe_catalog_extra_check_20261030
mysql=(docker exec -i test-mysql-1 mysql -uroot -plocal-test-root-only --default-character-set=utf8mb4 -N -B)
"${mysql[@]}" < scripts/catalog/check-extra-fixture.sql
"${mysql[@]}" "$db" < deploy/catalog/common-extra-20261030/import.sql
first=$("${mysql[@]}" "$db" -e "SELECT (SELECT COUNT(*) FROM ingredient),(SELECT COUNT(*) FROM seasoning)")
"${mysql[@]}" "$db" < deploy/catalog/common-extra-20261030/import.sql
second=$("${mysql[@]}" "$db" -e "SELECT (SELECT COUNT(*) FROM ingredient),(SELECT COUNT(*) FROM seasoning)")
test "$first" = "$second"
test "$second" = $'26\t10'
for table in ingredient seasoning; do
  kept=$("${mysql[@]}" "$db" -e "SELECT COUNT(*) FROM $table WHERE id='extra-existing' AND ${table}_image='keep-existing.webp' AND ${table}_desc='keep existing description'")
  test "$kept" = 1
  personal=$("${mysql[@]}" "$db" -e "SELECT COUNT(*) FROM $table WHERE id='extra-personal' AND scope='personal' AND ${table}_image='keep-personal.webp' AND ${table}_desc='keep personal'")
  test "$personal" = 1
  unrelated=$("${mysql[@]}" "$db" -e "SELECT COUNT(*) FROM $table WHERE id='extra-unrelated' AND ${table}_image='keep-unrelated.webp' AND ${table}_desc='keep unrelated'")
  test "$unrelated" = 1
done
echo 'PASS: full extra catalog imported twice, no duplicate names, existing and unrelated public records and personal records preserved'
