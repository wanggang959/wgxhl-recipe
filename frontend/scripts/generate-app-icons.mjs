import path from 'node:path'
import { fileURLToPath } from 'node:url'
import sharp from 'sharp'

const publicDir = fileURLToPath(new URL('../public/', import.meta.url))
const source = path.join(publicDir, 'app.png')
const background = '#fffaf2'

for (const [name, size] of [
  ['pwa-icon-192.png', 192],
  ['pwa-icon-512.png', 512],
  ['apple-touch-icon.png', 180],
  ['wechat-share.png', 512],
  ['favicon.png', 64],
]) {
  await sharp(source).flatten({ background }).resize(size, size)
    .png({ compressionLevel: 9 }).toFile(path.join(publicDir, name))
}

// Keep the whole house inside Android's circular maskable safe area.
const inset = await sharp(source).flatten({ background }).resize(360, 360).png().toBuffer()
await sharp({ create: { width: 512, height: 512, channels: 3, background } })
  .composite([{ input: inset, gravity: 'centre' }])
  .removeAlpha()
  .png({ compressionLevel: 9 }).toFile(path.join(publicDir, 'pwa-icon-maskable-512.png'))
