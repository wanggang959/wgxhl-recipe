<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showFailToast, showSuccessToast } from 'vant'
import { pageCategory } from '../api/category'
import { checkFavorite, createFavorite, deleteFavoriteByRecipeId } from '../api/favorite'
import { pageRecipe } from '../api/recipe'
import CategoryTabs from '../components/CategoryTabs.vue'
import EmptyState from '../components/EmptyState.vue'
import RecipeCard from '../components/RecipeCard.vue'
import { useUserStore } from '../stores/user'
import { getRecipeImage } from '../utils/imageUrl'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const categories = ref([])
const list = ref([])
const loading = ref(false)
const finished = ref(false)
const total = ref(0)
const favoriteMap = ref({})
const saveMessage = ref('')
const recommendMessage = ref('')
const suggestRecipe = ref(null)
const errorText = ref('')
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
    chooseRandomRecipe(true)
  }
})

watch(
  () => [route.query.pick, route.query.mode, route.query.t],
  ([pick, mode]) => {
    if (pick === 'today') chooseRandomRecipe(true)
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
    await syncFavoriteState(records)
    if (!suggestRecipe.value && list.value.length > 0) {
      chooseRandomRecipe(false)
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

function applySearch() {
  query.recipeName = filter.recipeName.trim()
  query.categoryId = filter.categoryId
  refreshList()
}

function pickCategory(categoryId = '') {
  filter.categoryId = categoryId
  applySearch()
}

function chooseRandomRecipe(open = false) {
  if (list.value.length === 0) {
    suggestRecipe.value = null
    return
  }
  const randomIndex = Math.floor(Math.random() * list.value.length)
  suggestRecipe.value = list.value[randomIndex]
  if (open && suggestRecipe.value) {
    showRecommendMessage(`今天推荐：${suggestRecipe.value.recipeName}`)
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
</script>

<template>
  <div v-if="isShowcaseMode" class="showcase-page">
    <header class="showcase-header">
      <div>
        <p>王刚家的大菜单</p>
        <h1>今晚想吃哪一道？</h1>
        <span>一页一页慢慢翻，只看菜名、口味和大图。</span>
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
        <div class="showcase-dish-info">
          <span>{{ item.taste || '家常' }}</span>
          <h2>{{ item.recipeName }}</h2>
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
        <p>家用食谱</p>
        <h1>王刚家的家常菜谱</h1>
        <span>记录家里的味道，今天想吃什么？</span>
      </div>
      <button type="button" class="today-button" @click="chooseRandomRecipe(true)">
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
          </div>
          <p>{{ suggestRecipe.recipeDesc || '点进去看看做法，今天就吃它。' }}</p>
          <div class="today-pick-actions">
            <button type="button" @click="chooseRandomRecipe(true)">
              <van-icon name="replay" />
              换一道
            </button>
            <button type="button" class="primary" @click="openDetail(suggestRecipe)">
              <van-icon name="description-o" />
              查看做法
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
      <input v-model="filter.recipeName" placeholder="搜索菜名、食材、口味" />
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
        <p>{{ suggestRecipe.taste || '家常' }} · {{ suggestRecipe.cookingTime || '用时未记录' }}</p>
      </div>
      <van-icon name="arrow" size="18" />
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
          @open="openDetail"
          @favorite="toggleFavorite"
        />
      </div>
      <EmptyState v-else-if="!loading" :show-button="userStore.isAdmin" @action="router.push('/recipe/create')" />
    </van-list>
  </div>
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

.showcase-dish::after {
  content: "";
  position: absolute;
  inset: 38% 0 0;
  background: linear-gradient(180deg, transparent, rgba(47, 38, 31, 0.78));
}

.showcase-dish-info {
  position: absolute;
  left: clamp(14px, 2vw, 24px);
  right: clamp(14px, 2vw, 24px);
  bottom: clamp(14px, 2vw, 24px);
  z-index: 1;
  color: #fff;
  text-shadow: 0 2px 14px rgba(47, 38, 31, 0.36);
}

.showcase-dish-info span {
  display: inline-flex;
  padding: 6px 11px;
  border-radius: 999px;
  background: rgba(255, 237, 213, 0.92);
  color: #c2410c;
  font-size: 13px;
  font-weight: 900;
  text-shadow: none;
}

.showcase-dish-info h2 {
  margin: 10px 0 0;
  font-size: clamp(24px, 4vw, 46px);
  line-height: 1.08;
  overflow-wrap: anywhere;
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
  gap: 7px;
}

.today-pick-tags span {
  padding: 5px 9px;
  border-radius: 999px;
  background: var(--app-primary-soft);
  color: #c2410c;
  font-size: 12px;
  font-weight: 700;
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
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.today-pick-actions button {
  height: 40px;
  border: 1px solid var(--app-border);
  border-radius: 999px;
  background: #fffaf2;
  color: #7c5c46;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  font-weight: 800;
}

.today-pick-actions button.primary {
  border-color: var(--app-primary);
  background: var(--app-primary);
  color: #fff;
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
  grid-template-columns: 72px 1fr 20px;
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
