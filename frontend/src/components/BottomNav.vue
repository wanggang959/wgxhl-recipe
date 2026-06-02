<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const active = computed(() => {
  if (route.path.startsWith('/recipes') && route.query.pick === 'today') return 'today'
  if (route.path.startsWith('/manage/base')) return '/manage/base'
  if (route.path.startsWith('/favorites')) return '/favorites'
  if (route.path.startsWith('/profile')) return '/profile'
  return '/recipes'
})

const items = [
  { key: '/recipes', path: '/recipes', label: '首页', icon: 'wap-home-o' },
  { key: '/manage/base', path: '/manage/base', label: '分类', icon: 'apps-o' },
  { key: 'today', path: '/recipes?pick=today', label: '今天吃什么', icon: 'fire-o' },
  { key: '/favorites', path: '/favorites', label: '收藏', icon: 'star-o' },
  { key: '/profile', path: '/profile', label: '我的', icon: 'contact-o' },
]

function go(item) {
  if (item.key === 'today') {
    router.push({
      path: '/recipes',
      query: {
        pick: 'today',
        t: Date.now(),
      },
    })
    return
  }
  router.push(item.path)
}
</script>

<template>
  <nav class="bottom-nav">
    <button
      v-for="item in items"
      :key="item.path"
      class="bottom-item"
      :class="{ active: active === item.key }"
      type="button"
      @click="go(item)"
    >
      <van-icon :name="item.icon" size="21" />
      <span>{{ item.label }}</span>
    </button>
  </nav>
</template>

<style scoped>
.bottom-nav {
  position: fixed;
  left: 50%;
  bottom: 0;
  z-index: 50;
  width: min(100%, 430px);
  transform: translateX(-50%);
  padding: 8px 8px max(8px, env(safe-area-inset-bottom));
  background: rgba(255, 250, 242, 0.96);
  border-top: 1px solid var(--app-border);
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  box-shadow: 0 -10px 28px rgba(154, 52, 18, 0.1);
  backdrop-filter: blur(14px);
}

.bottom-item {
  min-width: 0;
  border: 0;
  background: transparent;
  color: #9b7d66;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  padding: 5px 2px;
  font-size: 11px;
}

.bottom-item.active {
  color: var(--app-primary);
  font-weight: 700;
}

.bottom-item span {
  line-height: 1.2;
  white-space: nowrap;
}
</style>
