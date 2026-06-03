/**
 * 将 public 目录下较大的 PNG 转为 WebP（平衡画质与体积）
 * 用法：node scripts/convert-public-to-webp.mjs
 */
import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'
import sharp from 'sharp'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const publicDir = path.join(__dirname, '..', 'public')
const MIN_BYTES = 150 * 1024
const WEBP_QUALITY = 82
const ALPHA_QUALITY = 82

const files = fs.readdirSync(publicDir).filter((name) => /\.png$/i.test(name))

if (files.length === 0) {
  console.log('public 下没有 PNG 文件。')
  process.exit(0)
}

let totalBefore = 0
let totalAfter = 0

for (const name of files) {
  const input = path.join(publicDir, name)
  const { size } = fs.statSync(input)
  if (size < MIN_BYTES) {
    console.log(`跳过（较小）: ${name} (${(size / 1024).toFixed(1)} KB)`)
    continue
  }

  const output = input.replace(/\.png$/i, '.webp')
  await sharp(input)
    .webp({
      quality: WEBP_QUALITY,
      alphaQuality: ALPHA_QUALITY,
      effort: 4,
    })
    .toFile(output)

  const outSize = fs.statSync(output).size
  totalBefore += size
  totalAfter += outSize
  const ratio = ((1 - outSize / size) * 100).toFixed(1)
  console.log(
    `${name} -> ${path.basename(output)}: ${(size / 1024).toFixed(1)} KB -> ${(outSize / 1024).toFixed(1)} KB (-${ratio}%)`,
  )

  fs.unlinkSync(input)
}

console.log(
  `\n合计: ${(totalBefore / 1024 / 1024).toFixed(2)} MB -> ${(totalAfter / 1024 / 1024).toFixed(2)} MB`,
)
