<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const active = computed(() => {
  if (route.path.startsWith('/favorites')) return '/favorites'
  if (route.path.startsWith('/profile')) return '/profile'
  return '/recipes'
})

const tabs = [
  { path: '/recipes', label: '首页', icon: 'wap-home-o' },
  { path: '/favorites', label: '收藏', icon: 'star-o' },
  { path: '/profile', label: '我的', icon: 'contact-o' },
]

function goCreate() {
  router.push('/recipe/create')
}

const desktopNavs = [
  { path: '/recipes', label: '首页' },
  { path: '/manage/base', label: '分类' },
  { path: '/favorites', label: '收藏' },
  { path: '/profile', label: '关于我' },
]
</script>

<template>
  <div class="layout">
    <header class="header card-panel page-wrap">
      <div class="left">
        <div class="brand">
          <div class="title">家常食谱</div>
          <div class="sub-title">记录家的味道</div>
        </div>
        <nav class="desktop-nav">
          <a
            v-for="item in desktopNavs"
            :key="item.path"
            href="javascript:void(0)"
            :class="{ active: route.path.startsWith(item.path) }"
            @click="router.push(item.path)"
          >
            {{ item.label }}
          </a>
        </nav>
      </div>
      <div class="right">
        <div class="search-box">
          <van-icon name="search" size="15" />
          <span>可以搜菜名和食材</span>
        </div>
        <van-button type="warning" size="small" round @click="goCreate">
          + 添加菜谱
        </van-button>
      </div>
    </header>

    <main class="page-wrap content">
      <router-view />
    </main>

    <van-tabbar
      route
      fixed
      placeholder
      class="mobile-tabbar"
      active-color="#f59e0b"
      inactive-color="#6b7280"
      :model-value="active"
    >
      <van-tabbar-item
        v-for="item in tabs"
        :key="item.path"
        :to="item.path"
        :icon="item.icon"
      >
        {{ item.label }}
      </van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<style scoped>
.layout {
  min-height: 100vh;
}

.header {
  margin-top: 10px;
  padding: 14px 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.left,
.right {
  display: flex;
  align-items: center;
  gap: 18px;
}

.title {
  font-size: 21px;
  font-weight: 700;
  color: #111827;
}

.sub-title {
  font-size: 12px;
  color: #9ca3af;
}

.desktop-nav {
  display: flex;
  align-items: center;
  gap: 18px;
}

.desktop-nav a {
  color: #6b7280;
  text-decoration: none;
  font-size: 14px;
}

.desktop-nav a.active {
  color: #f59e0b;
  font-weight: 600;
}

.search-box {
  width: 220px;
  height: 34px;
  border-radius: 17px;
  background: #f3f4f6;
  color: #9ca3af;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 12px;
  font-size: 12px;
}

.content {
  padding-top: 12px;
  padding-bottom: 80px;
}

@media (min-width: 1024px) {
  .mobile-tabbar {
    display: none;
  }

  .content {
    padding-bottom: 24px;
  }
}

@media (max-width: 1024px) {
  .desktop-nav,
  .search-box {
    display: none;
  }

  .header {
    position: sticky;
    top: 0;
    z-index: 20;
  }

  .left,
  .right {
    gap: 10px;
  }

  .title {
    font-size: 18px;
  }
}

@media (max-width: 640px) {
  .sub-title {
    display: none;
  }
}
</style>
