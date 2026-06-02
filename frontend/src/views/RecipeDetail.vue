<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showFailToast, showSuccessToast } from 'vant'
import { checkFavorite, createFavorite, deleteFavoriteByRecipeId } from '../api/favorite'
import { getRecipeDetail } from '../api/recipe'
import { useUserStore } from '../stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const detail = ref({
  recipe: {},
  recipeStepList: [],
  ingredientList: [],
  imageList: [],
})
const isFavorite = ref(false)

const recipe = computed(() => detail.value.recipe || {})

onMounted(async () => {
  await loadDetail()
})

async function loadDetail() {
  loading.value = true
  try {
    const res = await getRecipeDetail(route.params.id)
    detail.value = res.data
    if (userStore.userId) {
      const fav = await checkFavorite(userStore.userId, route.params.id)
      isFavorite.value = Boolean(fav.data)
    }
  } catch (error) {
    showFailToast(error.message || '详情加载失败')
  } finally {
    loading.value = false
  }
}

async function toggleFavorite() {
  if (!userStore.userId) {
    showFailToast('请先登录后再收藏')
    return
  }
  try {
    if (isFavorite.value) {
      await deleteFavoriteByRecipeId(userStore.userId, route.params.id)
      showSuccessToast('已取消收藏')
      isFavorite.value = false
    } else {
      await createFavorite({ userId: userStore.userId, recipeId: route.params.id })
      showSuccessToast('收藏成功')
      isFavorite.value = true
    }
  } catch (error) {
    showFailToast(error.message || '收藏操作失败')
  }
}
</script>

<template>
  <div class="page-wrap">
    <section class="card-panel page">
      <van-nav-bar title="菜谱详情" left-arrow @click-left="router.back()">
        <template #right>
          <van-icon
            :name="isFavorite ? 'like' : 'like-o'"
            :color="isFavorite ? '#ef4444' : '#6b7280'"
            size="22"
            @click="toggleFavorite"
          />
        </template>
      </van-nav-bar>

      <van-loading v-if="loading" size="24px" class="loading">加载中...</van-loading>
      <div v-else>
        <img
          class="cover"
          :src="recipe.coverImage || 'https://via.placeholder.com/1000x520?text=No+Image'"
          :alt="recipe.recipeName"
        />
        <div class="summary">
          <h1>{{ recipe.recipeName }}</h1>
          <p>{{ recipe.recipeDesc || '暂无简介' }}</p>
          <div class="tags">
            <van-tag type="warning">{{ recipe.categoryName || '未分类' }}</van-tag>
            <van-tag plain>{{ recipe.difficulty || '普通' }}</van-tag>
            <van-tag plain>{{ recipe.cookingTime || '-' }}</van-tag>
            <van-tag plain>{{ recipe.servingCount || '-' }}</van-tag>
          </div>
        </div>

        <div class="section">
          <h3>食材</h3>
          <van-empty v-if="detail.ingredientList.length === 0" description="暂无食材" />
          <div v-else class="ingredient-grid">
            <div v-for="item in detail.ingredientList" :key="item.id" class="ingredient-item">
              <div class="name">{{ item.ingredientName }}</div>
              <div class="amount">{{ item.amount || '-' }} {{ item.unit || '' }}</div>
            </div>
          </div>
        </div>

        <div class="section">
          <h3>制作步骤</h3>
          <van-empty v-if="detail.recipeStepList.length === 0" description="暂无步骤" />
          <div v-else class="step-list">
            <article v-for="item in detail.recipeStepList" :key="item.id" class="step-card">
              <div class="step-index">{{ item.stepNo }}</div>
              <div class="step-body">
                <div class="step-title">{{ item.stepTitle || `步骤 ${item.stepNo}` }}</div>
                <div class="step-desc">{{ item.stepDesc || '暂无说明' }}</div>
                <img
                  v-if="item.stepImage"
                  :src="item.stepImage"
                  :alt="item.stepTitle || 'step-image'"
                  class="step-image"
                />
              </div>
            </article>
          </div>
        </div>

        <div class="ops">
          <van-button type="warning" block @click="router.push(`/recipe/${route.params.id}/edit`)">
            编辑菜谱
          </van-button>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.page {
  padding-bottom: 20px;
}

.loading {
  margin-top: 24px;
}

.cover {
  width: 100%;
  max-height: 460px;
  object-fit: cover;
}

.summary {
  padding: 14px;
}

h1 {
  margin: 0;
  font-size: 28px;
}

.summary p {
  margin: 8px 0;
  color: #6b7280;
}

.tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.section {
  padding: 0 14px 14px;
}

.section h3 {
  margin: 10px 0;
}

.ingredient-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.ingredient-item {
  border: 1px solid #f0f0f0;
  border-radius: 10px;
  padding: 10px;
}

.ingredient-item .name {
  font-weight: 600;
}

.amount {
  margin-top: 4px;
  color: #6b7280;
}

.step-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.step-card {
  display: flex;
  gap: 10px;
  border: 1px solid #f0f0f0;
  border-radius: 10px;
  padding: 10px;
}

.step-index {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #f59e0b;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
}

.step-body {
  flex: 1;
}

.step-title {
  font-weight: 600;
}

.step-desc {
  margin-top: 6px;
  color: #6b7280;
  white-space: pre-wrap;
}

.step-image {
  margin-top: 8px;
  width: 220px;
  max-width: 100%;
  border-radius: 8px;
}

.ops {
  padding: 14px;
}

@media (max-width: 768px) {
  h1 {
    font-size: 22px;
  }

  .ingredient-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
