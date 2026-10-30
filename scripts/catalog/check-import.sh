#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
db=recipe_catalog_check_20261030
mysql=(docker exec -i test-mysql-1 mysql -uroot -plocal-test-root-only --default-character-set=utf8mb4 -N -B)
"${mysql[@]}" < scripts/catalog/check-fixture.sql
"${mysql[@]}" "$db" < deploy/catalog/common-20261030/import.sql
first=$("${mysql[@]}" "$db" -e "SELECT (SELECT COUNT(*) FROM ingredient),(SELECT COUNT(*) FROM seasoning),(SELECT COUNT(*) FROM recipe_seasoning_rel)")
"${mysql[@]}" "$db" < deploy/catalog/common-20261030/import.sql
second=$("${mysql[@]}" "$db" -e "SELECT (SELECT COUNT(*) FROM ingredient),(SELECT COUNT(*) FROM seasoning),(SELECT COUNT(*) FROM recipe_seasoning_rel)")
test "$first" = "$second"
test "$second" = $'57\t19\t1'
kept=$("${mysql[@]}" "$db" -e "SELECT COUNT(*) FROM ingredient WHERE id='fixture-existing-eggplant' AND ingredient_image='keep-existing.webp' AND ingredient_desc='keep existing description'")
test "$kept" = 1
personal=$("${mysql[@]}" "$db" -e "SELECT COUNT(*) FROM ingredient WHERE id='fixture-personal' AND scope='personal' AND ingredient_image='keep-personal.webp'")
test "$personal" = 1
fixed=$("${mysql[@]}" "$db" -e "SELECT COUNT(*) FROM seasoning s JOIN recipe_seasoning_rel r ON r.seasoning_id=s.id WHERE s.id='fixture-oyster' AND s.seasoning_name=CONVERT(0xe89a9de6b2b9 USING utf8mb4) AND r.seasoning_name=s.seasoning_name")
test "$fixed" = 1
echo 'PASS: complete import, rerun has no duplicates, existing and personal records preserved, oyster sauce ID and recipe reference preserved'
