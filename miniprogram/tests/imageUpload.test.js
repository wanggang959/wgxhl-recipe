const test = require('node:test'), assert = require('node:assert/strict')
const { prepareImage, cropImage, PRESETS } = require('../utils/imageUpload')

function stubWx(options) {
  let compressCalls = 0
  global.wx = {
    getImageInfo: ({ src, success }) => {
      if (/compressed/.test(src)) success({ width: 1600, height: 1200 })
      else success({ width: options.width || 800, height: options.height || 600 })
    },
    getFileSystemManager: () => ({ getFileInfo: ({ success }) => success({ size: options.size || 1000 }) }),
    compressImage: ({ src, compressedWidth, compressedHeight, success }) => {
      compressCalls += 1
      if (options.onCompress) options.onCompress(compressCalls)
      assert.match(src, /tmp/)
      assert.ok(compressedWidth === 1600 || compressedHeight === 1600 || compressedWidth == null)
      success({ tempFilePath: `/tmp/compressed-${compressCalls}.jpg` })
    },
  }
  return () => compressCalls
}

test('small images upload unchanged', async () => {
  stubWx({ width: 800, height: 600, size: 200 * 1024 })
  const path = await prepareImage('/tmp/small.jpg', PRESETS.cover)
  assert.equal(path, '/tmp/small.jpg')
})

test('oversized dimensions are scaled before the size check', async () => {
  let compressed = false
  const calls = stubWx({
    width: 4000,
    height: 3000,
    size: 200 * 1024,
    onCompress: () => { compressed = true },
  })
  const path = await prepareImage('/tmp/big.jpg', PRESETS.cover)
  assert.equal(compressed, true)
  assert.equal(calls(), 1)
  assert.match(path, /compressed-1\.jpg$/)
})

test('oversized files are compressed until they fit', async () => {
  let size = 2 * 1024 * 1024
  const calls = stubWx({
    width: 1200,
    height: 900,
    get size() { return size },
    onCompress: count => { size = count >= 2 ? 400 * 1024 : 1500 * 1024 },
  })
  const path = await prepareImage('/tmp/heavy.jpg', PRESETS.cover)
  assert.equal(calls(), 2)
  assert.match(path, /compressed-2\.jpg$/)
})

test('images that cannot be compressed small enough fail loudly', async () => {
  stubWx({ width: 1200, height: 900, size: 2 * 1024 * 1024, onCompress: () => {} })
  await assert.rejects(prepareImage('/tmp/heavy.jpg', PRESETS.cover), /大小限制/)
})

test('uses the same crop ratios as the web app and respects cancellation',async()=>{let ratio='';global.wx={cropImage:options=>{ratio=options.cropScale;options.success({tempFilePath:'/tmp/cropped.jpg'})}};assert.equal(await cropImage('/tmp/source.jpg','cover'),'/tmp/cropped.jpg');assert.equal(ratio,'4:3');await cropImage('/tmp/source.jpg','ingredient');assert.equal(ratio,'1:1');global.wx.cropImage=options=>options.fail({errMsg:'cropImage:fail cancel'});assert.equal(await cropImage('/tmp/source.jpg','step'),'')})
test('unsupported crop asks before uploading the original',async()=>{global.wx={showModal:options=>options.success({confirm:false})};assert.equal(await cropImage('/tmp/source.jpg','cover'),'');global.wx.showModal=options=>options.success({confirm:true});assert.equal(await cropImage('/tmp/source.jpg','cover'),'/tmp/source.jpg')})
