<script setup>
import { computed, nextTick, reactive, ref } from 'vue'

const visible = ref(false)
const frameRef = ref(null)
const sourceUrl = ref('')
const fileName = ref('')
const aspectRatio = ref(1)
const imageSize = reactive({
  width: 0,
  height: 0,
})
const transform = reactive({
  scale: 1,
  offsetX: 0,
  offsetY: 0,
})
const dragState = reactive({
  dragging: false,
  startX: 0,
  startY: 0,
  startOffsetX: 0,
  startOffsetY: 0,
})

let resolver = null

const frameStyle = computed(() => ({
  aspectRatio: String(aspectRatio.value),
}))

const imageStyle = computed(() => {
  const { width, height } = getDisplaySize()
  return {
    width: `${width}px`,
    height: `${height}px`,
    left: `calc(50% - ${width / 2 - transform.offsetX}px)`,
    top: `calc(50% - ${height / 2 - transform.offsetY}px)`,
  }
})

function open(file, options = {}) {
  return new Promise((resolve, reject) => {
    if (!(file instanceof File) || !file.type.startsWith('image/')) {
      resolve(file)
      return
    }

    cleanup()
    resolver = resolve
    fileName.value = file.name
    aspectRatio.value = options.aspectRatio || 1
    sourceUrl.value = URL.createObjectURL(file)
    visible.value = true

    const img = new Image()
    img.onload = async () => {
      imageSize.width = img.naturalWidth
      imageSize.height = img.naturalHeight
      await nextTick()
      resetTransform()
    }
    img.onerror = () => {
      close()
      reject(new Error('图片读取失败'))
    }
    img.src = sourceUrl.value
  })
}

function getFrameSize() {
  const rect = frameRef.value?.getBoundingClientRect()
  return {
    width: rect?.width || 0,
    height: rect?.height || 0,
  }
}

function getBaseScale() {
  const frame = getFrameSize()
  if (!frame.width || !frame.height || !imageSize.width || !imageSize.height) {
    return 1
  }
  return Math.max(frame.width / imageSize.width, frame.height / imageSize.height)
}

function getDisplaySize() {
  const sizeScale = getBaseScale() * transform.scale
  return {
    width: imageSize.width * sizeScale,
    height: imageSize.height * sizeScale,
  }
}

function resetTransform() {
  transform.scale = 1
  transform.offsetX = 0
  transform.offsetY = 0
  constrainOffset()
}

function constrainOffset() {
  const frame = getFrameSize()
  const display = getDisplaySize()
  const maxX = Math.max(0, (display.width - frame.width) / 2)
  const maxY = Math.max(0, (display.height - frame.height) / 2)
  transform.offsetX = Math.min(maxX, Math.max(-maxX, transform.offsetX))
  transform.offsetY = Math.min(maxY, Math.max(-maxY, transform.offsetY))
}

function getPoint(event) {
  const touch = event.touches?.[0] || event.changedTouches?.[0]
  return {
    x: touch?.clientX ?? event.clientX,
    y: touch?.clientY ?? event.clientY,
  }
}

function startDrag(event) {
  event.preventDefault()
  const point = getPoint(event)
  dragState.dragging = true
  dragState.startX = point.x
  dragState.startY = point.y
  dragState.startOffsetX = transform.offsetX
  dragState.startOffsetY = transform.offsetY
}

function moveDrag(event) {
  if (!dragState.dragging) return
  event.preventDefault()
  const point = getPoint(event)
  transform.offsetX = dragState.startOffsetX + point.x - dragState.startX
  transform.offsetY = dragState.startOffsetY + point.y - dragState.startY
  constrainOffset()
}

function endDrag() {
  dragState.dragging = false
}

function handleScaleChange() {
  constrainOffset()
}

async function confirmCrop() {
  const frame = getFrameSize()
  const display = getDisplaySize()
  if (!frame.width || !frame.height || !display.width || !display.height) {
    resolveAndClose(null)
    return
  }

  const left = (frame.width - display.width) / 2 + transform.offsetX
  const top = (frame.height - display.height) / 2 + transform.offsetY
  const sx = Math.max(0, (-left / display.width) * imageSize.width)
  const sy = Math.max(0, (-top / display.height) * imageSize.height)
  const sw = Math.min(imageSize.width - sx, (frame.width / display.width) * imageSize.width)
  const sh = Math.min(imageSize.height - sy, (frame.height / display.height) * imageSize.height)

  const outputWidth = Math.round(Math.min(1600, sw))
  const outputHeight = Math.round(outputWidth / aspectRatio.value)
  const canvas = document.createElement('canvas')
  canvas.width = outputWidth
  canvas.height = outputHeight
  const ctx = canvas.getContext('2d')
  if (!ctx) {
    resolveAndClose(null)
    return
  }

  const img = new Image()
  img.onload = async () => {
    ctx.imageSmoothingEnabled = true
    ctx.imageSmoothingQuality = 'high'
    ctx.drawImage(img, sx, sy, sw, sh, 0, 0, outputWidth, outputHeight)
    const blob = await new Promise((resolve) => {
      canvas.toBlob(resolve, 'image/webp', 0.92)
    })
    if (!blob) {
      resolveAndClose(null)
      return
    }

    const croppedName = fileName.value.replace(/\.[^.]+$/, '') + '.webp'
    resolveAndClose(new File([blob], croppedName, {
      type: 'image/webp',
      lastModified: Date.now(),
    }))
  }
  img.onerror = () => resolveAndClose(null)
  img.src = sourceUrl.value
}

function cancelCrop() {
  resolveAndClose(null)
}

function resolveAndClose(value) {
  if (resolver) {
    resolver(value)
  }
  close()
}

function close() {
  visible.value = false
  resolver = null
  dragState.dragging = false
  cleanup()
}

function cleanup() {
  if (sourceUrl.value) {
    URL.revokeObjectURL(sourceUrl.value)
  }
  sourceUrl.value = ''
}

defineExpose({
  open,
})
</script>

<template>
  <van-popup v-model:show="visible" position="bottom" round :style="{ height: '86vh' }">
    <div class="cropper">
      <div class="cropper-header">
        <strong>裁剪图片</strong>
        <span>拖动图片调整位置，滑动缩放</span>
      </div>
      <div
        ref="frameRef"
        class="crop-frame"
        :style="frameStyle"
        @mousedown="startDrag"
        @mousemove="moveDrag"
        @mouseup="endDrag"
        @mouseleave="endDrag"
        @touchstart="startDrag"
        @touchmove="moveDrag"
        @touchend="endDrag"
      >
        <img v-if="sourceUrl" class="crop-image" :src="sourceUrl" :style="imageStyle" alt="待裁剪图片" draggable="false" />
        <div class="crop-mask"></div>
      </div>
      <div class="crop-controls">
        <span>缩放</span>
        <input v-model.number="transform.scale" type="range" min="1" max="3" step="0.01" @input="handleScaleChange" />
      </div>
      <div class="crop-actions">
        <van-button @click="cancelCrop">取消</van-button>
        <van-button type="warning" @click="confirmCrop">确认裁剪并上传</van-button>
      </div>
    </div>
  </van-popup>
</template>

<style scoped>
.cropper {
  height: 100%;
  padding: 16px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.cropper-header {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.cropper-header span {
  color: #6b7280;
  font-size: 13px;
}

.crop-frame {
  width: min(86vw, 460px);
  max-height: 58vh;
  margin: 0 auto;
  position: relative;
  overflow: hidden;
  background: #111827;
  border-radius: 14px;
  touch-action: none;
  cursor: grab;
}

.crop-frame:active {
  cursor: grabbing;
}

.crop-image {
  position: absolute;
  user-select: none;
  pointer-events: none;
}

.crop-mask {
  position: absolute;
  inset: 0;
  border: 2px solid rgba(255, 255, 255, 0.92);
  box-shadow: inset 0 0 0 9999px rgba(0, 0, 0, 0.08);
  pointer-events: none;
}

.crop-controls {
  display: grid;
  grid-template-columns: 44px 1fr;
  align-items: center;
  gap: 10px;
  color: #4b5563;
  font-size: 14px;
}

.crop-controls input {
  width: 100%;
}

.crop-actions {
  margin-top: auto;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
