import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import sharp from 'sharp'

const publicFile = name => new URL(`../public/${name}`, import.meta.url)

test('app icons have the advertised size and opaque backgrounds', async () => {
  const manifest = JSON.parse(readFileSync(publicFile('manifest.webmanifest'), 'utf8'))
  for (const icon of manifest.icons) {
    const url = new URL(icon.src, 'https://app.test')
    assert.equal(url.searchParams.get('v'), 'home-v2')
    const meta = await sharp(readFileSync(publicFile(url.pathname.slice(1)))).metadata()
    assert.equal(`${meta.width}x${meta.height}`, icon.sizes)
    assert.equal(meta.hasAlpha, false)
  }
  for (const [file, size] of [['apple-touch-icon.png', 180], ['favicon.png', 64], ['wechat-share.png', 512]]) {
    const meta = await sharp(readFileSync(publicFile(file))).metadata()
    assert.equal(meta.width, size)
    assert.equal(meta.height, size)
    assert.equal(meta.hasAlpha, false)
  }
  assert.equal(manifest.start_url, '/')
  assert.equal(manifest.icons.find(icon => icon.purpose === 'maskable').sizes, '512x512')
  assert.ok(manifest.icons.some(icon => icon.purpose === 'any'))
})

test('maskable artwork has padding around the house', async () => {
  const { data, info } = await sharp(readFileSync(publicFile('pwa-icon-maskable-512.png')))
    .raw().toBuffer({ resolveWithObject: true })
  for (let y = 0; y < info.height; y++) {
    for (let x = 0; x < info.width; x++) {
      if (x >= 76 && x < 436 && y >= 76 && y < 436) continue
      const index = (y * info.width + x) * info.channels
      assert.deepEqual([...data.subarray(index, index + 3)], [255, 250, 242])
    }
  }
})

test('install and share metadata reference the refreshed artwork', () => {
  const html = readFileSync(new URL('../index.html', import.meta.url), 'utf8')
  assert.match(html, /favicon\.png\?v=home-v2/)
  assert.match(html, /apple-touch-icon\.png\?v=home-v2/)
  assert.match(html, /wechat-share\.png\?v=home-v2/)
  assert.doesNotMatch(html, /favicon\.svg/)
  const main = readFileSync(new URL('../src/main.js', import.meta.url), 'utf8')
  assert.match(main, /wechat-share\.png\?v=home-v2/)
})
