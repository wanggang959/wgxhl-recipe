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
    width: Math.round(width * ratio),
    height: Math.round(height * ratio),
  }
}

export async function transcodeImageToWebp(file, options = {}) {
  const {
    maxLongEdge = 2048,
    quality = 0.86,
    minCompressBytes = 300 * 1024,
  } = options

  if (!(file instanceof File) || !file.type.startsWith('image/')) {
    return file
  }

  // 小图不做处理，避免无谓耗时与可能的质量损失。
  if (file.size <= minCompressBytes) {
    return file
  }

  const objectUrl = URL.createObjectURL(file)
  try {
    const img = await loadImage(objectUrl)
    const target = calculateTargetSize(img.naturalWidth, img.naturalHeight, maxLongEdge)
    const canvas = document.createElement('canvas')
    canvas.width = target.width
    canvas.height = target.height
    const ctx = canvas.getContext('2d')
    if (!ctx) {
      return file
    }

    ctx.imageSmoothingEnabled = true
    ctx.imageSmoothingQuality = 'high'
    ctx.drawImage(img, 0, 0, target.width, target.height)

    const blob = await new Promise((resolve) => {
      canvas.toBlob(resolve, 'image/webp', quality)
    })

    if (!blob) {
      return file
    }

    const webpName = file.name.replace(/\.[^.]+$/, '') + '.webp'
    const webpFile = new File([blob], webpName, {
      type: 'image/webp',
      lastModified: Date.now(),
    })

    // 如果转码后反而更大，回退原图。
    if (webpFile.size >= file.size) {
      return file
    }

    return webpFile
  } catch (error) {
    return file
  } finally {
    URL.revokeObjectURL(objectUrl)
  }
}
