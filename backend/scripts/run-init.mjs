import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'
import mysql from 'mysql2/promise'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const sqlPath = path.join(__dirname, '..', 'sql', 'init.sql')
const sql = fs.readFileSync(sqlPath, 'utf8')

const conn = await mysql.createConnection({
  host: 'wgxhl.space',
  user: 'recipe',
  password: '123456',
  database: 'recipe',
  multipleStatements: true,
  connectTimeout: 15000,
})

try {
  await conn.query(sql)
  const [tables] = await conn.query('SHOW TABLES')
  console.log('建表成功，共', tables.length, '张表：')
  for (const row of tables) {
    console.log(' -', Object.values(row)[0])
  }
} finally {
  await conn.end()
}
