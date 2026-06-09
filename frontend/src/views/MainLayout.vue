<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BottomNav from '../components/BottomNav.vue'
import NotificationBell from '../components/NotificationBell.vue'
import { useUserStore } from '../stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const isShowcase = computed(() => route.query.mode === 'showcase')
const isTodayMode = computed(() => route.query.pick === 'today')
</script>

<template>
  <div class="app-shell" :class="{ 'showcase-shell': isShowcase }">
    <header v-if="!isShowcase" class="top-bar">
      <div class="top-bar-row">
        <button class="brand" type="button" @click="router.push('/recipes')">
          <span>王师傅家</span>
          <strong>家庭生活助手</strong>
        </button>
        <div class="top-actions">
          <NotificationBell />
          <button
            v-if="!isTodayMode && userStore.isAdmin"
            class="top-icon-button"
            type="button"
            aria-label="添加菜谱"
            title="添加菜谱"
            @click="router.push('/recipe/create')"
          >
            <van-icon name="plus" size="20" />
          </button>
        </div>
      </div>
    </header>

    <main
      class="page-wrap content"
      :class="{ 'showcase-content': isShowcase, 'profile-shell': route.path === '/profile' && userStore.isAdmin }"
    >
      <router-view v-slot="{ Component, route: viewRoute }">
        <keep-alive>
          <component :is="Component" v-if="viewRoute.meta.keepAlive" :key="viewRoute.name" />
        </keep-alive>
        <component :is="Component" v-if="!viewRoute.meta.keepAlive" :key="viewRoute.fullPath" />
      </router-view>
    </main>

    <BottomNav v-if="!isShowcase" />
  </div>
</template>

<style scoped>
.top-bar {
  position: sticky;
  top: 0;
  z-index: 30;
  width: min(100vw, var(--app-shell-width));
  margin: 0 auto;
  min-height: var(--app-top-bar-height);
  padding: var(--safe-area-top) 12px 0;
  background: rgba(255, 250, 242, 0.9);
  border-bottom: 1px solid rgba(245, 223, 199, 0.72);
  backdrop-filter: blur(14px);
}

.top-bar-row {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.brand {
  min-width: 0;
  border: 0;
  background: transparent;
  padding: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  color: var(--app-text);
}

.brand span {
  font-size: 12px;
  color: var(--app-primary);
  font-weight: 700;
}

.brand strong {
  font-size: 18px;
  line-height: 1.2;
  white-space: nowrap;
}

.top-actions {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 8px;
}

.top-icon-button {
  width: 36px;
  height: 36px;
  min-width: 36px;
  border: 1px solid var(--app-primary);
  border-radius: 50%;
  padding: 0;
  background: var(--app-primary);
  color: #fff;
  display: inline-grid;
  place-items: center;
  line-height: 1;
  vertical-align: middle;
  appearance: none;
  -webkit-appearance: none;
  box-shadow: 0 8px 16px rgba(249, 115, 22, 0.18);
}

.top-icon-button:active {
  transform: scale(0.96);
}

.content {
  padding-top: 10px;
}

.showcase-shell {
  width: min(100%, 1180px);
  background: #fff7ed;
}

.showcase-content {
  width: min(100%, 1180px);
  padding: 0;
}

.profile-shell {
  min-height: calc(100dvh - var(--app-top-bar-height) - var(--app-bottom-nav-height));
  max-height: calc(100dvh - var(--app-top-bar-height) - var(--app-bottom-nav-height));
  overflow-x: hidden;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  padding-bottom: 10px;
  display: flex;
  flex-direction: column;
}
</style>
