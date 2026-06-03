/**
 * 打包前后端并复制到 deploy 目录（供 1Panel / Docker 部署）
 * 用法：node scripts/pack-deploy.mjs
 */
import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'
import { execSync } from 'child_process'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const root = path.join(__dirname, '..')

const backendJarName = 'recipe-backend-1.0.0-SNAPSHOT.jar'
const backendJarSrc = path.join(root, 'backend', 'target', backendJarName)
const backendJarDest = path.join(root, 'deploy', 'backend', backendJarName)
const frontendDistSrc = path.join(root, 'frontend', 'dist')
const frontendDistDest = path.join(root, 'deploy', 'frontend', 'dist')

function run(cmd, cwd) {
  console.log(`> ${cmd}`)
  execSync(cmd, { cwd, stdio: 'inherit', shell: true })
}

function copyFile(src, dest) {
  fs.mkdirSync(path.dirname(dest), { recursive: true })
  fs.copyFileSync(src, dest)
}

function copyDir(src, dest) {
  fs.rmSync(dest, { recursive: true, force: true })
  fs.cpSync(src, dest, { recursive: true })
}

console.log('\n[1/4] 打包后端...')
run('mvn clean package -DskipTests', path.join(root, 'backend'))

console.log('\n[2/4] 打包前端...')
run('npm run build', path.join(root, 'frontend'))

if (!fs.existsSync(backendJarSrc)) {
  console.error(`未找到后端 jar：${backendJarSrc}`)
  process.exit(1)
}
if (!fs.existsSync(frontendDistSrc)) {
  console.error(`未找到前端 dist：${frontendDistSrc}`)
  process.exit(1)
}

console.log('\n[3/4] 复制到 deploy/backend ...')
copyFile(backendJarSrc, backendJarDest)

console.log('[4/4] 复制到 deploy/frontend/dist ...')
copyDir(frontendDistSrc, frontendDistDest)

const jarKb = (fs.statSync(backendJarDest).size / 1024).toFixed(1)
const distFiles = fs.readdirSync(frontendDistDest, { recursive: true }).length

console.log('\n部署产物已就绪：')
console.log(`  - deploy/backend/${backendJarName} (${jarKb} KB)`)
console.log(`  - deploy/frontend/dist/ (${distFiles} 项)`)
