<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { closeToast, showFailToast, showSuccessToast } from 'vant'
import { deleteFavorite, pageFavorite } from '../api/favorite'
import { getRecipeDetail } from '../api/recipe'
import { checkWantedRecipe, createWantedRecipe } from '../api/want'
import EmptyState from '../components/EmptyState.vue'
import RecipeCard from '../components/RecipeCard.vue'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const list = ref([])
const wantedMap = ref({})
const wantedPendingMap = ref({})
const wantActionVisible = ref(false)
const wantDateVisible = ref(false)
const wantTargetRecipe = ref(null)
const customWantDate = ref('')

const minWantDate = computed(() => formatDate(new Date()))

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
    const favorites = (res.data.records || []).map((item) => ({
      ...item,
      id: item.recipeId,
      favoriteId: item.id,
    }))
    list.value = await enrichFavoriteRecipes(favorites)
    await syncWantedState(list.value)
  } catch (error) {
    showFailToast(error.message || '收藏加载失败')
  } finally {
    loading.value = false
  }
}

async function enrichFavoriteRecipes(records) {
  const results = await Promise.allSettled(
    records.map((item) =>
      getRecipeDetail(item.recipeId).then((res) => ({
        ...item,
        ...(res.data?.recipe || {}),
        id: item.recipeId,
        favoriteId: item.favoriteId,
      })),
    ),
  )
  return records.map((item, index) => (
    results[index].status === 'fulfilled' ? results[index].value : item
  ))
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
        :wanted="Boolean(wantedMap[item.id])"
        @open="router.push(`/recipe/${item.id}`)"
        @favorite="removeSafe"
        @want="openWantAction"
      />
    </div>

    <van-popup v-model:show="wantActionVisible" position="bottom" round>
      <div class="want-sheet">
        <h3>{{ wantTargetRecipe?.recipeName || '这道菜' }}</h3>
        <button type="button" class="want-sheet-primary" @click="openWantDatePicker">
          <van-icon name="cart-o" />
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
</style>
