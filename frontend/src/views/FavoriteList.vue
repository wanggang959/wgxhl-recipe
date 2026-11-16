<script setup>
import { onActivated, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { closeToast, showFailToast, showSuccessToast } from 'vant'
import { deleteFavorite, pageFavorite } from '../api/favorite'
import { getRecipesBatch, toggleRecipeLike } from '../api/recipe'
import EmptyState from '../components/EmptyState.vue'
import LoadMoreSentinel from '../components/LoadMoreSentinel.vue'
import RecipeCard from '../components/RecipeCard.vue'
import { useUserStore } from '../stores/user'
import { DATA_REFRESH_EVENT, DATA_SCOPE, hasDataChanged, hasMatchingScope, markDataStale, rememberDataVersions } from '../utils/dataRefresh'

const CACHE_MAX_AGE = 60 * 1000
const favoriteListCache = {
  userId: '',
  list: [],
  page: 0,
  total: 0,
  savedAt: 0,
}

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const loadingMore = ref(false)
const page = ref(favoriteListCache.userId === userStore.userId ? favoriteListCache.page : 0)
const total = ref(favoriteListCache.userId === userStore.userId ? favoriteListCache.total : 0)
let requestVersion = 0
const list = ref(favoriteListCache.userId === userStore.userId ? [...favoriteListCache.list] : [])
const likePendingMap = ref({})
const watchedDataScopes = [DATA_SCOPE.favorites, DATA_SCOPE.wanted, DATA_SCOPE.recipes]
const seenDataVersions = {}


onMounted(() => {
  window.addEventListener(DATA_REFRESH_EVENT, handleDataRefresh)
  if (list.value.length > 0) {
    if (hasDataChanged(watchedDataScopes, seenDataVersions) || isCacheStale()) {
      loadFavorite({ keepExisting: true, silent: true })
    }
    return
  }
  loadFavorite()
})

onActivated(() => {
  if (list.value.length > 0 && (hasDataChanged(watchedDataScopes, seenDataVersions) || isCacheStale())) {
    loadFavorite({ keepExisting: true, silent: true })
  }
})

onBeforeUnmount(() => {
  saveFavoriteCache()
  window.removeEventListener(DATA_REFRESH_EVENT, handleDataRefresh)
})

async function loadFavorite(options = {}) {
  if (options.append && loadingMore.value) return
  const version = ++requestVersion
  if (!userStore.userId) {
    list.value = []
    saveFavoriteCache()
    return
  }
  const showLoading = !options.keepExisting && list.value.length === 0
  if (showLoading) loading.value = true
  if (options.append) loadingMore.value = true
  try {
    const nextPage = options.append ? page.value + 1 : 1
    const res = await pageFavorite({
      current: nextPage,
      size: 50,
      userId: userStore.userId,
    })
    const favorites = (res.data.records || []).map((item) => ({
      ...item,
      id: item.recipeId,
      favoriteId: item.id,
    }))
    const enriched = await enrichFavoriteRecipes(favorites)
    if (version !== requestVersion) return
    list.value = options.append ? [...list.value, ...enriched] : enriched
    page.value = nextPage
    total.value = Number(res.data.total || 0)
    saveFavoriteCache()
    rememberDataVersions(watchedDataScopes, seenDataVersions)
  } catch (error) {
    if (!options.silent || list.value.length === 0) {
      showFailToast(error.message || '收藏加载失败')
    }
  } finally {
    if (version === requestVersion) { loading.value = false; loadingMore.value = false }
  }
}

function handleDataRefresh(event) {
  if (!hasMatchingScope(event, watchedDataScopes)) return
  loadFavorite({ keepExisting: list.value.length > 0, silent: true })
}

function isCacheStale() {
  return !favoriteListCache.savedAt || Date.now() - favoriteListCache.savedAt > CACHE_MAX_AGE
}

function saveFavoriteCache() {
  favoriteListCache.userId = userStore.userId || ''
  favoriteListCache.list = [...list.value]
  favoriteListCache.page = page.value
  favoriteListCache.total = total.value
  favoriteListCache.savedAt = Date.now()
}

async function enrichFavoriteRecipes(records) {
  if (!records.length) return []
  const res = await getRecipesBatch(records.map((item) => item.recipeId))
  const byId = new Map((res.data || []).map((recipe) => [recipe.id, recipe]))
  return records.map((item) => ({
    ...item,
    ...(byId.get(item.recipeId) || {}),
    id: item.recipeId,
    favoriteId: item.favoriteId,
  }))
}

async function remove(item) {
  try {
    await deleteFavorite(item.favoriteId)
    list.value = list.value.filter((entry) => entry.favoriteId !== item.favoriteId)
    saveFavoriteCache()
    markDataStale(DATA_SCOPE.favorites)
    await loadFavorite({ keepExisting: true, silent: true })
    showSuccessToast('已取消收藏')
  } catch (error) {
    showFailToast(error.message || '操作失败')
  }
}

async function removeSafe(item) {
  try {
    await deleteFavorite(item.favoriteId)
    list.value = list.value.filter((entry) => entry.favoriteId !== item.favoriteId)
    saveFavoriteCache()
    markDataStale(DATA_SCOPE.favorites)
    await loadFavorite({ keepExisting: true, silent: true })
    closeToast()
    showSuccessToast({ message: '已取消收藏', duration: 1400 })
  } catch (error) {
    closeToast()
    showFailToast({ message: error.message || '操作失败', duration: 1800 })
  }
}

async function toggleLikeOnCard(item) {
  if (!userStore.canMutate) {
    closeToast()
    showFailToast({ message: '请先登录后再点赞', duration: 1800 })
    return
  }
  if (likePendingMap.value[item.id]) return
  likePendingMap.value[item.id] = true
  try {
    const res = await toggleRecipeLike(item.id)
    const target = list.value.find((entry) => entry.id === item.id)
    if (target) {
      target.liked = Boolean(res.data?.liked)
      target.likeCount = Number(res.data?.likeCount || 0)
    }
    saveFavoriteCache()
    markDataStale(DATA_SCOPE.recipes)
  } catch (error) {
    closeToast()
    showFailToast({ message: error.message || '点赞失败', duration: 1800 })
  } finally {
    likePendingMap.value[item.id] = false
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
      <span v-if="userStore.userId">{{ total }} 道</span>
    </div>

    <EmptyState
      v-if="!userStore.userId"
      text="请先在“我的”页面登录，再收藏家里的拿手菜"
      button-text="去登录"
      @action="router.push('/profile')"
    />
    <van-loading v-else-if="loading && list.length === 0" size="24px" class="loading">加载中...</van-loading>
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
        class="favorite-recipe-card"
        :recipe="item"
        favorite
        :liked="Boolean(item.liked)"
        :like-count="Number(item.likeCount || 0)"
        :show-like-button="userStore.canMutate"
        :show-favorite-button="userStore.canMutate"
        @open="router.push(`/recipe/${item.id}`)"
        @favorite="removeSafe"
        @like="toggleLikeOnCard"
      />
      <LoadMoreSentinel v-if="list.length < total" :loading="loadingMore" @load="loadFavorite({ append: true, keepExisting: true })" />
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
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

@media (max-width: 759px) {
  .favorite-page :deep(.favorite-recipe-card .body) { padding: 10px; }
  .favorite-page :deep(.favorite-recipe-card .title-row) { flex-wrap: wrap; gap: 2px 6px; }
  .favorite-page :deep(.favorite-recipe-card .title-row h3) { flex: 1 1 100%; font-size: 15px; }
  .favorite-page :deep(.favorite-recipe-card .title-row span) { line-height: 1.4; }
  .favorite-page :deep(.favorite-recipe-card .tags-row) { gap: 5px; margin-top: 8px; }
  .favorite-page :deep(.favorite-recipe-card .tags-meta) { flex-wrap: wrap; margin-left: 0; min-width: 0; max-width: 100%; }
  .favorite-page :deep(.favorite-recipe-card .tag) { overflow-wrap: anywhere; line-height: 1.25; }
  .favorite-page :deep(.favorite-recipe-card .favorite) { width: 38px; height: 38px; }
  .favorite-page :deep(.favorite-recipe-card .favorite.has-count) { width: auto; max-width: 60%; }
  .favorite-page :deep(.favorite-recipe-card .like-pill) { min-width: 38px; height: 38px; box-sizing: border-box; }
}







</style>
