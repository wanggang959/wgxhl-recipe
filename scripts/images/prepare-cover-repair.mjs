import fs from 'node:fs/promises'
import path from 'node:path'
import { createHash } from 'node:crypto'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('../../', import.meta.url))
const sharp = createRequire(new URL('../../frontend/package.json', import.meta.url))('sharp')
const input = path.join(root, 'tmp/recipe-cover-audit')
const output = path.join(root, 'deploy/images/cover-repair-20261030')
const audit = JSON.parse(await fs.readFile(path.join(input, 'report.json'), 'utf8'))
const targets = audit.filter(row => row.bytes > 1024 * 1024 && row.format === 'png')
if (targets.length !== 8) throw Error('Expected exactly the eight audited PNG covers')
const digest = buffer => createHash('sha256').update(buffer).digest('hex')
await fs.mkdir(path.join(output, 'originals'), { recursive: true })
const records = []
for (const row of targets) {
  if (!/^recipe\/cover\/[a-z0-9-]+\.webp$/.test(row.key)) throw Error('Unexpected source path')
  const original = await fs.readFile(path.join(input, path.basename(row.key)))
  const metadata = await sharp(original).metadata()
  if (original.length !== row.bytes || metadata.format !== 'png') throw Error('Audit source changed')
  const objectKey = 'recipe/cover/optimized-20261030/' + path.basename(row.key)
  const asset = path.join(output, 'assets', objectKey)
  await fs.mkdir(path.dirname(asset), { recursive: true })
  const encoded = await sharp(original).rotate().resize({ width: 1280, height: 1280, fit: 'inside', withoutEnlargement: true })
    .webp({ quality: 80 }).toBuffer()
  const encodedMeta = await sharp(encoded).metadata()
  if (encodedMeta.format !== 'webp' || encoded.length > 320 * 1024) throw Error('Invalid optimized cover')
  await fs.writeFile(asset, encoded)
  await fs.writeFile(path.join(output, 'originals', path.basename(row.key)), original)
  records.push({ id: row.id, name: row.name, oldKey: row.key, objectKey, originalBytes: original.length,
    bytes: encoded.length, originalSha256: digest(original), sha256: digest(encoded), width: encodedMeta.width, height: encodedMeta.height })
}
await fs.writeFile(path.join(output, 'manifest.json'), JSON.stringify({ batch: 'cover-repair-20261030', records }, null, 2) + '\n')
const text = value => `CONVERT(0x${Buffer.from(value, 'utf8').toString('hex')} USING utf8mb4)`
const host = 'https://wg-recipe-1385696563.cos.ap-chengdu.myqcloud.com/'
function mapping(reverse = false) {
  return [
    'SET NAMES utf8mb4;',
    'CREATE TEMPORARY TABLE cover_repair_map (recipe_id varchar(36) PRIMARY KEY, old_path varchar(255) NOT NULL, new_path varchar(255) NOT NULL);',
    ...records.map(row => `INSERT INTO cover_repair_map VALUES (${text(row.id)},${text(reverse ? row.objectKey : row.oldKey)},${text(reverse ? row.oldKey : row.objectKey)});`),
  ]
}
function matches(column) {
  return `CAST(${column} AS BINARY) IN (CAST(m.old_path AS BINARY), CAST(CONCAT('/',m.old_path) AS BINARY), CAST(CONCAT(${text(host)},m.old_path) AS BINARY))`
}
const equal = (left, right) => `CAST(${left} AS BINARY)=CAST(${right} AS BINARY)`
function updates(reverse) {
  const sql = [...mapping(reverse), 'START TRANSACTION;',
    `UPDATE recipe r JOIN cover_repair_map m ON ${equal('r.id', 'm.recipe_id')} SET r.cover_image=m.new_path,r.update_time=NOW() WHERE ${matches('r.cover_image')};`]
  for (const [table, column] of [['user_favorite', 'cover_image'], ['user_wanted_recipe', 'cover_image'], ['recipe_image', 'image_url'], ['recipe_step', 'step_image']]) {
    sql.push(`UPDATE ${table} t JOIN cover_repair_map m ON ${equal('t.recipe_id', 'm.recipe_id')} JOIN recipe r ON ${equal('r.id', 'm.recipe_id')} AND ${equal('r.cover_image', 'm.new_path')}`
      + ` SET t.${column}=m.new_path,t.update_time=NOW() WHERE ${matches('t.' + column)};`)
  }
  sql.push('COMMIT;', `SELECT COUNT(*) AS mapped_recipes FROM recipe r JOIN cover_repair_map m ON ${equal('r.id', 'm.recipe_id')} AND ${equal('r.cover_image', 'm.new_path')};`)
  return sql.join('\n') + '\n'
}
await fs.writeFile(path.join(output, 'apply.sql'), updates(false))
await fs.writeFile(path.join(output, 'rollback.sql'), updates(true))
const checks = [...mapping(), `SELECT COUNT(*) AS mapped_recipes FROM recipe r JOIN cover_repair_map m ON ${equal('r.id', 'm.recipe_id')} AND ${equal('r.cover_image', 'm.new_path')};`]
for (const [table, column] of [['user_favorite', 'cover_image'], ['user_wanted_recipe', 'cover_image'], ['recipe_image', 'image_url'], ['recipe_step', 'step_image']]) {
  checks.push(`SELECT COUNT(*) AS stale_${table} FROM ${table} t JOIN cover_repair_map m ON ${equal('t.recipe_id', 'm.recipe_id')} JOIN recipe r ON ${equal('r.id', 'm.recipe_id')} AND ${equal('r.cover_image', 'm.new_path')} WHERE ${matches('t.' + column)};`)
}
await fs.writeFile(path.join(output, 'verify.sql'), checks.join('\n') + '\n')
const fixtures = ['CREATE DATABASE IF NOT EXISTS recipe_cover_repair_check_20261030 CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;',
  'ALTER DATABASE recipe_cover_repair_check_20261030 CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;',
  'USE recipe_cover_repair_check_20261030;', 'SET NAMES utf8mb4;']
for (const table of ['recipe', 'user_favorite', 'user_wanted_recipe', 'recipe_image', 'recipe_step']) {
  fixtures.push(`CREATE TABLE IF NOT EXISTS ${table} LIKE recipe.${table};`)
}
for (const row of records) {
  fixtures.push(`INSERT INTO recipe (id,recipe_name,cover_image,owner_user_id) VALUES (${text(row.id)},${text(row.name)},${text(row.oldKey)},'cover-fixture') ON DUPLICATE KEY UPDATE cover_image=VALUES(cover_image);`)
}
const first = records[0], last = records.at(-1)
fixtures.push(
  `INSERT INTO recipe (id,recipe_name,cover_image,owner_user_id) VALUES ('cover-unrelated','unrelated',${text(first.oldKey)},'cover-fixture') ON DUPLICATE KEY UPDATE cover_image=VALUES(cover_image);`,
  `INSERT INTO user_favorite (id,user_id,household_id,recipe_id,cover_image) VALUES ('cover-favorite','cover-user','cover-household',${text(first.id)},${text(host + first.oldKey)}) ON DUPLICATE KEY UPDATE cover_image=VALUES(cover_image);`,
  `INSERT INTO user_favorite (id,user_id,household_id,recipe_id,cover_image) VALUES ('cover-personal-favorite','cover-other-user','cover-household',${text(first.id)},'keep-personal-cover.jpg') ON DUPLICATE KEY UPDATE cover_image=VALUES(cover_image);`,
  `INSERT INTO user_favorite (id,user_id,household_id,recipe_id,cover_image) VALUES ('cover-concurrent-favorite','cover-user','cover-household',${text(last.id)},${text(last.oldKey)}) ON DUPLICATE KEY UPDATE cover_image=VALUES(cover_image);`,
  `INSERT INTO user_wanted_recipe (id,user_id,household_id,recipe_id,cover_image,planned_date) VALUES ('cover-wanted','cover-user','cover-household',${text(first.id)},${text(first.oldKey)},'2026-10-30') ON DUPLICATE KEY UPDATE cover_image=VALUES(cover_image);`,
  `INSERT INTO recipe_image (id,recipe_id,image_type,image_url) VALUES ('cover-image',${text(first.id)},'cover',${text('/' + first.oldKey)}) ON DUPLICATE KEY UPDATE image_url=VALUES(image_url);`,
  `INSERT INTO recipe_step (id,recipe_id,step_no,step_image) VALUES ('cover-step',${text(first.id)},1,${text(first.oldKey)}) ON DUPLICATE KEY UPDATE step_image=VALUES(step_image);`,
)
await fs.writeFile(path.join(root, 'tmp/cover-repair-fixture.sql'), fixtures.join('\n') + '\n')
await fs.writeFile(path.join(root, 'tmp/cover-repair-concurrent.sql'),
  `UPDATE recipe SET cover_image='recipe/cover/user-edited.jpg' WHERE id=${text(last.id)};\n`)
console.log(JSON.stringify({ output, covers: records.length, originalBytes: records.reduce((n,r)=>n+r.originalBytes,0), optimizedBytes: records.reduce((n,r)=>n+r.bytes,0) }))
