<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { closeToast, showFailToast, showSuccessToast } from 'vant'
import { deleteFavorite, pageFavorite } from '../api/favorite'
import EmptyState from '../components/EmptyState.vue'
import RecipeCard from '../components/RecipeCard.vue'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const list = ref([])

onMounted(() => {
  loadFavorite()
})

async function loadFavorite() {
  if (!userStore.userId) {
    list.value = []
    return
  }
  loading.value = true
  try {
    const res = await pageFavorite({
      current: 1,
      size: 200,
      userId: userStore.userId,
    })
    list.value = (res.data.records || []).map((item) => ({
      ...item,
      id: item.recipeId,
      favoriteId: item.id,
    }))
  } catch (error) {
    showFailToast(error.message || '收藏加载失败')
  } finally {
    loading.value = false
  }
}

async function remove(item) {
  try {
    await deleteFavorite(item.favoriteId)
    showSuccessToast('已取消收藏')
    await loadFavorite()
  } catch (error) {
    showFailToast(error.message || '操作失败')
  }
}

async function removeSafe(item) {
  try {
    await deleteFavorite(item.favoriteId)
    closeToast()
    showSuccessToast({ message: '已取消收藏', duration: 1400 })
    await loadFavorite()
  } catch (error) {
    closeToast()
    showFailToast({ message: error.message || '操作失败', duration: 1800 })
  }
}
</script>

<template>
  <section class="favorite-page">
    <div class="page-head">
      <div>
        <p>我的菜单</p>
        <h1>收藏</h1>
      </div>
      <span v-if="userStore.userId">{{ list.length }} 道</span>
    </div>

    <EmptyState
      v-if="!userStore.userId"
      text="请先在“我的”页面登录，再收藏家里的拿手菜"
      button-text="去登录"
      @action="router.push('/profile')"
    />
    <van-loading v-else-if="loading" size="24px" class="loading">加载中...</van-loading>
    <EmptyState
      v-else-if="list.length === 0"
      text="还没有收藏菜谱，先去菜单里挑一道吧"
      button-text="去首页"
      @action="router.push('/recipes')"
    />
    <div v-else class="cards">
      <RecipeCard
        v-for="item in list"
        :key="item.favoriteId"
        :recipe="item"
        favorite
        @open="router.push(`/recipe/${item.id}`)"
        @favorite="removeSafe"
      />
    </div>
  </section>
</template>

<style scoped>
.favorite-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.page-head {
  padding: 16px;
  border-radius: 20px;
  background: #fff;
  border: 1px solid var(--app-border);
  display: flex;
  justify-content: space-between;
  align-items: end;
}

.page-head p,
.page-head h1 {
  margin: 0;
}

.page-head p {
  color: var(--app-primary);
  font-size: 13px;
  font-weight: 800;
}

.page-head h1 {
  margin-top: 2px;
  color: var(--app-text);
  font-size: 26px;
}

.page-head span {
  color: var(--app-muted);
  font-size: 13px;
}

.loading {
  margin-top: 30px;
}

.cards {
  display: grid;
  grid-template-columns: 1fr;
  gap: 14px;
}
</style>
