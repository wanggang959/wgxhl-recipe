<script setup>
import { onActivated, onBeforeUnmount, onDeactivated, onMounted, ref } from 'vue'

const props = defineProps({ loading: Boolean })
const emit = defineEmits(['load'])
const target = ref(null)
let observer
function observe() {
  if (!target.value || observer) return
  observer = new IntersectionObserver((entries) => {
    if (entries.some((entry) => entry.isIntersecting) && !props.loading) emit('load')
  }, { rootMargin: '160px' })
  observer.observe(target.value)
}
function disconnect() { observer?.disconnect(); observer = undefined }
onMounted(observe)
onActivated(observe)
onDeactivated(disconnect)
onBeforeUnmount(disconnect)
</script>

<template>
  <div ref="target" class="load-more">
    <van-loading v-if="loading" size="18px">加载中...</van-loading>
    <button v-else type="button" @click="emit('load')">加载更多</button>
  </div>
</template>

<style scoped>
.load-more { display: flex; justify-content: center; padding: 14px; color: #987967; font-size: 13px; }
.load-more button { background: transparent; border: 0; color: #b7662d; font-size: 13px; padding: 6px 12px; }
</style>
