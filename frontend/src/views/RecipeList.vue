<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { closeToast, showFailToast, showSuccessToast } from 'vant'
import { pageCategory } from '../api/category'
import { checkFavorite, createFavorite, deleteFavoriteByRecipeId } from '../api/favorite'
import { pageRecipe } from '../api/recipe'
import { checkWantedRecipe, createWantedRecipe } from '../api/want'
import CategoryTabs from '../components/CategoryTabs.vue'
import ActionIcon from '../components/ActionIcon.vue'
import EmptyState from '../components/EmptyState.vue'
import RecipeCard from '../components/RecipeCard.vue'
import { useUserStore } from '../stores/user'
import { getRecipeImage } from '../utils/imageUrl'
import { formatRecipeVersionLabel } from '../utils/recipeVersion'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const categories = ref([])
const list = ref([])
const loading = ref(false)
const finished = ref(false)
const total = ref(0)
const favoriteMap = ref({})
const favoritePendingMap = ref({})
const wantedMap = ref({})
const wantedPendingMap = ref({})
const saveMessage = ref('')
const recommendMessage = ref('')
const suggestRecipe = ref(null)
const errorText = ref('')
const wantActionVisible = ref(false)
const wantDateVisible = ref(false)
const wantTargetRecipe = ref(null)
const customWantDate = ref('')
let recommendMessageTimer = null

const query = reactive({
  current: 1,
  size: 8,
  recipeName: '',
  categoryId: '',
  difficulty: '',
  status: '上架',
})

const filter = reactive({
  recipeName: '',
  categoryId: '',
})

const hasData = computed(() => list.value.length > 0)
const todayCover = computed(() => getRecipeImage(suggestRecipe.value?.coverImage))
const isTodayMode = computed(() => route.query.pick === 'today')
const isShowcaseMode = computed(() => route.query.mode === 'showcase')
const showcasePage = ref(0)
const showcasePageSize = 3
const showcasePages = computed(() => Math.max(1, Math.ceil(list.value.length / showcasePageSize)))
const showcaseRecipes = computed(() => {
  const start = showcasePage.value * showcasePageSize
  return list.value.slice(start, start + showcasePageSize)
})
const minWantDate = computed(() => formatDate(new Date()))

onMounted(async () => {
  const message = sessionStorage.getItem('recipeSaveMessage')
  if (message) {
    saveMessage.value = message
    sessionStorage.removeItem('recipeSaveMessage')
    window.setTimeout(() => {
      saveMessage.value = ''
    }, 2400)
  }
  await Promise.all([loadCategory(), refreshList()])
  if (route.query.pick === 'today') {
    chooseRandomRecipe({ showMessage: true })
  }
})

watch(
  () => [route.query.pick, route.query.mode, route.query.t],
  ([pick, mode]) => {
    if (pick === 'today') chooseRandomRecipe({ showMessage: true })
    if (mode === 'showcase') showcasePage.value = 0
  },
)

async function loadCategory() {
  try {
    const res = await pageCategory({ current: 1, size: 100 })
    categories.value = res.data.records || []
  } catch (error) {
    showFailToast(error.message || '分类加载失败')
  }
}

async function refreshList() {
  query.current = 1
  list.value = []
  finished.value = false
  suggestRecipe.value = null
  errorText.value = ''
  await loadMore()
}

async function loadMore() {
  if (finished.value || loading.value) return
  loading.value = true
  try {
    const res = await pageRecipe({ ...query })
    const records = res.data.records || []
    total.value = Number(res.data.total || 0)
    list.value = list.value.concat(records)
    query.current += 1
    if (list.value.length >= total.value || records.length === 0) {
      finished.value = true
    }
    await Promise.all([syncFavoriteState(records), syncWantedState(records)])
    if (!suggestRecipe.value && list.value.length > 0) {
      chooseRandomRecipe()
    }
  } catch (error) {
    errorText.value = error.message || '菜谱加载失败'
    showFailToast(errorText.value)
  } finally {
    loading.value = false
  }
}

async function syncFavoriteState(records) {
  if (!userStore.userId || records.length === 0) return
  const tasks = records.map((item) =>
    checkFavorite(userStore.userId, item.id).then((res) => {
      favoriteMap.value[item.id] = Boolean(res.data)
    }),
  )
  await Promise.allSettled(tasks)
}

async function syncWantedState(records) {
  if (!userStore.userId || records.length === 0) return
  const tasks = records.map((item) =>
    checkWantedRecipe(userStore.userId, item.id).then((res) => {
      wantedMap.value[item.id] = Boolean(res.data)
    }),
  )
  await Promise.allSettled(tasks)
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

function chooseRandomRecipe(options = {}) {
  const showMessage = Boolean(options.showMessage)
  const forceDifferent = Boolean(options.forceDifferent)

  if (list.value.length === 0) {
    suggestRecipe.value = null
    return
  }

  let pool = list.value
  if (forceDifferent && suggestRecipe.value && list.value.length > 1) {
    pool = list.value.filter((item) => item.id !== suggestRecipe.value.id)
  }

  const randomIndex = Math.floor(Math.random() * pool.length)
  const nextRecipe = pool[randomIndex]
  const unchanged = forceDifferent && suggestRecipe.value?.id === nextRecipe?.id

  suggestRecipe.value = nextRecipe

  if (showMessage && suggestRecipe.value) {
    showRecommendMessage(`今天推荐：${suggestRecipe.value.recipeName}`)
    return
  }

  if (forceDifferent && list.value.length <= 1) {
    showRecommendMessage('目前只有一道菜，先去添加更多吧')
    return
  }

  if (unchanged) {
    showRecommendMessage('暂时没有别的菜了，往下翻翻自己挑')
  }
}

function enterShowcaseMode() {
  router.push({
    path: '/recipes',
    query: {
      pick: 'today',
      mode: 'showcase',
      t: Date.now(),
    },
  })
}

function exitShowcaseMode() {
  router.push({
    path: '/recipes',
    query: {
      pick: 'today',
      t: Date.now(),
    },
  })
}

async function nextShowcasePage() {
  if (showcasePage.value < showcasePages.value - 1) {
    showcasePage.value += 1
    return
  }
  if (!finished.value) {
    await loadMore()
    if (showcasePage.value < showcasePages.value - 1) {
      showcasePage.value += 1
      return
    }
  }
  showcasePage.value = 0
}

function prevShowcasePage() {
  if (showcasePage.value > 0) {
    showcasePage.value -= 1
    return
  }
  showcasePage.value = showcasePages.value - 1
}

function showRecommendMessage(message) {
  recommendMessage.value = message
  window.clearTimeout(recommendMessageTimer)
  recommendMessageTimer = window.setTimeout(() => {
    recommendMessage.value = ''
  }, 1800)
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
      showSuccessToast('已取消收藏')
    } else {
      await createFavorite({ userId: userStore.userId, recipeId: item.id })
      favoriteMap.value[item.id] = true
      showSuccessToast('收藏成功')
    }
  } catch (error) {
    showFailToast(error.message || '收藏操作失败')
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
    if (favoriteMap.value[item.id]) {
      await deleteFavoriteByRecipeId(userStore.userId, item.id)
      favoriteMap.value[item.id] = false
      closeToast()
      showSuccessToast({ message: '已取消收藏', duration: 1400 })
    } else {
      await createFavorite({ userId: userStore.userId, recipeId: item.id })
      favoriteMap.value[item.id] = true
      closeToast()
      showSuccessToast({ message: '收藏成功', duration: 1400 })
    }
  } catch (error) {
    closeToast()
    showFailToast({ message: error.message || '收藏操作失败', duration: 1800 })
  } finally {
    favoritePendingMap.value[item.id] = false
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
    closeToast()
    showSuccessToast({ message: res.message || '已添加到想吃', duration: 1400 })
    closeWantPanels()
  } catch (error) {
    closeToast()
    showFailToast({ message: error.message || '添加想吃失败', duration: 1800 })
  } finally {
    wantedPendingMap.value[recipe.id] = false
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
  <div v-if="isShowcaseMode" class="showcase-page">
    <header class="showcase-header">
      <div>
        <p>王刚家的大菜单</p>
        <h1>今晚想吃哪一道？</h1>
        <span>一页一页慢慢翻，只看菜名、口味、作者和版本。</span>
      </div>
      <button type="button" @click="exitShowcaseMode">
        <van-icon name="cross" />
        退出
      </button>
    </header>

    <van-loading v-if="loading && list.length === 0" size="24px" class="loading">正在摆菜单...</van-loading>
    <EmptyState
      v-else-if="list.length === 0"
      text="还没有菜谱，先添加一道拿手菜吧"
      :show-button="userStore.isAdmin"
      @action="router.push('/recipe/create')"
    />
    <section v-else class="showcase-board">
      <article
        v-for="(item, index) in showcaseRecipes"
        :key="item.id"
        class="showcase-dish"
        :class="{ featured: index === 0 }"
        @click="openDetail(item)"
      >
        <img :src="getRecipeImage(item.coverImage)" :alt="item.recipeName" />
        <div class="showcase-actions" @click.stop>
          <button class="showcase-action" type="button" :class="{ active: favoriteMap[item.id] }" @click="toggleFavoriteSafe(item)">
            <ActionIcon name="heart" :filled="Boolean(favoriteMap[item.id])" :size="20" />
          </button>
          <button class="showcase-action" type="button" :class="{ active: wantedMap[item.id] }" @click="openWantAction(item)">
            <ActionIcon name="cart" :size="19" />
          </button>
        </div>
        <h2 class="showcase-dish-title">{{ item.recipeName }}</h2>
        <div class="showcase-dish-tags">
          <span class="taste">{{ item.taste || '家常' }}</span>
          <span v-if="item.ownerName" class="owner">{{ item.ownerName }}</span>
          <span class="version">{{ formatRecipeVersionLabel(item.recipeVersion) }}</span>
        </div>
      </article>
    </section>

    <footer class="showcase-footer">
      <button type="button" @click="prevShowcasePage">
        <van-icon name="arrow-left" />
        上一页
      </button>
      <div>{{ showcasePage + 1 }} / {{ showcasePages }}</div>
      <button type="button" class="primary" @click="nextShowcasePage">
        下一页
        <van-icon name="arrow" />
      </button>
    </footer>
  </div>

  <div v-else class="home-page">
    <section v-if="!isTodayMode" class="hero">
      <div>
        <p>欢迎回家</p>
        <h1>王刚家的家常菜谱</h1>
        <span>记录家里的味道，今天想吃什么？</span>
      </div>
      <button type="button" class="today-button" @click="chooseRandomRecipe({ showMessage: true })">
        <van-icon name="fire-o" size="18" />
        今天吃什么
      </button>
    </section>

    <section v-else class="today-mode">
      <div class="today-mode-head">
        <p>今天吃什么</p>
        <h1>{{ suggestRecipe?.recipeName || '正在挑一道菜' }}</h1>
        <span>{{ suggestRecipe ? '这道就很适合今天。' : '先把家里的菜单翻一遍。' }}</span>
      </div>
      <div v-if="suggestRecipe" class="today-pick-card">
        <img :src="todayCover" :alt="suggestRecipe.recipeName" />
        <div class="today-pick-body">
          <div class="today-pick-tags">
            <span>{{ suggestRecipe.categoryName || '未分类' }}</span>
            <span>{{ suggestRecipe.taste || '家常' }}</span>
            <span>{{ suggestRecipe.cookingTime || '用时未记录' }}</span>
            <div class="today-pick-tags-meta">
              <span v-if="suggestRecipe.ownerName" class="owner">{{ suggestRecipe.ownerName }}</span>
              <span class="version">{{ formatRecipeVersionLabel(suggestRecipe.recipeVersion) }}</span>
            </div>
          </div>
          <p>{{ suggestRecipe.recipeDesc || '点进去看看做法，今天就吃它。' }}</p>
          <div class="today-pick-actions">
            <button type="button" @click="chooseRandomRecipe({ forceDifferent: true })">
              <van-icon name="replay" />
              换一道
            </button>
            <button type="button" class="primary" @click="openDetail(suggestRecipe)">
              <van-icon name="description-o" />
              查看做法
            </button>
            <button type="button" class="want-action" @click="openWantAction(suggestRecipe)">
              <ActionIcon name="cart" :size="17" />
              {{ wantedMap[suggestRecipe.id] ? '已想吃' : '想吃' }}
            </button>
          </div>
          <button type="button" class="showcase-entry" @click="enterShowcaseMode">
            <van-icon name="photo-o" />
            进入大屏选菜模式
          </button>
        </div>
      </div>
      <EmptyState
        v-else-if="!loading"
        text="还没有菜谱，先添加一道拿手菜吧"
        :show-button="userStore.isAdmin"
        @action="router.push('/recipe/create')"
      />
    </section>

    <form v-if="!isTodayMode" class="search-card" @submit.prevent="applySearch">
      <van-icon name="search" size="18" />
      <input v-model="filter.recipeName" placeholder="搜索菜名、作者用户名" />
      <button type="submit">搜索</button>
    </form>

    <CategoryTabs v-if="!isTodayMode" v-model="filter.categoryId" :categories="categories" @change="pickCategory" />

    <div v-if="saveMessage" class="save-banner">
      <van-icon name="success" />
      <span>{{ saveMessage }}，已回到首页</span>
    </div>

    <div v-if="recommendMessage" class="recommend-banner">
      <van-icon name="fire-o" />
      <span>{{ recommendMessage }}</span>
    </div>

    <section v-if="suggestRecipe && !isTodayMode" class="today-card" @click="openDetail(suggestRecipe)">
      <img :src="todayCover" :alt="suggestRecipe.recipeName" />
      <div>
        <span>今日推荐</span>
        <strong>{{ suggestRecipe.recipeName }}</strong>
        <p>
          <template v-if="suggestRecipe.ownerName">{{ suggestRecipe.ownerName }} · </template>{{ formatRecipeVersionLabel(suggestRecipe.recipeVersion) }} · {{ suggestRecipe.taste || '家常' }} · {{ suggestRecipe.cookingTime || '用时未记录' }}
        </p>
      </div>
      <div class="today-card-actions" @click.stop>
        <button type="button" :class="{ active: favoriteMap[suggestRecipe.id] }" @click="toggleFavoriteSafe(suggestRecipe)">
          <ActionIcon name="heart" :filled="Boolean(favoriteMap[suggestRecipe.id])" :size="16" />
        </button>
        <button type="button" :class="{ active: wantedMap[suggestRecipe.id] }" @click="openWantAction(suggestRecipe)">
          <ActionIcon name="cart" :size="16" />
        </button>
        <button type="button" class="detail-action" aria-label="查看详情" @click="openDetail(suggestRecipe)">
          <van-icon name="arrow" size="18" />
        </button>
      </div>
    </section>

    <div class="list-head">
      <h2>{{ isTodayMode ? '也可以自己挑' : '菜单' }}</h2>
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
          :recipe="item"
          :favorite="Boolean(favoriteMap[item.id])"
          :wanted="Boolean(wantedMap[item.id])"
          @open="openDetail"
          @favorite="toggleFavoriteSafe"
          @want="openWantAction"
        />
      </div>
      <EmptyState v-else-if="!loading" :show-button="userStore.isAdmin" @action="router.push('/recipe/create')" />
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

.showcase-page {
  min-height: 100vh;
  padding: clamp(14px, 2vw, 24px);
  background:
    linear-gradient(180deg, rgba(255, 247, 237, 0.92), rgba(255, 250, 242, 0.98)),
    radial-gradient(circle at 80% 8%, rgba(251, 146, 60, 0.24), transparent 28%);
  color: var(--app-text);
}

.showcase-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.showcase-header p,
.showcase-header h1,
.showcase-header span {
  margin: 0;
}

.showcase-header p {
  color: var(--app-primary);
  font-size: 14px;
  font-weight: 900;
}

.showcase-header h1 {
  margin-top: 4px;
  font-size: clamp(30px, 5vw, 54px);
  line-height: 1.08;
}

.showcase-header span {
  display: block;
  margin-top: 8px;
  color: var(--app-muted);
  font-size: 15px;
}

.showcase-header button,
.showcase-footer button,
.showcase-entry {
  border: 0;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-weight: 900;
}

.showcase-header button {
  flex: 0 0 auto;
  height: 40px;
  padding: 0 14px;
  background: #fff;
  color: #7c5c46;
  border: 1px solid var(--app-border);
}

.showcase-board {
  margin-top: clamp(14px, 2vw, 24px);
  min-height: calc(100vh - 176px);
  display: grid;
  grid-template-columns: minmax(0, 1.18fr) minmax(0, 0.92fr);
  grid-template-rows: repeat(2, minmax(220px, 1fr));
  gap: clamp(12px, 2vw, 20px);
}

.showcase-dish {
  position: relative;
  min-height: 220px;
  overflow: hidden;
  border-radius: clamp(20px, 3vw, 34px);
  background: #fff;
  border: 1px solid rgba(255, 237, 213, 0.78);
  box-shadow: 0 18px 40px rgba(154, 52, 18, 0.16);
}

.showcase-dish.featured {
  grid-row: span 2;
}

.showcase-dish img {
  width: 100%;
  height: 100%;
  min-height: inherit;
  object-fit: cover;
  display: block;
  transition: transform 0.28s ease;
}

.showcase-dish:active img {
  transform: scale(1.02);
}

.showcase-actions {
  position: absolute;
  right: clamp(12px, 1.8vw, 22px);
  top: clamp(12px, 1.8vw, 22px);
  z-index: 2;
  display: flex;
  gap: 8px;
}

.showcase-action {
  width: 40px;
  height: 40px;
  border: 0;
  border-radius: 50%;
  background: rgba(47, 38, 31, 0.38);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
  overflow: hidden;
  backdrop-filter: blur(10px);
}

.showcase-action :deep(.action-svg-icon),
.today-card-actions button :deep(.action-svg-icon) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.showcase-action.active {
  color: #fb923c;
  background: rgba(255, 247, 237, 0.92);
}

.showcase-dish::after {
  content: "";
  position: absolute;
  inset: 38% 0 0;
  background: linear-gradient(180deg, transparent, rgba(47, 38, 31, 0.78));
}

.showcase-dish-title {
  position: absolute;
  left: clamp(14px, 2vw, 24px);
  right: clamp(72px, 14vw, 160px);
  bottom: clamp(14px, 2vw, 24px);
  z-index: 1;
  margin: 0;
  color: #fff;
  font-size: clamp(24px, 4vw, 46px);
  line-height: 1.08;
  overflow-wrap: anywhere;
  text-shadow: 0 2px 14px rgba(47, 38, 31, 0.36);
}

.showcase-dish-tags {
  position: absolute;
  right: clamp(10px, 1.6vw, 18px);
  bottom: clamp(10px, 1.6vw, 18px);
  z-index: 2;
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 5px;
  max-width: 58%;
}

.showcase-dish-tags span {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  padding: 4px 8px;
  border: 0;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
  text-shadow: none;
}

.showcase-dish-tags .taste,
.showcase-dish-tags .owner,
.showcase-dish-tags .version {
  background: rgba(255, 237, 213, 0.92);
  color: #c2410c;
}

.showcase-footer {
  margin-top: 14px;
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 12px;
}

.showcase-footer button {
  height: 46px;
  background: #fff;
  color: #7c5c46;
  border: 1px solid var(--app-border);
}

.showcase-footer button.primary {
  background: var(--app-primary);
  color: #fff;
  border-color: var(--app-primary);
}

.showcase-footer div {
  min-width: 76px;
  color: var(--app-muted);
  text-align: center;
  font-weight: 800;
}

.hero {
  min-height: 156px;
  padding: 18px;
  border-radius: 22px;
  background:
    linear-gradient(135deg, rgba(255, 247, 237, 0.92), rgba(255, 237, 213, 0.88)),
    url("https://images.unsplash.com/photo-1556911220-bff31c812dba?auto=format&fit=crop&w=900&q=80") center/cover;
  box-shadow: 0 16px 32px rgba(154, 52, 18, 0.13);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
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

.today-button {
  align-self: flex-start;
  height: 38px;
  border: 0;
  border-radius: 999px;
  padding: 0 14px;
  background: var(--app-primary);
  color: #fff;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 700;
  box-shadow: 0 8px 18px rgba(249, 115, 22, 0.28);
}

.today-mode {
  padding: 16px;
  border-radius: 24px;
  background: linear-gradient(180deg, #fff7e8 0%, #ffffff 100%);
  border: 1px solid #fed7aa;
  box-shadow: 0 16px 34px rgba(154, 52, 18, 0.13);
}

.today-mode-head p,
.today-mode-head h1,
.today-mode-head span {
  margin: 0;
}

.today-mode-head p {
  color: var(--app-primary);
  font-size: 13px;
  font-weight: 900;
}

.today-mode-head h1 {
  margin-top: 3px;
  color: var(--app-text);
  font-size: 27px;
  line-height: 1.2;
  overflow-wrap: anywhere;
}

.today-mode-head span {
  display: block;
  margin-top: 6px;
  color: var(--app-muted);
  font-size: 13px;
}

.today-pick-card {
  margin-top: 14px;
  overflow: hidden;
  border-radius: 20px;
  background: #fff;
  border: 1px solid var(--app-border);
}

.today-pick-card img {
  width: 100%;
  aspect-ratio: 4 / 3;
  object-fit: cover;
  display: block;
}

.today-pick-body {
  padding: 13px;
}

.today-pick-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 7px;
}

.today-pick-tags-meta {
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  gap: 7px;
  margin-left: auto;
}

.today-pick-tags > span,
.today-pick-tags-meta span {
  box-sizing: border-box;
  padding: 5px 9px;
  border: 1px solid transparent;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
}

.today-pick-tags > span {
  background: var(--app-primary-soft);
  color: #c2410c;
}

.today-pick-tags-meta span.owner {
  background: #fff4e8;
  color: #9a3412;
  border-color: rgba(249, 115, 22, 0.22);
}

.today-pick-tags-meta span.version {
  background: #f3f4f6;
  color: #4b5563;
  border-color: rgba(107, 114, 128, 0.18);
}

.today-pick-body p {
  margin: 10px 0 0;
  color: #6f5a49;
  line-height: 1.6;
  font-size: 14px;
}

.today-pick-actions {
  margin-top: 13px;
  display: grid;
  grid-template-columns: minmax(0, 0.98fr) minmax(0, 1.08fr) minmax(0, 0.94fr);
  gap: 8px;
}

.today-pick-actions button {
  min-width: 0;
  height: 40px;
  padding: 0 8px;
  border: 1px solid var(--app-border);
  border-radius: 999px;
  background: #fffaf2;
  color: #7c5c46;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  font-size: 14px;
  font-weight: 800;
  line-height: 1;
  white-space: nowrap;
  word-break: keep-all;
}

.today-pick-actions button.primary {
  border-color: var(--app-primary);
  background: var(--app-primary);
  color: #fff;
}

.today-pick-actions button.want-action {
  border-color: #fed7aa;
  background: #fff7e8;
  color: #c2410c;
}

.showcase-entry {
  width: 100%;
  height: 42px;
  margin-top: 12px;
  background: #2f261f;
  color: #fff;
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

.save-banner,
.recommend-banner,
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

.recommend-banner {
  background: #fff7e8;
  color: #c2410c;
  border: 1px solid #fed7aa;
  font-weight: 700;
}

.error-card {
  justify-content: space-between;
  background: #fff1f2;
  color: #be123c;
}

.today-card {
  min-height: 86px;
  padding: 10px;
  border-radius: 18px;
  background: #fff;
  border: 1px solid var(--app-border);
  box-shadow: 0 12px 26px rgba(154, 52, 18, 0.08);
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
}

.today-card img {
  width: 72px;
  height: 72px;
  object-fit: cover;
  border-radius: 14px;
}

.today-card span {
  color: var(--app-primary);
  font-size: 12px;
  font-weight: 800;
}

.today-card strong {
  display: block;
  margin-top: 2px;
  color: var(--app-text);
  font-size: 17px;
}

.today-card p {
  margin: 4px 0 0;
  color: var(--app-muted);
  font-size: 12px;
}

.today-card-actions {
  width: 98px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  color: #9b7d66;
}

.today-card-actions button {
  flex: 0 0 30px;
  width: 30px;
  height: 30px;
  border: 1px solid var(--app-border);
  border-radius: 50%;
  padding: 0;
  background: #fffaf2;
  color: #9b7d66;
  display: inline-grid;
  place-items: center;
  line-height: 1;
  overflow: hidden;
  appearance: none;
  -webkit-appearance: none;
}

.today-card-actions button.active {
  color: #f97316;
  background: #fff7e8;
  border-color: #fed7aa;
}

.today-card-actions .detail-action {
  color: #9b7d66;
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
  grid-template-columns: 1fr;
  gap: 14px;
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

@media (max-width: 720px) {
  .showcase-header {
    flex-direction: column;
  }

  .showcase-board {
    min-height: auto;
    grid-template-columns: 1fr;
    grid-template-rows: none;
  }

  .showcase-dish,
  .showcase-dish.featured {
    grid-row: auto;
    aspect-ratio: 4 / 3;
  }

  .showcase-footer {
    position: sticky;
    bottom: 0;
    padding: 10px 0;
    background: rgba(255, 247, 237, 0.92);
    backdrop-filter: blur(12px);
  }
}
</style>
