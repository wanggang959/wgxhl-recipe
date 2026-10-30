function loadImage(url) {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => resolve(img)
    img.onerror = reject
    img.src = url
  })
}

function calculateTargetSize(width, height, maxLongEdge) {
  const longEdge = Math.max(width, height)
  if (longEdge <= maxLongEdge) {
    return { width, height }
  }
  const ratio = maxLongEdge / longEdge
  return {
    width: Math.max(1, Math.round(width * ratio)),
    height: Math.max(1, Math.round(height * ratio)),
  }
}

export async function canvasToImageBlob(canvas, quality = 0.84) {
  const encode = type => new Promise(resolve => canvas.toBlob(resolve, type, quality))
  const webp = await encode('image/webp')
  if (webp?.type === 'image/webp') return webp
  const jpeg = await encode('image/jpeg')
  if (jpeg?.type === 'image/jpeg') return jpeg
  throw new Error('当前浏览器无法压缩图片，请更换浏览器后重试')
}

export function imageBlobToFile(blob, name) {
  const extension = { 'image/webp': 'webp', 'image/jpeg': 'jpg' }[blob.type]
  if (!extension) throw new Error('图片编码格式不受支持')
  return new File([blob], name.replace(/\.[^.]+$/, '') + '.' + extension, {
    type: blob.type,
    lastModified: Date.now(),
  })
}

export async function transcodeImageToWebp(file, options = {}) {
  const {
    maxLongEdge = 1600,
    quality = 0.84,
    minCompressBytes = 300 * 1024,
    maxBytes = 512 * 1024,
  } = options

  if (!(file instanceof File) || !file.type.startsWith('image/')) {
    return file
  }

  const objectUrl = URL.createObjectURL(file)
  try {
    const img = await loadImage(objectUrl)
    let edge = Math.min(maxLongEdge, Math.max(img.naturalWidth, img.naturalHeight))
    if (file.size <= minCompressBytes && file.size <= maxBytes
        && Math.max(img.naturalWidth, img.naturalHeight) <= maxLongEdge) return file
    const canvas = document.createElement('canvas')
    const ctx = canvas.getContext('2d')
    if (!ctx) throw new Error('当前浏览器无法处理图片')
    const qualities = [...new Set([quality, Math.min(quality, 0.74), Math.min(quality, 0.64)])]
    while (true) {
      const target = calculateTargetSize(img.naturalWidth, img.naturalHeight, edge)
      canvas.width = target.width
      canvas.height = target.height
      ctx.imageSmoothingEnabled = true
      ctx.imageSmoothingQuality = 'high'
      ctx.fillStyle = '#fff'
      ctx.fillRect(0, 0, target.width, target.height)
      ctx.drawImage(img, 0, 0, target.width, target.height)
      for (const q of qualities) {
        const blob = await canvasToImageBlob(canvas, q)
        if (blob.size <= maxBytes) return imageBlobToFile(blob, file.name)
      }
      if (edge <= 480) break
      edge = Math.max(480, Math.floor(edge * 0.8))
    }
    throw new Error('图片压缩后仍然过大，请选择其他图片')
  } finally {
    URL.revokeObjectURL(objectUrl)
  }
}
