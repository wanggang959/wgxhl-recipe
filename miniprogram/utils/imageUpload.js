const api = require('../services/api')

const PRESETS = {
  cover: { folder: 'recipe/cover', maxEdge: 1600, maxBytes: 512 * 1024 },
  step: { folder: 'recipe/step', maxEdge: 2048, maxBytes: 1024 * 1024 },
  ingredient: { folder: 'ingredient', maxEdge: 2048, maxBytes: 1024 * 1024 },
  seasoning: { folder: 'seasoning', maxEdge: 2048, maxBytes: 1024 * 1024 },
}

function chooseImage(count = 1) {
  return new Promise((resolve, reject) => {
    wx.chooseMedia({
      count,
      mediaType: ['image'],
      sizeType: ['compressed'],
      sourceType: ['album', 'camera'],
      success: result => resolve((result.tempFiles || []).map(item => item.tempFilePath).filter(Boolean)),
      fail: error => {
        if (/cancel/i.test(String(error?.errMsg || ''))) resolve([])
        else reject(new Error('选择图片失败，请重试'))
      },
    })
  })
}

function cropImage(src, kind) {
  return new Promise((resolve, reject) => {
    const unsupported = () => wx.showModal({ title: '无法裁剪图片', content: '当前微信版本不支持图片裁剪，是否继续上传原图？', confirmText: '上传原图', success: ({confirm}) => resolve(confirm ? src : ''), fail: () => reject(new Error('无法打开图片裁剪')) })
    if (typeof wx.cropImage !== 'function') {
      unsupported()
      return
    }
    wx.cropImage({ src, cropScale: kind === 'cover' || kind === 'step' ? '4:3' : '1:1', success: result => resolve(result.tempFilePath || ''), fail: error => { const message=String(error?.errMsg || ''); if (/cancel/i.test(message)) resolve(''); else if (/not support|unsupported/i.test(message)) unsupported(); else reject(new Error('图片裁剪失败，请重试')) } })
  })
}

function fileInfo(filePath) {
  return new Promise((resolve, reject) => {
    wx.getFileSystemManager().getFileInfo({
      filePath,
      success: resolve,
      fail: () => reject(new Error('读取图片信息失败，请重试')),
    })
  })
}

function imageInfo(src) {
  return new Promise((resolve, reject) => {
    wx.getImageInfo({
      src,
      success: resolve,
      fail: () => reject(new Error('读取图片尺寸失败，请重试')),
    })
  })
}

function compressImage(options) {
  return new Promise((resolve, reject) => {
    wx.compressImage({
      ...options,
      success: result => resolve(result.tempFilePath),
      fail: () => reject(new Error('图片压缩失败，请换一张试试')),
    })
  })
}

async function prepareImage(filePath, preset) {
  let info = await imageInfo(filePath)
  let current = filePath
  let width = Number(info.width || 0)
  let height = Number(info.height || 0)
  if (width > preset.maxEdge || height > preset.maxEdge) {
    const options = { src: filePath, quality: 82 }
    if (width >= height) options.compressedWidth = preset.maxEdge
    else options.compressedHeight = preset.maxEdge
    current = await compressImage(options)
    info = await imageInfo(current)
    width = Number(info.width || 0)
    height = Number(info.height || 0)
    if (width > preset.maxEdge || height > preset.maxEdge) {
      throw new Error(`图片边长需控制在 ${preset.maxEdge} 像素以内，请裁剪后再上传`)
    }
  }
  let size = Number((await fileInfo(current)).size || 0)
  for (let attempt = 0; attempt < 4 && size > preset.maxBytes; attempt += 1) {
    const quality = Math.max(40, 82 - attempt * 16)
    current = await compressImage({ src: current, quality })
    size = Number((await fileInfo(current)).size || 0)
  }
  if (size > preset.maxBytes) throw new Error('图片压缩后仍超过大小限制，请选择更小的图片')
  return current
}

async function uploadImage(filePath, kind = 'step') {
  const preset = PRESETS[kind] || PRESETS.step
  const prepared = await prepareImage(filePath, preset)
  return api.upload('/file/upload', prepared, { folder: preset.folder })
}

module.exports = { PRESETS, chooseImage, cropImage, prepareImage, uploadImage }
