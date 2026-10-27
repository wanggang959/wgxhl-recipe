<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { closeToast, showConfirmDialog, showFailToast, showSuccessToast } from 'vant'
import { checkFavorite, createFavorite, deleteFavoriteByRecipeId } from '../api/favorite'
import { deleteRecipe, getRecipeDetail, setRecipeStatus } from '../api/recipe'
import RecipeDetail from '../components/RecipeDetail.vue'
import { useUserStore } from '../stores/user'
import { DATA_SCOPE, markDataChanged, markDataStale } from '../utils/dataRefresh'

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
const isOwner = computed(() => Boolean(userStore.userId && detail.value.recipe?.ownerUserId === userStore.userId))
const canTakeDown = computed(() => {
  if (!userStore.canMutate) return false
  return isOwner.value || userStore.isHouseholdAdmin || userStore.isSuperAdmin
})

onMounted(async () => {
  await loadDetail()
})

async function loadDetail() {
  loading.value = true
  errorText.value = ''
  try {
    const res = await getRecipeDetail(route.params.id)
    detail.value = res.data
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
      markDataStale(DATA_SCOPE.favorites)
    } else {
      await createFavorite({ userId: userStore.userId, recipeId: route.params.id })
      showSuccessToast('收藏成功')
      isFavorite.value = true
      markDataStale(DATA_SCOPE.favorites)
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
      markDataStale(DATA_SCOPE.favorites)
      closeToast()
      showSuccessToast({ message: '已取消收藏', duration: 1400 })
    } else {
      await createFavorite({ userId: userStore.userId, recipeId: route.params.id })
      isFavorite.value = true
      markDataStale(DATA_SCOPE.favorites)
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

async function toggleRecipeStatus() {
  const offline = detail.value.recipe?.status === '下架'
  const name = detail.value.recipe?.recipeName || '这道菜'
  try {
    await showConfirmDialog({
      title: offline ? '恢复上架' : '下架菜谱',
      message: offline
        ? `确认把「${name}」恢复到家庭菜谱吗？`
        : `下架后家人将看不到「${name}」，只有作者和家庭管理员还能打开。`,
    })
    await setRecipeStatus({ id: route.params.id, status: offline ? '上架' : '下架' })
    markDataStale(DATA_SCOPE.recipes)
    showSuccessToast(offline ? '已恢复上架' : '已下架')
    await loadDetail()
  } catch (error) {
    if (error?.message) showFailToast(error.message)
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
    markDataChanged([DATA_SCOPE.recipes, DATA_SCOPE.favorites, DATA_SCOPE.wanted, DATA_SCOPE.todos])
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
        :can-edit="isOwner"
        :can-take-down="canTakeDown"
        :show-favorite="userStore.canMutate"
        :show-todo-action="userStore.canMutate"
        @back="router.back()"
        @favorite="toggleFavoriteSafe"
        @todo="router.push({ path: '/todo/create', query: { title: detail.recipe?.recipeName, category: 'COOK', relatedType: 'RECIPE', relatedId: detail.recipe?.id } })"
        @edit="router.push(`/recipe/${route.params.id}/edit`)"
        @delete="removeRecipe"
        @toggle-status="toggleRecipeStatus"
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
