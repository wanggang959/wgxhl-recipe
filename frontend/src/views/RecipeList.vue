<script setup>
import { loadAllPages } from '../utils/pagination'
import { computed, onActivated, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { closeToast, showFailToast, showSuccessToast } from 'vant'
import { pageCategory } from '../api/category'
import { createFavorite, deleteFavoriteByRecipeId } from '../api/favorite'
import { pageRecipe, toggleRecipeLike } from '../api/recipe'
import { createWantedRecipe } from '../api/want'
import CategoryTabs from '../components/CategoryTabs.vue'
import ActionIcon from '../components/ActionIcon.vue'
import EmptyState from '../components/EmptyState.vue'
import RecipeCard from '../components/RecipeCard.vue'
import { useUserStore } from '../stores/user'
import { DATA_REFRESH_EVENT, DATA_SCOPE, hasDataChanged, hasMatchingScope, markDataStale, rememberDataVersions } from '../utils/dataRefresh'
import { beginRecipeRefresh } from '../utils/recipePaging'

const CACHE_MAX_AGE = 60 * 1000
const DEFAULT_QUERY = {
  current: 1,
  size: 8,
  recipeName: '',
  categoryId: '',
  difficulty: '',
  status: '上架',
  scope: 'family',
  sort: 'latest',
  randomPivot: null,
}
const DEFAULT_FILTER = {
  recipeName: '',
  categoryId: '',
}
const recipeListCache = {
  categories: [],
  list: [],
  total: 0,
  finished: false,
  favoriteMap: {},
  wantedMap: {},
  query: null,
  filter: null,
  savedAt: 0,
}

const router = useRouter()
const userStore = useUserStore()
const canMutate = computed(() => userStore.canMutate)

const categories = ref([...recipeListCache.categories])
const list = ref([...recipeListCache.list])
const loading = ref(false)
const finished = ref(recipeListCache.finished)
const total = ref(recipeListCache.total)
const favoriteMap = ref({ ...recipeListCache.favoriteMap })
const favoritePendingMap = ref({})
const wantedMap = ref({ ...recipeListCache.wantedMap })
const wantedPendingMap = ref({})
const likePendingMap = ref({})
const saveMessage = ref('')
const errorText = ref('')
const wantActionVisible = ref(false)
const wantDateVisible = ref(false)
const wantTargetRecipe = ref(null)
const customWantDate = ref('')
let listRevision = 0
const watchedDataScopes = [DATA_SCOPE.recipes, DATA_SCOPE.favorites, DATA_SCOPE.wanted, DATA_SCOPE.base]
const seenDataVersions = {}

const query = reactive({
  ...DEFAULT_QUERY,
  ...(recipeListCache.query || {}),
})

// 游客没有家庭数据，直接落在公共范围
if (userStore.isGuest) {
  query.scope = 'public'
  query.status = '上架'
}

const filter = reactive({
  ...DEFAULT_FILTER,
  ...(recipeListCache.filter || {}),
})

const hasData = computed(() => list.value.length > 0)
const familyName = computed(() => userStore.user?.householdName || '王师傅家')
const canManageRecipes = computed(() => Boolean(userStore.isSuperAdmin))
const showOffline = computed(() => query.status === '下架')

function toggleShowOffline() {
  query.status = showOffline.value ? '上架' : '下架'
  refreshList()
}

const isPublicScope = computed(() => query.scope === 'public')
const sortOptions = [
  { value: 'latest', label: '最新' },
  { value: 'likes_desc', label: '最赞' },
  { value: 'likes_asc', label: '冷门' },
  { value: 'random', label: '随机' },
]

function pickScope(scope) {
  if (query.scope === scope) return
  query.scope = scope
  query.status = '上架'
  query.categoryId = ''
  filter.categoryId = ''
  refreshList()
}

function pickSort(sort) {
  if (query.sort === sort) return
  query.sort = sort
  refreshList()
}
const minWantDate = computed(() => formatDate(new Date()))

onMounted(async () => {
  window.addEventListener(DATA_REFRESH_EVENT, handleDataRefresh)
  if (!canManageRecipes.value && query.status !== '上架') {
    query.status = '上架'
  }
  const message = sessionStorage.getItem('recipeSaveMessage')
  const shouldForceRefresh = Boolean(message)
  if (message) {
    saveMessage.value = message
    sessionStorage.removeItem('recipeSaveMessage')
    window.setTimeout(() => {
      saveMessage.value = ''
    }, 2400)
  }
  if (categories.value.length === 0) {
    await loadCategory()
  }
  if (list.value.length > 0 && !shouldForceRefresh) {
    if (hasDataChanged(watchedDataScopes, seenDataVersions)) {
      refreshList({ keepExisting: true, silent: true })
    } else if (isCacheStale()) {
      refreshList({ keepExisting: true, silent: true })
    }
  } else {
    await refreshList({ keepExisting: list.value.length > 0 })
  }
})

onActivated(() => {
  if (list.value.length > 0 && hasDataChanged(watchedDataScopes, seenDataVersions)) {
    refreshList({ keepExisting: true, silent: true })
  } else if (list.value.length > 0 && isCacheStale()) {
    refreshList({ keepExisting: true, silent: true })
  }
})

onBeforeUnmount(() => {
  saveRecipeListCache()
  window.removeEventListener(DATA_REFRESH_EVENT, handleDataRefresh)
})

async function loadCategory() {
  try {
    const res = await loadAllPages(pageCategory)
    categories.value = res.data.records || []
    saveRecipeListCache()
  } catch (error) {
    showFailToast(error.message || '分类加载失败')
  }
}

async function refreshList(options = {}) {
  const keepExisting = Boolean(options.keepExisting)
  listRevision += 1
  loading.value = false
  beginRecipeRefresh(query)
  if (!keepExisting) {
    list.value = []
  }
  finished.value = false
  errorText.value = ''
  await loadMore({
    replace: keepExisting,
    silent: Boolean(options.silent),
  })
}

async function loadMore(options = {}) {
  if (finished.value || loading.value) return
  const currentPage = query.current
  const revision = listRevision
  loading.value = true
  try {
    const res = await pageRecipe({ ...query })
    if (revision !== listRevision) return
    const records = res.data.records || []
    total.value = Number(res.data.total || 0)
    list.value = options.replace ? records : list.value.concat(records)
    query.current = currentPage + 1
    if (list.value.length >= total.value || records.length === 0) {
      finished.value = true
    }
    records.forEach((item) => {
      favoriteMap.value[item.id] = Boolean(item.favorite)
      wantedMap.value[item.id] = Boolean(item.wanted)
    })
    saveRecipeListCache()
    rememberDataVersions(watchedDataScopes, seenDataVersions)
  } catch (error) {
    if (revision !== listRevision) return
    const message = error.message || '菜谱加载失败'
    if (!options.silent || list.value.length === 0) {
      errorText.value = message
      showFailToast(errorText.value)
    }
  } finally {
    if (revision === listRevision) loading.value = false
  }
}

function handleDataRefresh(event) {
  if (!hasMatchingScope(event, watchedDataScopes)) return
  if (list.value.length > 0) {
    refreshList({ keepExisting: true, silent: true })
    return
  }
  refreshList({ silent: true })
}

function isCacheStale() {
  return !recipeListCache.savedAt || Date.now() - recipeListCache.savedAt > CACHE_MAX_AGE
}

function saveRecipeListCache() {
  recipeListCache.categories = [...categories.value]
  recipeListCache.list = [...list.value]
  recipeListCache.total = total.value
  recipeListCache.finished = finished.value
  recipeListCache.favoriteMap = { ...favoriteMap.value }
  recipeListCache.wantedMap = { ...wantedMap.value }
  recipeListCache.query = { ...query }
  recipeListCache.filter = { ...filter }
  recipeListCache.savedAt = Date.now()
}

function applySearch() {
  query.recipeName = filter.recipeName.trim()
  query.categoryId = filter.categoryId
  refreshList()
}

function pickCategory(categoryId = '') {
  filter.categoryId = categoryId
  applySearch()
}

function openDetail(item) {
  router.push(`/recipe/${item.id}`)
}

async function toggleFavorite(item) {
  if (!userStore.userId) {
    showFailToast('请先在“我的”页面登录')
    return
  }
  try {
    if (favoriteMap.value[item.id]) {
      await deleteFavoriteByRecipeId(userStore.userId, item.id)
      favoriteMap.value[item.id] = false
      markDataStale(DATA_SCOPE.favorites)
      showSuccessToast('已取消收藏')
    } else {
      await createFavorite({ userId: userStore.userId, recipeId: item.id })
      favoriteMap.value[item.id] = true
      markDataStale(DATA_SCOPE.favorites)
      showSuccessToast('收藏成功')
    }
  } catch (error) {
    showFailToast(error.message || '收藏操作失败')
  }
}

async function toggleLikeOnCard(item) {
  if (!canMutate.value) {
    closeToast()
    showFailToast('请先登录后再点赞')
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
    markDataStale(DATA_SCOPE.recipes)
    saveRecipeListCache()
  } catch (error) {
    closeToast()
    showFailToast({ message: error.message || '点赞失败', duration: 1800 })
  } finally {
    likePendingMap.value[item.id] = false
  }
}

async function toggleFavoriteSafe(item) {
  if (!userStore.userId) {
    closeToast()
    showFailToast('请先登录后再收藏')
    return
  }
  if (favoritePendingMap.value[item.id]) return
  favoritePendingMap.value[item.id] = true
  try {
    let result
    let delta
    if (favoriteMap.value[item.id]) {
      result = await deleteFavoriteByRecipeId(userStore.userId, item.id)
      delta = -1
      favoriteMap.value[item.id] = false
      markDataStale(DATA_SCOPE.favorites)
      closeToast()
      showSuccessToast({ message: '已取消收藏', duration: 1400 })
    } else {
      result = await createFavorite({ userId: userStore.userId, recipeId: item.id })
      delta = 1
      favoriteMap.value[item.id] = true
      markDataStale(DATA_SCOPE.favorites)
      closeToast()
      showSuccessToast({ message: '收藏成功', duration: 1400 })
    }
    item.favoriteCount = result.data?.favoriteCount != null
      ? Number(result.data.favoriteCount)
      : Math.max(0, Number(item.favoriteCount || 0) + delta)
    markDataStale(DATA_SCOPE.recipes)
  } catch (error) {
    closeToast()
    showFailToast({ message: error.message || '收藏操作失败', duration: 1800 })
  } finally {
    favoritePendingMap.value[item.id] = false
    saveRecipeListCache()
  }
}

function formatDate(date) {
  const target = new Date(date)
  const year = target.getFullYear()
  const month = String(target.getMonth() + 1).padStart(2, '0')
  const day = String(target.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function addDays(days) {
  const date = new Date()
  date.setDate(date.getDate() + days)
  return formatDate(date)
}

function openWantAction(item) {
  if (!userStore.userId) {
    closeToast()
    showFailToast('请先登录后再添加想吃')
    return
  }
  wantTargetRecipe.value = item
  wantActionVisible.value = true
}

function openWantDatePicker() {
  customWantDate.value = minWantDate.value
  wantActionVisible.value = false
  wantDateVisible.value = true
}

function closeWantPanels() {
  wantActionVisible.value = false
  wantDateVisible.value = false
  wantTargetRecipe.value = null
}

async function addWantedByDate(plannedDate) {
  const recipe = wantTargetRecipe.value
  if (!recipe || wantedPendingMap.value[recipe.id]) return
  wantedPendingMap.value[recipe.id] = true
  try {
    const res = await createWantedRecipe({
      userId: userStore.userId,
      recipeId: recipe.id,
      plannedDate,
    })
    wantedMap.value[recipe.id] = true
    markDataStale([DATA_SCOPE.wanted, DATA_SCOPE.todos])
    closeToast()
    showSuccessToast({ message: res.message || '已添加到想吃', duration: 1400 })
    closeWantPanels()
  } catch (error) {
    closeToast()
    showFailToast({ message: error.message || '添加想吃失败', duration: 1800 })
  } finally {
    wantedPendingMap.value[recipe.id] = false
    saveRecipeListCache()
  }
}

function submitCustomWantDate() {
  if (!customWantDate.value) {
    showFailToast('请选择想吃日期')
    return
  }
  addWantedByDate(customWantDate.value)
}
</script>

<template>
  <div class="home-page">
    <section class="hero">
      <div>
        <p>欢迎回家</p>
        <h1>{{ familyName }}的生活日常</h1>
        <span>记录家里的味道，也照管家里的小事。</span>
      </div>
    </section>

    <div class="scope-switch">
      <div v-if="!userStore.isGuest" class="scope-tabs">
        <button type="button" :class="{ active: !isPublicScope }" @click="pickScope('family')">
          <van-icon name="friends-o" size="13" />
          家庭
        </button>
        <button type="button" :class="{ active: isPublicScope }" @click="pickScope('public')">
          <van-icon name="globe-o" size="13" />
          公共
        </button>
      </div>
      <div v-else class="scope-tabs guest-scope">
        <span>
          <van-icon name="globe-o" size="13" />
          看看大家的家常菜
        </span>
      </div>
      <div class="sort-switch">
        <button
          v-for="item in sortOptions"
          :key="item.value"
          type="button"
          :class="{ active: query.sort === item.value }"
          @click="pickSort(item.value)"
        >
          {{ item.label }}
        </button>
      </div>
    </div>

    <form class="search-card" @submit.prevent="applySearch">
      <van-icon name="search" size="18" />
      <input v-model="filter.recipeName" :placeholder="isPublicScope ? '搜索菜名' : '搜索菜名、作者用户名'" />
      <button type="submit">搜索</button>
    </form>

    <CategoryTabs v-if="!isPublicScope" v-model="filter.categoryId" :categories="categories" @change="pickCategory" />

    <div v-if="!isPublicScope && canManageRecipes" class="offline-filter">
      <button type="button" :class="{ active: showOffline }" @click="toggleShowOffline">
        <van-icon :name="showOffline ? 'eye-o' : 'closed-eye'" size="14" />
        {{ showOffline ? '返回在架菜谱' : '查看已下架' }}
      </button>
      <span v-if="showOffline">下架的菜只有作者和家庭管理员能看到</span>
    </div>

    <div v-if="saveMessage" class="save-banner">
      <van-icon name="success" />
      <span>{{ saveMessage }}，已回到首页</span>
    </div>

    <div class="list-head">
      <h2>{{ isPublicScope ? '公共菜谱' : '菜单' }}</h2>
      <span>{{ total }} 道菜</span>
    </div>

    <div v-if="errorText" class="error-card">
      <span>{{ errorText }}</span>
      <van-button size="small" plain type="warning" @click="refreshList">重试</van-button>
    </div>

    <van-list :loading="loading" :finished="finished" finished-text="已经看到菜单底啦" @load="loadMore">
      <div v-if="hasData" class="cards">
        <RecipeCard
          v-for="item in list"
          :key="item.id"
          class="home-recipe-card"
          :recipe="item"
          :favorite="Boolean(favoriteMap[item.id])"
          :liked="Boolean(item.liked)"
          :like-count="Number(item.likeCount || 0)"
          :show-like-button="canMutate"
          :show-favorite-button="canMutate"
          @open="openDetail"
          @favorite="toggleFavoriteSafe"
          @like="toggleLikeOnCard"
        />
      </div>
      <EmptyState
        v-else-if="!loading"
        :text="isPublicScope ? '还没有人公开菜谱，把你的拿手菜分享出来吧' : '还没有菜谱，先添加一道拿手菜吧'"
        :show-button="canMutate"
        @action="router.push('/recipe/create')"
      />
    </van-list>
  </div>

  <van-popup v-model:show="wantActionVisible" position="bottom" round>
    <div class="want-sheet">
      <h3>{{ wantTargetRecipe?.recipeName || '这道菜' }}</h3>
      <button type="button" class="want-sheet-primary" @click="openWantDatePicker">
        <ActionIcon name="cart" :size="17" />
        想吃
      </button>
      <button type="button" @click="closeWantPanels">取消</button>
    </div>
  </van-popup>

  <van-popup v-model:show="wantDateVisible" position="bottom" round>
    <div class="want-sheet">
      <h3>选择想吃日期</h3>
      <div class="want-date-actions">
        <button type="button" @click="addWantedByDate(addDays(0))">今日</button>
        <button type="button" @click="addWantedByDate(addDays(1))">明日</button>
      </div>
      <label class="want-custom-date">
        <span>自选日期</span>
        <input v-model="customWantDate" type="date" :min="minWantDate" />
      </label>
      <button type="button" class="want-sheet-primary" @click="submitCustomWantDate">添加到想吃</button>
      <button type="button" @click="closeWantPanels">取消</button>
    </div>
  </van-popup>
</template>

<style scoped>
.home-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.hero {
  min-height: 112px;
  padding: 18px;
  border-radius: 22px;
  background:
    linear-gradient(135deg, rgba(255, 247, 237, 0.92), rgba(255, 237, 213, 0.88)),
    url("https://images.unsplash.com/photo-1556911220-bff31c812dba?auto=format&fit=crop&w=900&q=80") center/cover;
  box-shadow: 0 16px 32px rgba(154, 52, 18, 0.13);
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.hero p,
.hero h1,
.hero span {
  margin: 0;
}

.hero p {
  color: #c2410c;
  font-size: 13px;
  font-weight: 800;
}

.hero h1 {
  margin-top: 4px;
  font-size: 27px;
  line-height: 1.16;
  color: #2f261f;
}

.hero span {
  display: block;
  margin-top: 8px;
  color: #7c5c46;
  font-size: 14px;
}

.search-card {
  height: 48px;
  padding: 0 8px 0 14px;
  border: 1px solid var(--app-border);
  border-radius: 999px;
  background: #fff;
  box-shadow: 0 8px 22px rgba(154, 52, 18, 0.07);
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr) 64px;
  align-items: center;
  gap: 8px;
  color: var(--app-primary);
}

.search-card input {
  min-width: 0;
  border: 0;
  outline: 0;
  color: var(--app-text);
  font-size: 14px;
  background: transparent;
}

.search-card button {
  width: 64px;
  min-width: 64px;
  height: 34px;
  border: 0;
  border-radius: 999px;
  background: var(--app-primary-soft);
  color: #c2410c;
  font-weight: 800;
  white-space: nowrap;
  word-break: keep-all;
}

.offline-filter {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.offline-filter button {
  height: 30px;
  padding: 0 12px;
  border: 1px solid var(--app-border);
  border-radius: 999px;
  background: #fff;
  color: #6b7280;
  font-size: 12px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.offline-filter button.active {
  background: #f3f4f6;
  border-color: #d1d5db;
  color: #374151;
}

.offline-filter span {
  font-size: 11px;
  color: #9ca3af;
}

.scope-switch {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
}

.scope-tabs,
.sort-switch {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 3px;
  border-radius: 999px;
  border: 1px solid var(--app-border);
  background: #fff;
  box-shadow: 0 6px 16px rgba(154, 52, 18, 0.05);
}

.scope-tabs button,
.sort-switch button {
  height: 30px;
  padding: 0 12px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: var(--app-muted);
  font-size: 12px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.scope-tabs button.active {
  background: #fff1e6;
  color: #c2410c;
}

.guest-scope span {
  height: 30px;
  padding: 0 12px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 700;
  color: var(--app-muted);
}

.sort-switch button.active {
  background: #f3f4f6;
  color: #111827;
}

.save-banner,
.error-card {
  padding: 10px 12px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

.save-banner {
  background: #ecfdf3;
  color: #15803d;
}

.error-card {
  justify-content: space-between;
  background: #fff1f2;
  color: #be123c;
}

.list-head {
  display: flex;
  align-items: end;
  justify-content: space-between;
  padding: 2px 2px 0;
}

.list-head h2 {
  margin: 0;
  color: var(--app-text);
  font-size: 19px;
}

.list-head span {
  color: var(--app-muted);
  font-size: 13px;
}

.cards {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

@media (max-width: 759px) {
  .home-page :deep(.home-recipe-card .body) { padding: 10px; }
  .home-page :deep(.home-recipe-card .title-row) { flex-wrap: wrap; gap: 2px 6px; }
  .home-page :deep(.home-recipe-card .title-row h3) { flex: 1 1 100%; font-size: 15px; }
  .home-page :deep(.home-recipe-card .title-row span) { line-height: 1.4; }
  .home-page :deep(.home-recipe-card .tags-row) { gap: 5px; margin-top: 8px; }
  .home-page :deep(.home-recipe-card .tags-meta) { flex-wrap: wrap; margin-left: 0; min-width: 0; max-width: 100%; }
  .home-page :deep(.home-recipe-card .tag) { overflow-wrap: anywhere; line-height: 1.25; }
  .home-page :deep(.home-recipe-card .favorite) { width: 38px; height: 38px; }
  .home-page :deep(.home-recipe-card .favorite.has-count) { width: auto; max-width: 60%; }
  .home-page :deep(.home-recipe-card .like-pill) { min-width: 38px; height: 38px; box-sizing: border-box; }
}

.want-sheet {
  padding: 18px 14px max(18px, env(safe-area-inset-bottom));
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: #fffaf2;
}

.want-sheet h3 {
  margin: 0 0 4px;
  color: var(--app-text);
  font-size: 17px;
  line-height: 1.35;
  text-align: center;
  overflow-wrap: anywhere;
}

.want-sheet button {
  width: 100%;
  height: 44px;
  border: 1px solid var(--app-border);
  border-radius: 999px;
  background: #fff;
  color: #7c5c46;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-weight: 800;
}

.want-sheet button.want-sheet-primary,
.want-date-actions button {
  background: var(--app-primary);
  color: #fff;
  border-color: var(--app-primary);
}

.want-date-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.want-custom-date {
  display: grid;
  gap: 6px;
  color: var(--app-muted);
  font-size: 13px;
}

.want-custom-date input {
  height: 42px;
  border: 1px solid var(--app-border);
  border-radius: 12px;
  padding: 0 11px;
  background: #fff;
  color: var(--app-text);
  outline: 0;
}

@media (min-width: 760px) {
  .cards {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

</style>
