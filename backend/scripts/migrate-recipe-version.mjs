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
    "SHOW COLUMNS FROM recipe WHERE Field = 'recipe_version'",
  )
  if (cols.length === 0) {
    await conn.query(
      "ALTER TABLE recipe ADD COLUMN recipe_version varchar(8) NOT NULL DEFAULT '1.0' COMMENT '版本号（如1.0）' AFTER recipe_name",
    )
    console.log('已添加 recipe_version')
  } else {
    console.log('recipe_version 已存在')
  }

  await conn.query("UPDATE recipe SET recipe_version = '1.0' WHERE recipe_version IS NULL OR recipe_version = ''")

  const [indexes] = await conn.query('SHOW INDEX FROM recipe')
  const indexNames = new Set(indexes.map((row) => row.Key_name))
  if (indexNames.has('uk_recipe_name')) {
    await conn.query('ALTER TABLE recipe DROP INDEX uk_recipe_name')
    console.log('已删除 uk_recipe_name')
  }
  if (!indexNames.has('uk_recipe_name_version_owner')) {
    await conn.query(
      'ALTER TABLE recipe ADD UNIQUE KEY uk_recipe_name_version_owner (recipe_name, recipe_version, owner_user_id)',
    )
    console.log('已添加 uk_recipe_name_version_owner')
  } else {
    console.log('uk_recipe_name_version_owner 已存在')
  }

  const [rows] = await conn.query(
    'SELECT id, recipe_name, recipe_version, owner_name FROM recipe ORDER BY create_time',
  )
  console.log('当前菜谱：')
  for (const row of rows) {
    console.log(`  - ${row.recipe_name} v${row.recipe_version} · ${row.owner_name || '未署名'}`)
  }
  console.log('完成')
} finally {
  await conn.end()
}
