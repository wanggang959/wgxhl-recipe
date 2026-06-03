/**
 * 清理重复的「王师傅」账号，并将内置管理员用户名恢复为「王师傅」。
 * 用法：node backend/scripts/fix-duplicate-admin.mjs
 */
import mysql from 'mysql2/promise'

const ADMIN_ID = 'admin-wangshifu'
const ADMIN_USERNAME = '王师傅'

const conn = await mysql.createConnection({
  host: 'wgxhl.space',
  user: 'recipe',
  password: '123456',
  database: 'recipe',
  connectTimeout: 15000,
})

try {
  const [users] = await conn.query(
    'SELECT id, username, nickname, user_role FROM app_user WHERE username = ? OR id = ? ORDER BY create_time ASC',
    [ADMIN_USERNAME, ADMIN_ID],
  )

  if (users.length === 0) {
    console.log('未找到王师傅相关账号，无需处理。')
    process.exit(0)
  }

  let canonical = users.find((u) => u.id === ADMIN_ID) || users.find((u) => u.username === ADMIN_USERNAME)
  if (!canonical) {
    canonical = users[0]
  }

  await conn.query(
    'UPDATE app_user SET username = ?, user_role = ?, status = ? WHERE id = ?',
    [ADMIN_USERNAME, 'admin', 'normal', canonical.id],
  )
  console.log('已校正主账号:', canonical.id, '-> 用户名', ADMIN_USERNAME)

  const duplicateIds = users
    .map((u) => u.id)
    .filter((id) => id !== canonical.id)

  if (duplicateIds.length > 0) {
    const placeholders = duplicateIds.map(() => '?').join(',')
    await conn.query(`DELETE FROM app_user WHERE id IN (${placeholders})`, duplicateIds)
    console.log('已删除重复账号:', duplicateIds.join(', '))
  } else {
    console.log('未发现重复账号。')
  }
} finally {
  await conn.end()
}
