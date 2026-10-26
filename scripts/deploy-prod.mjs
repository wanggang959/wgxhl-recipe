#!/usr/bin/env node
/**
 * 一键升级生产环境：本机构建 -> SSH 上传 -> 重启 -> 健康检查
 *
 * 用法：
 *   node scripts/deploy-prod.mjs                 构建并部署前后端
 *   node scripts/deploy-prod.mjs --backend-only  只部署后端
 *   node scripts/deploy-prod.mjs --frontend-only 只部署前端
 *   node scripts/deploy-prod.mjs --skip-build    直接使用现有构建产物
 *   node scripts/deploy-prod.mjs --dry-run       只上传到服务器暂存目录，不替换线上
 *
 * 环境变量：DEPLOY_HOST（默认 wgxhl.space）、DEPLOY_USER（默认 root）、DEPLOY_KEY（默认桌面私钥）
 *
 * 说明：后端 jar 以 bind mount 方式挂载，替换后需重建容器；前端为 1Panel 静态站点，覆盖文件即可。
 * 表结构变更仍需手动执行 backend/sql 下新增的 SQL。
 */
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { spawnSync } from 'node:child_process'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(__dirname, '..')

const KNOWN_FLAGS = new Set(['--backend-only', '--frontend-only', '--skip-build', '--dry-run', '--help', '-h'])
const flags = new Set(process.argv.slice(2))
const unknownFlags = [...flags].filter((flag) => !KNOWN_FLAGS.has(flag))
const backendOnly = flags.has('--backend-only')
const frontendOnly = flags.has('--frontend-only')
const skipBuild = flags.has('--skip-build')
const dryRun = flags.has('--dry-run')

const doBackend = !frontendOnly
const doFrontend = !backendOnly

const HOST = process.env.DEPLOY_HOST || 'wgxhl.space'
const USER = process.env.DEPLOY_USER || 'root'
const SSH_KEY = process.env.DEPLOY_KEY || path.join(os.homedir(), 'Desktop', 'ssh-key-154.219.110.161.key')
const TARGET = `${USER}@${HOST}`

const REMOTE_BACKEND_DIR = '/opt/1panel/docker/compose/wgxhl-recipe-backend'
const REMOTE_JAR_NAME = 'recipe-backend-1.0.0-SNAPSHOT.jar'
const REMOTE_FRONTEND_DIR = '/opt/1panel/www/sites/recipe.wgxhl.space/index'
const REMOTE_STAGING_DIR = '/opt/wgxhl-deploy/staging'
const REMOTE_BACKUP_ROOT = '/opt/wgxhl-deploy/backups'
const BACKUP_KEEP = 5

const BACKEND_JAR = path.join(root, 'backend', 'target', REMOTE_JAR_NAME)
const FRONTEND_DIST = path.join(root, 'frontend', 'dist')

const SSH_OPTS = [
  '-o', 'BatchMode=yes',
  '-o', 'StrictHostKeyChecking=accept-new',
  '-o', 'ConnectTimeout=15',
]

const HEALTH_URL = 'http://127.0.0.1:9001/recipe/health'
const PUBLIC_URL = 'https://recipe.wgxhl.space/recipe/health'

function printHelp() {
  console.log(`用法：node scripts/deploy-prod.mjs [选项]

  --backend-only   只部署后端
  --frontend-only  只部署前端
  --skip-build     跳过本地构建，直接使用 backend/target 与 frontend/dist
  --dry-run        只构建并上传到服务器暂存目录，不替换线上文件、不重启
  -h, --help       显示帮助

环境变量：
  DEPLOY_HOST  服务器地址（默认 wgxhl.space）
  DEPLOY_USER  登录用户（默认 root）
  DEPLOY_KEY   SSH 私钥路径（默认 ~/Desktop/ssh-key-154.219.110.161.key）`)
}

function fail(message) {
  console.error(`\n[失败] ${message}`)
  process.exit(1)
}

function step(title) {
  console.log(`\n== ${title} ==`)
}

function timestamp() {
  const now = new Date()
  const pad = (value) => String(value).padStart(2, '0')
  return `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}-${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`
}

function run(command, options = {}) {
  console.log(`> ${command}`)
  const result = spawnSync(command, { shell: true, stdio: 'inherit', ...options })
  if (result.status !== 0) {
    fail(`命令执行失败（exit ${result.status}）：${command}`)
  }
}

function runArgs(bin, args, options = {}) {
  console.log(`> ${bin} ${args.join(' ')}`)
  const result = spawnSync(bin, args, { stdio: 'inherit', ...options })
  if (result.status !== 0) {
    fail(`命令执行失败（exit ${result.status}）：${bin}`)
  }
}

function ssh(remoteCommand, { capture = false } = {}) {
  const result = spawnSync('ssh', [...SSH_OPTS, '-i', SSH_KEY, TARGET, remoteCommand], {
    encoding: 'utf8',
    stdio: capture ? ['ignore', 'pipe', 'pipe'] : 'inherit',
  })
  if (result.error) {
    fail(`无法启动 ssh：${result.error.message}`)
  }
  if (result.status !== 0) {
    const detail = capture ? `\n${(result.stderr || '').trim()}` : ''
    fail(`远程命令失败（exit ${result.status}）${detail}`)
  }
  return (result.stdout || '').trim()
}

function sshTry(remoteCommand) {
  const result = spawnSync('ssh', [...SSH_OPTS, '-i', SSH_KEY, TARGET, remoteCommand], {
    stdio: ['ignore', 'inherit', 'inherit'],
  })
  return result.status === 0
}

function scpTo(localPath, remotePath) {
  console.log(`> scp ${path.basename(localPath)} -> ${TARGET}:${remotePath}`)
  const result = spawnSync('scp', [...SSH_OPTS, '-i', SSH_KEY, localPath, `${TARGET}:${remotePath}`], {
    stdio: 'inherit',
  })
  if (result.status !== 0) {
    fail(`scp 上传失败（exit ${result.status}）：${localPath}`)
  }
}

function setupLocalBuildTools() {
  const localJavaRoot = 'D:\\software\\java'
  const localJavaHome = path.join(localJavaRoot, 'jdk8')
  const localMavenHome = path.join(localJavaRoot, 'maven')
  const binDirs = []

  if (fs.existsSync(path.join(localJavaHome, 'bin', 'java.exe'))) {
    process.env.JAVA_HOME = process.env.JAVA_HOME || localJavaHome
    binDirs.push(path.join(localJavaHome, 'bin'))
  }
  if (fs.existsSync(path.join(localMavenHome, 'bin', 'mvn.cmd'))) {
    process.env.MAVEN_HOME = process.env.MAVEN_HOME || localMavenHome
    binDirs.push(path.join(localMavenHome, 'bin'))
  }
  if (binDirs.length > 0) {
    process.env.PATH = `${binDirs.join(path.delimiter)}${path.delimiter}${process.env.PATH || ''}`
  }
}

function fileSize(filePath) {
  return `${(fs.statSync(filePath).size / 1024 / 1024).toFixed(1)} MB`
}

function rollbackHint(ts) {
  const lines = []
  if (doBackend) {
    lines.push(`cp '${REMOTE_BACKUP_ROOT}/${ts}/${REMOTE_JAR_NAME}' '${REMOTE_BACKEND_DIR}/${REMOTE_JAR_NAME}'`)
    lines.push(`cd '${REMOTE_BACKEND_DIR}' && docker compose up -d --force-recreate`)
  }
  if (doFrontend) {
    lines.push(`tar -xzf '${REMOTE_BACKUP_ROOT}/${ts}/frontend-dist.tar.gz' -C '${REMOTE_FRONTEND_DIR}'`)
  }
  return lines.join('\n')
}

if (flags.has('--help') || flags.has('-h')) {
  printHelp()
  process.exit(0)
}
if (unknownFlags.length > 0) {
  console.error(`未知参数：${unknownFlags.join(' ')}\n`)
  printHelp()
  process.exit(1)
}
if (backendOnly && frontendOnly) {
  fail('--backend-only 与 --frontend-only 不能同时使用')
}

const ts = timestamp()

console.log('生产部署计划：')
console.log(`  目标：${TARGET}${dryRun ? '（dry-run，不改线上）' : ''}`)
console.log(`  范围：${doBackend ? '后端' : ''}${doBackend && doFrontend ? ' + ' : ''}${doFrontend ? '前端' : ''}`)
console.log(`  备份：${REMOTE_BACKUP_ROOT}/${ts}（保留最近 ${BACKUP_KEEP} 份）`)

step('1/6 准备与连接检查')
if (!fs.existsSync(SSH_KEY)) {
  fail(`找不到 SSH 私钥：${SSH_KEY}\n可通过 DEPLOY_KEY 环境变量指定`)
}
if (!sshTry('true')) {
  fail(`SSH 连接失败：${TARGET}\n请确认私钥已安装、网络可达`)
}
console.log(`SSH 连接正常：${TARGET}`)
ssh(`mkdir -p '${REMOTE_STAGING_DIR}' '${REMOTE_BACKUP_ROOT}'`)

const liveDirs = []
if (doBackend) liveDirs.push(REMOTE_BACKEND_DIR)
if (doFrontend) liveDirs.push(REMOTE_FRONTEND_DIR)
const missingDirs = ssh(`for d in ${liveDirs.join(' ')}; do [ -d "$d" ] || echo "$d"; done`, { capture: true })
if (missingDirs) {
  fail(`服务器目录不存在：\n${missingDirs}`)
}
if (doBackend && !sshTry('docker inspect wgxhl-recipe-backend >/dev/null 2>&1')) {
  fail('未找到容器 wgxhl-recipe-backend，请确认 1Panel 编排是否正常')
}

step('2/6 本地构建')
if (skipBuild) {
  console.log('跳过构建（--skip-build）')
} else {
  setupLocalBuildTools()
  if (doBackend) {
    run('mvn clean package -DskipTests', { cwd: path.join(root, 'backend') })
  }
  if (doFrontend) {
    run('npm run build', { cwd: path.join(root, 'frontend') })
  }
}
if (doBackend) {
  if (!fs.existsSync(BACKEND_JAR)) fail(`缺少后端产物：${BACKEND_JAR}`)
  console.log(`后端 jar：${fileSize(BACKEND_JAR)}`)
}
if (doFrontend) {
  if (!fs.existsSync(path.join(FRONTEND_DIST, 'index.html'))) fail(`缺少前端产物：${FRONTEND_DIST}`)
  console.log(`前端 dist：${FRONTEND_DIST}`)
}

step('3/6 备份线上文件')
if (dryRun) {
  console.log('dry-run 跳过备份')
} else {
  const commands = [`mkdir -p '${REMOTE_BACKUP_ROOT}/${ts}'`]
  if (doBackend) {
    commands.push(`cp -p '${REMOTE_BACKEND_DIR}/${REMOTE_JAR_NAME}' '${REMOTE_BACKUP_ROOT}/${ts}/'`)
  }
  if (doFrontend) {
    commands.push(`tar -czf '${REMOTE_BACKUP_ROOT}/${ts}/frontend-dist.tar.gz' -C '${REMOTE_FRONTEND_DIR}' .`)
  }
  commands.push(`ls -1dt '${REMOTE_BACKUP_ROOT}'/*/ | tail -n +${BACKUP_KEEP + 1} | xargs -r rm -rf`)
  ssh(`set -e; ${commands.join('; ')}`)
  console.log(`已备份到 ${REMOTE_BACKUP_ROOT}/${ts}`)
}

step('4/6 上传到服务器暂存目录')
if (doBackend) {
  scpTo(BACKEND_JAR, `${REMOTE_STAGING_DIR}/recipe-backend.jar.new`)
}
if (doFrontend) {
  // 用相对路径打包，避免 GNU tar 把 Windows 盘符当成远程主机
  const archiveRel = path.join('tmp', `frontend-dist-${ts}.tar.gz`)
  const archiveAbs = path.join(root, archiveRel)
  fs.mkdirSync(path.join(root, 'tmp'), { recursive: true })
  runArgs('tar', ['-czf', archiveRel, '-C', 'frontend/dist', '.'], { cwd: root })
  try {
    scpTo(archiveAbs, `${REMOTE_STAGING_DIR}/frontend-dist.tar.gz`)
  } finally {
    fs.rmSync(archiveAbs, { force: true })
  }
}

if (dryRun) {
  console.log('\ndry-run 完成：文件已上传到服务器暂存目录，未替换线上文件、未重启服务。')
  console.log(`暂存目录：${REMOTE_STAGING_DIR}`)
  process.exit(0)
}

step('5/6 替换线上文件')
if (doBackend) {
  ssh(`mv '${REMOTE_STAGING_DIR}/recipe-backend.jar.new' '${REMOTE_BACKEND_DIR}/${REMOTE_JAR_NAME}' && chmod 644 '${REMOTE_BACKEND_DIR}/${REMOTE_JAR_NAME}'`)
  console.log('后端 jar 已替换')
}
if (doFrontend) {
  ssh(`tar -xzf '${REMOTE_STAGING_DIR}/frontend-dist.tar.gz' -C '${REMOTE_FRONTEND_DIR}' && chown -R root:root '${REMOTE_FRONTEND_DIR}' && rm -f '${REMOTE_STAGING_DIR}/frontend-dist.tar.gz'`)
  console.log('前端静态文件已覆盖')
}
if (doBackend) {
  ssh(`cd '${REMOTE_BACKEND_DIR}' && docker compose up -d --force-recreate`)
}

step('6/6 健康检查')
if (doBackend) {
  console.log('等待后端启动（最多 90 秒）...')
  const healthy = sshTry(`for i in $(seq 1 45); do if curl -fsS -m 2 '${HEALTH_URL}' >/dev/null 2>&1; then echo '后端健康检查通过'; exit 0; fi; sleep 2; done; echo '后端健康检查失败，最近日志：'; docker logs --tail 80 wgxhl-recipe-backend; exit 1`)
  if (!healthy) {
    console.error(`\n回滚命令：\n${rollbackHint(ts)}`)
    fail('后端未能通过健康检查，请根据日志排查后回滚')
  }
}
const publicCheck = spawnSync('curl', ['-fsS', '-m', '10', PUBLIC_URL], { encoding: 'utf8' })
if (publicCheck.status === 0) {
  console.log(`公网健康检查通过：${publicCheck.stdout.trim()}`)
} else {
  console.warn(`公网健康检查失败（curl exit ${publicCheck.status}），请手动确认：${PUBLIC_URL}`)
}

console.log('\n部署完成')
if (doBackend) console.log(`  后端：${REMOTE_BACKEND_DIR}/${REMOTE_JAR_NAME}`)
if (doFrontend) console.log(`  前端：${REMOTE_FRONTEND_DIR}`)
console.log(`  备份：${REMOTE_BACKUP_ROOT}/${ts}`)
console.log(`\n回滚命令：\n${rollbackHint(ts)}`)
console.log('\n提示：如本次涉及数据库表结构变更，请确认已在 MySQL 执行 backend/sql 下对应的 SQL。')
