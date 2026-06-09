<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { closeToast, showConfirmDialog, showFailToast, showSuccessToast } from 'vant'
import { checkFavorite, createFavorite, deleteFavoriteByRecipeId } from '../api/favorite'
import { pageIngredient } from '../api/ingredient'
import { deleteRecipe, getRecipeDetail } from '../api/recipe'
import { pageSeasoning } from '../api/seasoning'
import RecipeDetail from '../components/RecipeDetail.vue'
import { useUserStore } from '../stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const detail = ref({
  recipe: {},
  recipeStepList: [],
  ingredientList: [],
  seasoningList: [],
  imageList: [],
})
const isFavorite = ref(false)
const favoritePending = ref(false)
const errorText = ref('')

onMounted(async () => {
  await loadDetail()
})

async function loadDetail() {
  loading.value = true
  errorText.value = ''
  try {
    const res = await getRecipeDetail(route.params.id)
    detail.value = res.data
    await mergeIngredientImages()
    await mergeSeasoningImages()
    if (userStore.userId) {
      const fav = await checkFavorite(userStore.userId, route.params.id)
      isFavorite.value = Boolean(fav.data)
    }
  } catch (error) {
    errorText.value = error.message || '详情加载失败'
    showFailToast(errorText.value)
  } finally {
    loading.value = false
  }
}

async function mergeSeasoningImages() {
  const seasoningList = detail.value.seasoningList || []
  const needsImage = seasoningList.some((item) => item.seasoningId && !item.seasoningImage)
  if (!needsImage) {
    return
  }
  const res = await pageSeasoning({ current: 1, size: 1000 })
  const imageMap = new Map((res.data.records || []).map((item) => [item.id, item.seasoningImage]))
  detail.value = {
    ...detail.value,
    seasoningList: seasoningList.map((item) => ({
      ...item,
      seasoningImage: item.seasoningImage || imageMap.get(item.seasoningId) || '',
    })),
  }
}

async function mergeIngredientImages() {
  const ingredientList = detail.value.ingredientList || []
  const needsImage = ingredientList.some((item) => item.ingredientId && !item.ingredientImage)
  if (!needsImage) {
    return
  }
  const res = await pageIngredient({ current: 1, size: 1000 })
  const imageMap = new Map((res.data.records || []).map((item) => [item.id, item.ingredientImage]))
  detail.value = {
    ...detail.value,
    ingredientList: ingredientList.map((item) => ({
      ...item,
      ingredientImage: item.ingredientImage || imageMap.get(item.ingredientId) || '',
    })),
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

async function toggleFavoriteSafe() {
  if (!userStore.userId) {
    closeToast()
    showFailToast({ message: '请先登录后再收藏', duration: 1800 })
    return
  }
  if (favoritePending.value) return
  favoritePending.value = true
  try {
    if (isFavorite.value) {
      await deleteFavoriteByRecipeId(userStore.userId, route.params.id)
      isFavorite.value = false
      closeToast()
      showSuccessToast({ message: '已取消收藏', duration: 1400 })
    } else {
      await createFavorite({ userId: userStore.userId, recipeId: route.params.id })
      isFavorite.value = true
      closeToast()
      showSuccessToast({ message: '收藏成功', duration: 1400 })
    }
  } catch (error) {
    closeToast()
    showFailToast({ message: error.message || '收藏操作失败', duration: 1800 })
  } finally {
    favoritePending.value = false
  }
}

async function removeRecipe() {
  const name = detail.value.recipe?.recipeName || '这道菜'
  try {
    await showConfirmDialog({
      title: '删除菜谱',
      message: `确认删除「${name}」吗？删除后无法恢复。`,
    })
    await deleteRecipe(route.params.id)
    showSuccessToast('菜谱已删除')
    router.replace('/')
  } catch (error) {
    if (error?.message) showFailToast(error.message)
  }
}
</script>

<template>
  <div class="app-shell">
    <main class="page-wrap detail-page">
      <van-loading v-if="loading" size="24px" class="loading">加载中...</van-loading>
      <div v-else-if="errorText" class="error-card">
        <span>{{ errorText }}</span>
        <van-button size="small" plain type="warning" @click="loadDetail">重试</van-button>
      </div>
      <RecipeDetail
        v-else
        :detail="detail"
        :favorite="isFavorite"
        :can-edit="userStore.isAdmin"
        :show-favorite="userStore.canMutate"
        :show-todo-action="userStore.canMutate"
        @back="router.back()"
        @favorite="toggleFavoriteSafe"
        @todo="router.push({ path: '/todo/create', query: { title: detail.recipe?.recipeName, category: 'COOK', relatedType: 'RECIPE', relatedId: detail.recipe?.id } })"
        @edit="router.push(`/recipe/${route.params.id}/edit`)"
        @delete="removeRecipe"
      />
    </main>
  </div>
</template>

<style scoped>
.detail-page {
  min-height: 100vh;
}

.loading {
  margin-top: 40px;
}

.error-card {
  margin-top: 16px;
  padding: 12px;
  border-radius: 16px;
  background: #fff1f2;
  color: #be123c;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
</style>
