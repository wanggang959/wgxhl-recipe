/**
 * 将 recipe 库复制为 recipe-cloud，创建 recipe-cloud 用户，并导出本地备份 SQL
 * 用法：
 *   $env:MYSQL_ROOT_PASS='your-root-pass'; node backend/scripts/clone-recipe-to-cloud.mjs
 */
import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'
import mysql from 'mysql2/promise'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const rootDir = path.join(__dirname, '..', '..')

const config = {
  host: process.env.MYSQL_HOST || 'wgxhl.space',
  port: Number(process.env.MYSQL_PORT || 3306),
  user: process.env.MYSQL_ROOT_USER || 'root',
  password: process.env.MYSQL_ROOT_PASS,
  charset: 'utf8mb4',
  multipleStatements: true,
}

const sourceDb = 'recipe'
const targetDb = 'recipe-cloud'
const appUser = 'recipe-cloud'
const appPass = '123456'

function qIdent(name) {
  return `\`${String(name).replace(/`/g, '``')}\``
}

function sqlValue(value) {
  if (value === null || value === undefined) return 'NULL'
  if (value instanceof Date) {
    return `'${value.toISOString().slice(0, 19).replace('T', ' ')}'`
  }
  if (Buffer.isBuffer(value)) {
    return `X'${value.toString('hex')}'`
  }
  if (typeof value === 'number') return String(value)
  if (typeof value === 'boolean') return value ? '1' : '0'
  return `'${String(value).replace(/\\/g, '\\\\').replace(/'/g, "''")}'`
}

async function main() {
  if (!config.password) {
    throw new Error('请设置环境变量 MYSQL_ROOT_PASS')
  }
  const conn = await mysql.createConnection(config)
  const backupLines = [
    '-- recipe-cloud backup',
    `-- generated at ${new Date().toISOString()}`,
    'SET NAMES utf8mb4;',
    `CREATE DATABASE IF NOT EXISTS ${qIdent(targetDb)} DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`,
    `USE ${qIdent(targetDb)};`,
    '',
  ]

  try {
    await conn.query('SET NAMES utf8mb4')

    console.log(`[1/5] 创建数据库 ${targetDb} ...`)
    await conn.query(
      `CREATE DATABASE IF NOT EXISTS ${qIdent(targetDb)} DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci`,
    )

    console.log(`[2/5] 读取源库 ${sourceDb} 表清单 ...`)
    const [tables] = await conn.query(
      'SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=? AND TABLE_TYPE=? ORDER BY TABLE_NAME',
      [sourceDb, 'BASE TABLE'],
    )
    if (!tables.length) {
      throw new Error(`源库 ${sourceDb} 没有表`)
    }

    console.log(`[3/5] 复制 ${tables.length} 张表到 ${targetDb} ...`)
    await conn.query(`USE ${qIdent(targetDb)}`)
    await conn.query('SET FOREIGN_KEY_CHECKS=0')

    for (const { TABLE_NAME } of tables) {
      const table = TABLE_NAME
      console.log(`  - ${table}`)
      await conn.query(`DROP TABLE IF EXISTS ${qIdent(targetDb)}.${qIdent(table)}`)
      await conn.query(`CREATE TABLE ${qIdent(targetDb)}.${qIdent(table)} LIKE ${qIdent(sourceDb)}.${qIdent(table)}`)
      await conn.query(`INSERT INTO ${qIdent(targetDb)}.${qIdent(table)} SELECT * FROM ${qIdent(sourceDb)}.${qIdent(table)}`)

      const [createRows] = await conn.query(`SHOW CREATE TABLE ${qIdent(sourceDb)}.${qIdent(table)}`)
      backupLines.push(`DROP TABLE IF EXISTS ${qIdent(table)};`)
      backupLines.push(`${createRows[0]['Create Table']};`)
      backupLines.push('')

      const [rows] = await conn.query(`SELECT * FROM ${qIdent(sourceDb)}.${qIdent(table)}`)
      if (rows.length > 0) {
        const columns = Object.keys(rows[0])
        const chunkSize = 100
        for (let i = 0; i < rows.length; i += chunkSize) {
          const chunk = rows.slice(i, i + chunkSize)
          const values = chunk
            .map((row) => `(${columns.map((col) => sqlValue(row[col])).join(', ')})`)
            .join(',\n')
          backupLines.push(
            `INSERT INTO ${qIdent(table)} (${columns.map(qIdent).join(', ')}) VALUES\n${values};`,
          )
        }
        backupLines.push('')
      }
    }

    await conn.query('SET FOREIGN_KEY_CHECKS=1')

    console.log(`[4/5] 创建用户 ${appUser} 并授权 ...`)
    const [users] = await conn.query(
      'SELECT user, host FROM mysql.user WHERE user=?',
      [appUser],
    )
    if (users.length === 0) {
      await conn.query(`CREATE USER ${qIdent(appUser)}@'%' IDENTIFIED BY ?`, [appPass])
    } else {
      await conn.query(`ALTER USER ${qIdent(appUser)}@'%' IDENTIFIED BY ?`, [appPass])
    }
    await conn.query(`GRANT ALL PRIVILEGES ON ${qIdent(targetDb)}.* TO ${qIdent(appUser)}@'%'`)
    await conn.query('FLUSH PRIVILEGES')

    backupLines.push(`-- app user: ${appUser}`)
    backupLines.push(`CREATE USER IF NOT EXISTS ${qIdent(appUser)}@'%' IDENTIFIED BY '${appPass}';`)
    backupLines.push(`GRANT ALL PRIVILEGES ON ${qIdent(targetDb)}.* TO ${qIdent(appUser)}@'%';`)
    backupLines.push('FLUSH PRIVILEGES;')
    backupLines.push('')

    const backupDir = path.join(rootDir, 'backend', 'sql', 'backups')
    fs.mkdirSync(backupDir, { recursive: true })
    const stamp = new Date().toISOString().slice(0, 10).replace(/-/g, '')
    const backupFile = path.join(backupDir, `recipe-cloud-${stamp}.sql`)
    fs.writeFileSync(backupFile, `${backupLines.join('\n')}\n`, 'utf8')

    console.log(`[5/5] 本地备份已写入: ${backupFile}`)

    const [verifyTables] = await conn.query(
      'SELECT COUNT(*) AS cnt FROM information_schema.TABLES WHERE TABLE_SCHEMA=?',
      [targetDb],
    )
    console.log('\n完成:')
    console.log(`  源库: ${sourceDb}`)
    console.log(`  新库: ${targetDb} (${verifyTables[0].cnt} 张表)`)
    console.log(`  用户: ${appUser} / ${appPass}`)
    console.log(`  备份: ${backupFile}`)
  } finally {
    await conn.end()
  }
}

main().catch((error) => {
  console.error('执行失败:', error.message || error)
  process.exit(1)
})
