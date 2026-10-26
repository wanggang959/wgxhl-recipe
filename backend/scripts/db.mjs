#!/usr/bin/env node
/**
 * 数据库查询/操作入口（连接信息复用 .cursor/mcp.json 的 mysql 配置）
 *
 * 用法：
 *   node backend/scripts/db.mjs "select * from todo limit 5"
 *   node backend/scripts/db.mjs --file backend/sql/add_xxx.sql --write
 *   node backend/scripts/db.mjs --write "update todo set status='DONE' where id='...'"
 *   echo "show tables" | node backend/scripts/db.mjs
 *
 * 默认只允许 SELECT / SHOW / DESCRIBE / EXPLAIN；
 * 其他语句必须显式加 --write，执行前请确认已备份。
 * 可用 MYSQL_HOST / MYSQL_PORT / MYSQL_USER / MYSQL_PASS / MYSQL_DB 覆盖配置。
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import mysql from 'mysql2/promise'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(__dirname, '../..')

const args = process.argv.slice(2)
const allowWrite = args.includes('--write')
const fileIndex = args.indexOf('--file')

function loadConfig() {
  if (process.env.MYSQL_HOST && process.env.MYSQL_USER) {
    return {
      host: process.env.MYSQL_HOST,
      port: Number(process.env.MYSQL_PORT || 3306),
      user: process.env.MYSQL_USER,
      password: process.env.MYSQL_PASS || '',
      database: process.env.MYSQL_DB || 'recipe',
    }
  }
  const mcpPath = path.join(repoRoot, '.cursor/mcp.json')
  if (fs.existsSync(mcpPath)) {
    const mcp = JSON.parse(fs.readFileSync(mcpPath, 'utf8'))
    const env = mcp?.mcpServers?.mysql?.env || {}
    if (env.MYSQL_HOST && env.MYSQL_USER) {
      return {
        host: env.MYSQL_HOST,
        port: Number(env.MYSQL_PORT || 3306),
        user: env.MYSQL_USER,
        password: env.MYSQL_PASS || '',
        database: env.MYSQL_DB || 'recipe',
      }
    }
  }
  throw new Error('未找到数据库配置：请设置 MYSQL_* 环境变量或检查 .cursor/mcp.json')
}

function readSql() {
  if (fileIndex >= 0) {
    const filePath = args[fileIndex + 1]
    if (!filePath) throw new Error('--file 后需要 SQL 文件路径')
    return fs.readFileSync(path.resolve(process.cwd(), filePath), 'utf8')
  }
  const inline = args.filter((arg, index) => index !== fileIndex + 1 || fileIndex < 0)
    .filter((arg) => arg !== '--write' && arg !== '--file')
  if (inline.length > 0) return inline.join(' ')
  return fs.readFileSync(0, 'utf8')
}

function isReadOnly(sql) {
  const text = sql
    .replace(/^\s*(\/\*[\s\S]*?\*\/|--[^\n]*\n|#[^\n]*\n)/g, '')
    .trim()
  if (!/^(select|show|describe|desc|explain)\b/i.test(text)) return false
  return !/\b(into\s+(outfile|dumpfile)|load_file\s*\()/i.test(text)
}

function formatRows(rows) {
  if (!Array.isArray(rows)) return rows
  if (rows.length === 0) return '(0 rows)'
  return JSON.stringify(rows, null, 2)
}

const sql = readSql().trim()
if (!sql) {
  console.error('SQL 不能为空')
  process.exit(1)
}
if (!allowWrite && !isReadOnly(sql)) {
  console.error('拒绝执行：默认只允许只读查询。确认要执行写操作请加 --write，并先做好备份。')
  process.exit(2)
}

const config = loadConfig()
const conn = await mysql.createConnection({
  ...config,
  charset: 'utf8mb4',
  connectTimeout: 15000,
  multipleStatements: allowWrite,
})

try {
  const started = Date.now()
  const [result] = await conn.query(sql)
  const elapsed = Date.now() - started
  if (Array.isArray(result)) {
    console.log(formatRows(result))
    console.log(`\n(${result.length} row${result.length === 1 ? '' : 's'}, ${elapsed} ms)`)
  } else {
    console.log(JSON.stringify(result))
    console.log(`\n(affected ${result.affectedRows ?? 0}, ${elapsed} ms)`)
  }
} finally {
  await conn.end()
}
