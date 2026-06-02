<script setup>
import { computed } from 'vue'
import { getImageUrl } from '../utils/imageUrl'

const props = defineProps({
  modelValue: {
    type: String,
    default: '',
  },
  label: {
    type: String,
    default: '图片',
  },
  disabled: {
    type: Boolean,
    default: false,
  },
  size: {
    type: Number,
    default: 92,
  },
})

const emit = defineEmits(['upload'])

const preview = computed(() => getImageUrl(props.modelValue))
</script>

<template>
  <div class="image-uploader">
    <div class="label">{{ label }}</div>
    <div class="row">
      <van-image
        v-if="preview"
        :width="size"
        :height="size"
        fit="cover"
        radius="12"
        :src="preview"
      />
      <van-uploader
        :after-read="(file) => emit('upload', file)"
        accept="image/*"
        :disabled="disabled"
        :max-count="1"
      />
    </div>
  </div>
</template>

<style scoped>
.image-uploader {
  margin: 10px 0 12px;
}

.label {
  margin-bottom: 7px;
  color: var(--app-muted);
  font-size: 13px;
}

.row {
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>
