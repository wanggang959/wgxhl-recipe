import fs from 'node:fs/promises'
import { fileURLToPath } from 'node:url'

const catalog = JSON.parse(await fs.readFile(new URL('./existing-descriptions-20261030.json', import.meta.url), 'utf8'))
const sqlText = value => `CONVERT(0x${Buffer.from(value, 'utf8').toString('hex')} USING utf8mb4)`
const statements = ['SET NAMES utf8mb4;', 'START TRANSACTION;']
let count = 0
for (const [table, entries] of [['ingredient', catalog.ingredients], ['seasoning', catalog.seasonings]]) {
  const names = new Set()
  for (const [name, description] of entries) {
    if (!name?.trim() || !description?.trim() || description.length > 255 || names.has(name)) {
      throw new Error(`Invalid or duplicate description: ${table}/${name}`)
    }
    names.add(name)
    statements.push(`UPDATE ${table} SET ${table}_desc=${sqlText(description)}, update_time=NOW()`
      + ` WHERE scope='platform' AND owner_user_id='platform' AND ${table}_name=${sqlText(name)}`
      + ` AND (${table}_desc IS NULL OR CHAR_LENGTH(TRIM(${table}_desc))=0);`)
    count++
  }
}
statements.push('COMMIT;')
const output = new URL('../../deploy/catalog/common-20261030/fill-existing-descriptions.sql', import.meta.url)
await fs.mkdir(new URL('./', output), { recursive: true })
await fs.writeFile(output, statements.join('\n') + '\n')
console.log(`Built ${count} blank-only public description updates: ${fileURLToPath(output)}`)
