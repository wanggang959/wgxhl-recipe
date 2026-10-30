import { createHash } from 'node:crypto'
import fs from 'node:fs/promises'
import path from 'node:path'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('../../', import.meta.url))
const require = createRequire(new URL('../../frontend/package.json', import.meta.url))
const sharp = require('sharp')
const extra = process.argv.includes('--extra')
const suffix = extra ? '-extra-20261030' : '-20261030'
const catalog = JSON.parse(await fs.readFile(path.join(root, `scripts/catalog/common-catalog${suffix}.json`), 'utf8'))
const sources = JSON.parse(await fs.readFile(path.join(root, `tmp/catalog-sources${suffix}.json`), 'utf8'))
const partial = process.argv.includes('--partial')
const sample = process.argv.includes('--sample')
if (extra && sample) throw new Error('Samples are only available for the original batch')
const output = path.join(root, 'deploy/catalog', catalog.batch + (sample ? '-sample' : ''))
await fs.mkdir(output, { recursive: true })
const sqlText = value => `CONVERT(0x${Buffer.from(value, 'utf8').toString('hex')} USING utf8mb4)`
const xmlText = value => value.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
const records = []

for (const [kind, entries] of [['ingredient', catalog.ingredients], ['seasoning', catalog.seasonings]]) {
  for (const [slug, name, description] of entries) {
    if (sample && !(kind === 'ingredient' && ['eggplant', 'zucchini'].includes(slug))) continue
    const source = sources.find(item => item.kind === kind && item.slug === slug)
    if (!source) {
      if (partial) continue
      throw new Error(`Missing generated image: ${kind}/${slug}`)
    }
    const objectKey = `${kind}/${catalog.batch}/${slug}.webp`
    const asset = path.join(output, 'assets', objectKey)
    await fs.mkdir(path.dirname(asset), { recursive: true })
    await sharp(source.source).flatten({ background: '#ffffff' }).resize(512, 512, { fit: 'contain', background: '#ffffff' })
      .webp({ quality: 86, effort: 6 }).toFile(asset)
    const hash = createHash('sha256').update(`${catalog.batch}:${kind}:${slug}`).digest('hex')
    const id = [hash.slice(0, 8), hash.slice(8, 12), hash.slice(12, 16), hash.slice(16, 20), hash.slice(20, 32)].join('-')
    records.push({ id, kind, name, description, objectKey })
  }
}

await fs.writeFile(path.join(output, 'manifest.json'), JSON.stringify({ batch: catalog.batch, records }, null, 2) + '\n')
const sql = ['SET NAMES utf8mb4;', 'START TRANSACTION;']
for (const record of records) {
  const { id, kind, name, objectKey, description } = record
  sql.push(`INSERT INTO ${kind} (id, ${kind}_name, ${kind}_image, ${kind}_desc, scope, owner_user_id, create_time, update_time)`
    + ` SELECT ${sqlText(id)}, ${sqlText(name)}, ${sqlText(objectKey)}, ${sqlText(description)}, 'platform', 'platform', NOW(), NOW()`
    + ` FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ${kind} WHERE owner_user_id='platform' AND ${kind}_name=${sqlText(name)});`)
}
if (!sample && !extra) sql.push(
  `UPDATE seasoning s LEFT JOIN seasoning correct ON correct.owner_user_id='platform' AND correct.seasoning_name=${sqlText('蚝油')}`
    + ` SET s.seasoning_name=${sqlText('蚝油')}, s.update_time=NOW()`
    + ` WHERE s.scope='platform' AND s.owner_user_id='platform' AND s.seasoning_name=${sqlText('耗油')} AND correct.id IS NULL;`,
  `UPDATE recipe_seasoning_rel r JOIN seasoning s ON s.id=r.seasoning_id`
    + ` SET r.seasoning_name=${sqlText('蚝油')}, r.update_time=NOW()`
    + ` WHERE s.scope='platform' AND s.owner_user_id='platform' AND s.seasoning_name=${sqlText('蚝油')} AND r.seasoning_name=${sqlText('耗油')};`,
)
sql.push('COMMIT;')
if (!partial) await fs.writeFile(path.join(output, 'import.sql'), sql.join('\n') + '\n')

for (let start = 0; start < records.length; start += 16) {
  const group = records.slice(start, start + 16)
  const width = 960, cellWidth = 240, cellHeight = 265
  const layers = []
  for (let index = 0; index < group.length; index++) {
    const record = group[index]
    const left = (index % 4) * cellWidth, top = Math.floor(index / 4) * cellHeight
    const thumbnail = await sharp(path.join(output, 'assets', record.objectKey)).resize(224, 224).png().toBuffer()
    layers.push({ input: thumbnail, left: left + 8, top })
    const label = `<svg width="240" height="40"><rect width="240" height="40" fill="white"/><text x="120" y="27" text-anchor="middle" font-family="Microsoft YaHei, sans-serif" font-size="21">${start + index + 1}. ${xmlText(record.name)}</text></svg>`
    layers.push({ input: Buffer.from(label), left, top: top + 224 })
  }
  await sharp({ create: { width, height: Math.ceil(group.length / 4) * cellHeight, channels: 3, background: '#ffffff' } })
    .composite(layers).jpeg({ quality: 88 }).toFile(path.join(output, `review-${Math.floor(start / 16) + 1}.jpg`))
}
console.log(`Built ${records.length} catalog images${partial ? ' (partial preview only)' : ' and transactional import.sql'} in ${output}`)
