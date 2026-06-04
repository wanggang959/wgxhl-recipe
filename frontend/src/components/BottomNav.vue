<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ActionIcon from './ActionIcon.vue'
import { useUserStore } from '../stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const active = computed(() => {
  if (route.path.startsWith('/recipes') && route.query.pick === 'today') return 'today'
  if (route.path.startsWith('/manage/base')) return '/manage/base'
  if (route.path.startsWith('/favorites')) return '/favorites'
  if (route.path.startsWith('/want')) return '/want'
  if (route.path.startsWith('/todo')) return '/todo'
  if (route.path.startsWith('/profile')) return '/profile'
  return '/recipes'
})

const items = computed(() => [
  { key: '/recipes', path: '/recipes', label: '首页', icon: 'wap-home-o' },
  { key: 'today', path: '/recipes?pick=today', label: '今天吃什么', icon: 'fire-o' },
  { key: '/favorites', path: '/favorites', label: '收藏', icon: 'star-o' },
  { key: '/want', path: '/want', label: '想吃', icon: 'cart-o' },
  { key: '/todo', path: '/todo', label: '待办', icon: 'todo-list-o' },
  { key: '/profile', path: '/profile', label: '我的', icon: 'contact-o' },
])

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
      <ActionIcon v-if="item.icon === 'cart-o'" name="cart" :size="21" />
      <van-icon v-else :name="item.icon" size="21" />
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
  width: min(100vw, var(--app-shell-width));
  height: var(--app-bottom-nav-height);
  transform: translateX(-50%);
  padding: 8px 8px calc(8px + var(--app-nav-safe-bottom));
  background: rgba(255, 250, 242, 0.96);
  border-top: 1px solid var(--app-border);
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(0, 1fr));
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
  justify-content: center;
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
