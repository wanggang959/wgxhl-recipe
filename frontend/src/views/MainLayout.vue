<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BottomNav from '../components/BottomNav.vue'
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
      <button class="brand" type="button" @click="router.push('/recipes')">
        <span>王刚家</span>
        <strong>家常菜谱</strong>
      </button>
      <van-button
        v-if="!isTodayMode && userStore.isAdmin"
        round
        type="warning"
        size="small"
        icon="plus"
        @click="router.push('/recipe/create')"
      >
        添加
      </van-button>
    </header>

    <main
      class="page-wrap content"
      :class="{ 'showcase-content': isShowcase, 'profile-shell': route.path === '/profile' && userStore.isAdmin }"
    >
      <router-view />
    </main>

    <BottomNav v-if="!isShowcase" />
  </div>
</template>

<style scoped>
.top-bar {
  position: sticky;
  top: 0;
  z-index: 30;
  width: min(100%, 430px);
  margin: 0 auto;
  padding: 10px 12px;
  background: rgba(255, 250, 242, 0.9);
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid rgba(245, 223, 199, 0.72);
  backdrop-filter: blur(14px);
}

.brand {
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
  height: calc(100dvh - 52px - 72px);
  max-height: calc(100dvh - 52px - 72px);
  overflow: hidden;
  padding-bottom: 10px;
  display: flex;
  flex-direction: column;
}
</style>
