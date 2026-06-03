import mysql from 'mysql2/promise'

const ADMIN_ID = 'admin-wangshifu'
const ADMIN_NAME = '王师傅'

const conn = await mysql.createConnection({
  host: process.env.DB_HOST || 'wgxhl.space',
  user: process.env.DB_USER || 'recipe',
  password: process.env.DB_PASSWORD || '123456',
  database: process.env.DB_NAME || 'recipe',
  connectTimeout: 15000,
})

try {
  const [result] = await conn.query(
    `UPDATE recipe
     SET owner_user_id = ?, owner_name = ?
     WHERE owner_user_id IS NULL OR owner_user_id = ''
        OR owner_name IS NULL OR owner_name = ''`,
    [ADMIN_ID, ADMIN_NAME],
  )
  const [stats] = await conn.query(
    'SELECT COUNT(*) AS total FROM recipe WHERE owner_name = ?',
    [ADMIN_NAME],
  )
  console.log(`已补全 ${result.affectedRows} 条菜谱署名为「${ADMIN_NAME}」`)
  console.log(`当前共 ${stats[0].total} 条菜谱署名王师傅`)
} finally {
  await conn.end()
}
