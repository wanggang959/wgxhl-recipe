import mysql from 'mysql2/promise'

const config = {
  host: process.env.MYSQL_HOST || 'wgxhl.space',
  port: Number(process.env.MYSQL_PORT || 3306),
  user: process.env.MYSQL_USER || 'recipe',
  password: process.env.MYSQL_PASS || '123456',
  database: process.env.MYSQL_DB || 'recipe',
  charset: 'utf8mb4',
}

function looksEnglish(text) {
  if (!text) return false
  return /^[A-Za-z][A-Za-z0-9 _.-]*$/.test(text.trim())
}

const conn = await mysql.createConnection(config)
try {
  const [tables] = await conn.query(
    'SELECT TABLE_NAME, TABLE_COMMENT FROM information_schema.TABLES WHERE TABLE_SCHEMA=? ORDER BY TABLE_NAME',
    [config.database],
  )
  const [columns] = await conn.query(
    'SELECT TABLE_NAME, COLUMN_NAME, COLUMN_COMMENT FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=? ORDER BY TABLE_NAME, ORDINAL_POSITION',
    [config.database],
  )
  console.log('英文表注释:')
  for (const row of tables) {
    if (looksEnglish(row.TABLE_COMMENT)) {
      console.log(`- ${row.TABLE_NAME}: ${row.TABLE_COMMENT}`)
    }
  }
  console.log('\n英文字段注释:')
  for (const row of columns) {
    if (looksEnglish(row.COLUMN_COMMENT)) {
      console.log(`- ${row.TABLE_NAME}.${row.COLUMN_NAME}: ${row.COLUMN_COMMENT}`)
    }
  }
} finally {
  await conn.end()
}
