import mysql from 'mysql2/promise'

const conn = await mysql.createConnection({
  host: process.env.DB_HOST || 'wgxhl.space',
  user: process.env.DB_USER || 'recipe',
  password: process.env.DB_PASSWORD || '123456',
  database: process.env.DB_NAME || 'recipe',
  connectTimeout: 15000,
})

try {
  const [cols] = await conn.query(
    "SHOW COLUMNS FROM recipe WHERE Field IN ('owner_user_id', 'owner_name')",
  )
  const existing = new Set(cols.map((row) => row.Field))

  if (!existing.has('owner_user_id')) {
    await conn.query(
      "ALTER TABLE recipe ADD COLUMN owner_user_id varchar(36) NULL COMMENT '上传者用户id' AFTER category_name",
    )
    console.log('已添加 owner_user_id')
  } else {
    console.log('owner_user_id 已存在，跳过')
  }

  if (!existing.has('owner_name')) {
    await conn.query(
      "ALTER TABLE recipe ADD COLUMN owner_name varchar(64) NULL COMMENT '上传者显示名' AFTER owner_user_id",
    )
    console.log('已添加 owner_name')
  } else {
    console.log('owner_name 已存在，跳过')
  }

  const [verify] = await conn.query(
    "SHOW COLUMNS FROM recipe WHERE Field IN ('owner_user_id', 'owner_name')",
  )
  for (const row of verify) {
    console.log(`  ${row.Field}\t${row.Type}\t${row.Null}`)
  }
  console.log('完成')
} finally {
  await conn.end()
}
