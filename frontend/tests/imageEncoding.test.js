import test from 'node:test'
import assert from 'node:assert/strict'
import { File } from 'node:buffer'
import { canvasToImageBlob, imageBlobToFile, transcodeImageToWebp } from '../src/utils/image.js'

test('unsupported WebP encoding falls back to a correctly named JPEG', async () => {
  const types = []
  const canvas = { toBlob(callback, type) {
    types.push(type)
    callback(new Blob(['data'], { type: type === 'image/webp' ? 'image/png' : 'image/jpeg' }))
  } }
  const blob = await canvasToImageBlob(canvas)
  assert.equal(blob.type, 'image/jpeg')
  assert.deepEqual(types, ['image/webp', 'image/jpeg'])
  const old = globalThis.File
  globalThis.File = File
  try {
    const file = imageBlobToFile(blob, 'food.webp')
    assert.equal(file.name, 'food.jpg')
    assert.equal(file.type, 'image/jpeg')
    assert.throws(() => imageBlobToFile(new Blob(['png'], { type: 'image/png' }), 'food.webp'))
  } finally { globalThis.File = old }
})

test('supported WebP stays WebP and null/unsupported outputs fail clearly', async () => {
  assert.equal((await canvasToImageBlob({ toBlob: cb => cb(new Blob(['data'], { type: 'image/webp' })) })).type, 'image/webp')
  await assert.rejects(canvasToImageBlob({ toBlob: cb => cb(null) }))
  await assert.rejects(canvasToImageBlob({ toBlob: cb => cb(new Blob(['png'], { type: 'image/png' })) }))
})

function setup(t, { encode, width = 4032, height = 3024, context = true, broken = false } = {}) {
  const old = { File: globalThis.File, Image: globalThis.Image, document: globalThis.document,
    create: URL.createObjectURL, revoke: URL.revokeObjectURL }
  let revoked = false
  const draws = []
  const canvas = {
    getContext: () => context ? { fillRect() {}, drawImage(...args) { draws.push(args.slice(-2)) } } : null,
    toBlob(cb, type, quality) { cb(encode ? encode(canvas, type, quality) : new Blob(['data'], { type })) },
  }
  globalThis.File = File
  globalThis.Image = class {
    naturalWidth = width
    naturalHeight = height
    set src(_) { queueMicrotask(() => broken ? this.onerror(new Error('decode failed')) : this.onload()) }
  }
  globalThis.document = { createElement: () => canvas }
  URL.createObjectURL = () => 'blob:test'
  URL.revokeObjectURL = () => { revoked = true }
  t.after(() => {
    globalThis.File = old.File; globalThis.Image = old.Image; globalThis.document = old.document
    URL.createObjectURL = old.create; URL.revokeObjectURL = old.revoke
  })
  return { draws, revoked: () => revoked }
}

test('compression meets a byte budget by reducing dimensions when needed', async t => {
  const state = setup(t, { encode: (canvas, type) => new Blob([new Uint8Array(Math.ceil(canvas.width * canvas.height / 10))], { type }) })
  const file = await transcodeImageToWebp(new File(['x'.repeat(200000)], 'food.png', { type: 'image/png' }),
    { minCompressBytes: 0, maxLongEdge: 1280, maxBytes: 30000 })
  assert.ok(file.size <= 30000)
  assert.equal(file.type, 'image/webp')
  assert.ok(state.draws.length > 1)
  assert.ok(state.draws.every(([width, height]) => width <= 1280 && height >= 1))
  assert.equal(state.revoked(), true)
})

test('transcoding never returns a mislabeled PNG when the browser only encodes JPEG', async t => {
  setup(t, { encode: (_, type) => new Blob(['data'], { type: type === 'image/webp' ? 'image/png' : 'image/jpeg' }) })
  const result = await transcodeImageToWebp(new File(['input'], 'food.webp', { type: 'image/webp' }), { minCompressBytes: 0 })
  assert.equal(result.name, 'food.jpg')
  assert.equal(result.type, 'image/jpeg')
})

test('failed compression and decoding do not silently upload the original file', async t => {
  const state = setup(t, { encode: (_, type) => new Blob(['x'.repeat(1000)], { type }), width: 800, height: 600 })
  await assert.rejects(transcodeImageToWebp(new File(['input'], 'food.png', { type: 'image/png' }),
    { minCompressBytes: 0, maxBytes: 100 }), /压缩后仍然过大/)
  assert.equal(state.revoked(), true)
})

test('missing canvas or a decode error releases the image URL and fails', async t => {
  const state = setup(t, { context: false })
  await assert.rejects(transcodeImageToWebp(new File(['input'], 'food.png', { type: 'image/png' }), { minCompressBytes: 0 }))
  assert.equal(state.revoked(), true)
})

test('small, already bounded images are preserved', async t => {
  const state = setup(t, { width: 400, height: 300 })
  const input = new File(['input'], 'food.jpg', { type: 'image/jpeg' })
  assert.equal(await transcodeImageToWebp(input), input)
  assert.equal(state.draws.length, 0)
  assert.equal(state.revoked(), true)
})

test('small files with excessive dimensions are still resized', async t => {
  const state = setup(t)
  const input = new File(['input'], 'food.jpg', { type: 'image/jpeg' })
  const output = await transcodeImageToWebp(input, { maxLongEdge: 1280 })
  assert.notEqual(output, input)
  assert.ok(state.draws.length > 0)
  assert.ok(state.draws.every(([width, height]) => width <= 1280 && height <= 1280))
})

test('image decoding failures release the URL and are not ignored', async t => {
  const state = setup(t, { broken: true })
  await assert.rejects(transcodeImageToWebp(new File(['input'], 'food.jpg', { type: 'image/jpeg' })), /decode failed/)
  assert.equal(state.revoked(), true)
})
