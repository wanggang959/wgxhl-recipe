#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
db=recipe_cover_repair_check_20261030
mysql=(docker exec -i test-mysql-1 mysql -uroot -plocal-test-root-only --default-character-set=utf8mb4 -N -B)
"${mysql[@]}" < tmp/cover-repair-fixture.sql
for pass in 1 2; do
  result=$("${mysql[@]}" "$db" < deploy/images/cover-repair-20261030/apply.sql)
  test "$result" = 8
done
verification=$("${mysql[@]}" "$db" < deploy/images/cover-repair-20261030/verify.sql)
test "$verification" = $'8\n0\n0\n0\n0'
personal=$("${mysql[@]}" "$db" -e "SELECT COUNT(*) FROM user_favorite WHERE id='cover-personal-favorite' AND cover_image='keep-personal-cover.jpg'")
test "$personal" = 1
unrelated=$("${mysql[@]}" "$db" -e "SELECT COUNT(*) FROM recipe WHERE id='cover-unrelated' AND cover_image NOT LIKE '%optimized-20261030%'")
test "$unrelated" = 1
rollback=$("${mysql[@]}" "$db" < deploy/images/cover-repair-20261030/rollback.sql)
test "$rollback" = 8
remaining=$("${mysql[@]}" "$db" -e "SELECT COUNT(*) FROM recipe WHERE cover_image LIKE '%optimized-20261030%'")
test "$remaining" = 0
"${mysql[@]}" "$db" < tmp/cover-repair-concurrent.sql
concurrent=$("${mysql[@]}" "$db" < deploy/images/cover-repair-20261030/apply.sql)
test "$concurrent" = 7
kept=$("${mysql[@]}" "$db" -e "SELECT COUNT(*) FROM user_favorite WHERE id='cover-concurrent-favorite' AND cover_image NOT LIKE '%optimized-20261030%'")
test "$kept" = 1
echo 'PASS: atomic cover replacement and all four reference types, idempotency, rollback, and concurrent user-edit protection'
