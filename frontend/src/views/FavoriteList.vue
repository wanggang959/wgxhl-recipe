<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showFailToast, showSuccessToast } from 'vant'
import { deleteFavorite, pageFavorite } from '../api/favorite'
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
    list.value = res.data.records || []
  } catch (error) {
    showFailToast(error.message || '收藏加载失败')
  } finally {
    loading.value = false
  }
}

async function remove(item) {
  try {
    await deleteFavorite(item.id)
    showSuccessToast('已取消收藏')
    await loadFavorite()
  } catch (error) {
    showFailToast(error.message || '操作失败')
  }
}
</script>

<template>
  <section class="card-panel page">
    <div class="header">
      <h3>我的收藏</h3>
      <span v-if="userStore.userId">共 {{ list.length }} 条</span>
    </div>

    <van-empty
      v-if="!userStore.userId"
      description="请先在“我的”页面登录"
      class="empty-box"
    />
    <van-loading v-else-if="loading" size="24px" class="loading">加载中...</van-loading>
    <van-empty v-else-if="list.length === 0" description="暂无收藏" class="empty-box" />
    <div v-else class="grid">
      <article v-for="item in list" :key="item.id" class="favorite-card">
        <img
          :src="item.coverImage || 'https://via.placeholder.com/300x220?text=No+Image'"
          class="cover"
          :alt="item.recipeName"
          @click="router.push(`/recipe/${item.recipeId}`)"
        />
        <div class="body">
          <div class="name" @click="router.push(`/recipe/${item.recipeId}`)">
            {{ item.recipeName }}
          </div>
          <div class="ops">
            <van-button size="small" plain type="primary" @click="router.push(`/recipe/${item.recipeId}`)">
              查看
            </van-button>
            <van-button size="small" plain type="danger" @click="remove(item)">
              取消收藏
            </van-button>
          </div>
        </div>
      </article>
    </div>
  </section>
</template>

<style scoped>
.page {
  padding: 16px;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.header h3 {
  margin: 0;
  font-size: 20px;
}

.header span {
  font-size: 13px;
  color: #9ca3af;
}

.loading {
  margin-top: 30px;
}

.grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.favorite-card {
  border: 1px solid #f0f0f0;
  border-radius: 12px;
  overflow: hidden;
}

.cover {
  width: 100%;
  height: 160px;
  object-fit: cover;
  cursor: pointer;
}

.body {
  padding: 10px;
}

.name {
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
}

.ops {
  margin-top: 10px;
  display: flex;
  gap: 8px;
}

@media (max-width: 1024px) {
  .grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .grid {
    grid-template-columns: 1fr;
  }
}
</style>
