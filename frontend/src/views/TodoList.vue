<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { closeToast, showConfirmDialog, showFailToast, showSuccessToast } from 'vant'
import { completeTodo, deleteTodo, getTodoSummary, pageTodo } from '../api/todo'
import { pageUser } from '../api/user'
import { deleteWantedRecipe, pageWantedRecipe } from '../api/want'
import EmptyState from '../components/EmptyState.vue'
import TodoCard from '../components/TodoCard.vue'
import { useUserStore } from '../stores/user'
import { getRecipeImage } from '../utils/imageUrl'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const loading = ref(false)
const list = ref([])
const wantedList = ref([])
const userNameMap = ref({})
const activeCategory = ref('')
const activeTime = ref('all')
const summary = ref({
  todayCount: 0,
  doneCount: 0,
  dueSoonCount: 0,
  totalCount: 0,
})

const timeFilters = [
  ['all', '全部'],
  ['today', '今日'],
  ['three', '近三天'],
  ['week', '近一周'],
]

const categories = [
  ['', '全部'],
  ['COOK', '做饭'],
  ['GROCERY', '采购'],
  ['LIFE', '生活'],
  ['BIRTHDAY', '生日'],
  ['SERVER', '服务器'],
  ['DOMAIN', '域名'],
  ['PAYMENT', '缴费'],
  ['OTHER', '其他'],
]

const combinedItems = computed(() => {
  const todoItems = list.value.map((item) => ({
    type: 'todo',
    id: item.id,
    category: item.category,
    date: dateFromDateTime(item.dueTime),
    sortTime: sortValue(item.dueTime),
    data: item,
  }))
  const cookItems = wantedList.value.map((item) => ({
    type: 'wanted',
    id: item.id,
    category: 'COOK',
    date: item.plannedDate,
    sortTime: `${item.plannedDate || '9999-12-31'}T00:00:00`,
    data: item,
  }))
  return [...todoItems, ...cookItems]
})

const filteredItems = computed(() => combinedItems.value
  .filter((item) => categoryMatches(item))
  .filter((item) => timeMatches(item.date))
  .sort((a, b) => String(a.sortTime).localeCompare(String(b.sortTime))))

const groupedItems = computed(() => {
  const groups = []
  filteredItems.value.forEach((item) => {
    const key = item.date || 'none'
    let group = groups.find((entry) => entry.key === key)
    if (!group) {
      group = {
        key,
        label: formatDateLabel(item.date),
        items: [],
      }
      groups.push(group)
    }
    group.items.push(item)
  })
  return groups
})

const displayTodayCount = computed(() => combinedItems.value.filter((item) => timeMatchesDate(item.date, 'today')).length)
const displayDueSoonCount = computed(() => combinedItems.value.filter((item) => timeMatchesDate(item.date, 'week')).length)

function handleRouteRefresh(event) {
  const path = event?.detail?.path
  if (!path || path === '/todo' || path.startsWith('/todo')) {
    loadTodo()
  }
}

onMounted(() => {
  loadTodo()
  window.addEventListener('app:route-refresh', handleRouteRefresh)
})

onUnmounted(() => {
  window.removeEventListener('app:route-refresh', handleRouteRefresh)
})

watch(
  () => route.path,
  (path) => {
    if (path === '/todo') {
      loadTodo()
    }
  },
)

async function loadTodo(options = {}) {
  if (!userStore.userId) return
  const showLoading = options.showLoading ?? (list.value.length === 0 && wantedList.value.length === 0)
  if (showLoading) loading.value = true
  try {
    const [pageRes, summaryRes, wantedRes] = await Promise.all([
      pageTodo({ current: 1, size: 200 }),
      getTodoSummary(),
      pageWantedRecipe({
        current: 1,
        size: 200,
        plannedDateStart: formatDate(new Date()),
      }),
      loadFamilyMembers(),
    ])
    list.value = pageRes.data.records || []
    summary.value = summaryRes.data || summary.value
    wantedList.value = wantedRes.data.records || []
  } catch (error) {
    showFailToast(error.message || '待办加载失败')
  } finally {
    if (showLoading) loading.value = false
  }
}

async function loadFamilyMembers() {
  const res = await pageUser({ current: 1, size: 200 })
  const nextMap = {}
  ;(res.data.records || []).forEach((user) => {
    nextMap[user.id] = user.nickname || user.username || '家人'
  })
  userNameMap.value = nextMap
}

function categoryMatches(item) {
  if (!activeCategory.value) return true
  return item.category === activeCategory.value
}

function timeMatches(dateText) {
  return timeMatchesDate(dateText, activeTime.value)
}

function timeMatchesDate(dateText, range) {
  if (range === 'all') return true
  if (!dateText) return false
  const diff = dayDiff(dateText)
  if (range === 'today') return diff === 0
  if (range === 'three') return diff >= 0 && diff < 3
  if (range === 'week') return diff >= 0 && diff < 7
  return true
}

function dateFromDateTime(value) {
  if (!value) return ''
  return String(value).slice(0, 10)
}

function sortValue(value) {
  if (!value) return '9999-12-31T23:59:59'
  return String(value)
}

function dayDiff(dateText) {
  const today = new Date(formatDate(new Date()))
  const target = new Date(dateText)
  return Math.round((target - today) / 86400000)
}

function formatDateLabel(dateText) {
  if (!dateText) return '未定时间'
  const today = formatDate(new Date())
  const tomorrow = addDays(1)
  if (dateText === today) return '今日'
  if (dateText === tomorrow) return '明日'
  const [, month, day] = dateText.split('-')
  return `${Number(month)}月${Number(day)}日`
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

function getUserName(userId) {
  return userNameMap.value[userId] || '家人'
}

async function finishWanted(item) {
  try {
    await showConfirmDialog({
      title: '完成做饭计划',
      message: `确认完成「${item.recipeName}」吗？`,
    })
    await deleteWantedRecipe(item.id)
    closeToast()
    showSuccessToast({ message: '已完成', duration: 1400 })
    await loadTodo({ showLoading: false })
  } catch (error) {
    if (error?.message) showFailToast(error.message || '操作失败')
  }
}

async function finish(item) {
  try {
    if (item.canComplete === false) {
      showFailToast(item.completeDisabledReason || '暂时不能完成')
      return
    }
    if (item.category === 'BIRTHDAY') {
      await showConfirmDialog({
        title: '完成生日提醒',
        message: `确认完成「${item.title}」吗？生日提醒只能当天完成。`,
      })
      await completeTodo(item.id)
      closeToast()
      showSuccessToast({ message: '已完成', duration: 1300 })
      await loadTodo({ showLoading: false })
      return
    }
    await showConfirmDialog({
      title: '进入下一个周期',
      message: `确认处理「${item.title}」吗？接下来需要填写下一个到期时间。`,
    })
    router.push(`/todo/${item.id}/edit?nextCycle=1`)
  } catch (error) {
    if (error?.message) showFailToast(error.message || '操作失败')
  }
}

function completeBlocked(item) {
  showFailToast(item.completeDisabledReason || '暂时不能完成')
}

async function remove(item) {
  try {
    await showConfirmDialog({
      title: '删除待办',
      message: `确认删除「${item.title}」吗？`,
    })
    await deleteTodo(item.id)
    showSuccessToast('已删除')
    await loadTodo({ showLoading: false })
  } catch (error) {
    if (error?.message) showFailToast(error.message)
  }
}
</script>

<template>
  <section class="todo-page">
    <div class="overview-card">
      <div>
        <p>待办清单</p>
        <h1>今日 {{ displayTodayCount }} 件</h1>
        <span>已完成 {{ summary.doneCount }} 件 · 近期待办 {{ displayDueSoonCount }} 件</span>
      </div>
      <div class="overview-actions">
        <button type="button" @click="router.push('/todo/summary?range=today')">
          <van-icon name="cart-o" />
          采购汇总
        </button>
        <button type="button" class="ghost" @click="router.push('/todo/create')">
          <van-icon name="plus" />
          新增待办
        </button>
      </div>
    </div>

    <div class="filter-card">
      <div class="filter-row time-row">
        <span>范围</span>
        <div>
          <button
            v-for="[value, label] in timeFilters"
            :key="value"
            type="button"
            :class="{ active: activeTime === value }"
            @click="activeTime = value"
          >
            {{ label }}
          </button>
        </div>
      </div>
      <div class="filter-row category-row">
        <span>类型</span>
        <div>
          <button
            v-for="[value, label] in categories"
            :key="value || 'all'"
            type="button"
            :class="{ active: activeCategory === value }"
            @click="activeCategory = value"
          >
            {{ label }}
          </button>
        </div>
      </div>
    </div>

    <van-loading v-if="loading" size="24px" class="loading">加载中...</van-loading>
    <EmptyState
      v-else-if="filteredItems.length === 0"
      text="这个筛选下还没有待办"
      button-text="新增待办"
      @action="router.push('/todo/create')"
    />
    <div v-else class="group-list">
      <section v-for="group in groupedItems" :key="group.key" class="todo-group">
        <h2>{{ group.label }}</h2>
        <template v-for="item in group.items" :key="`${item.type}-${item.id}`">
          <TodoCard
            v-if="item.type === 'todo'"
            :todo="item.data"
            @open="router.push(`/todo/${item.data.id}`)"
            @complete="finish"
            @complete-blocked="completeBlocked"
            @edit="router.push(`/todo/${item.data.id}/edit`)"
            @delete="remove"
          />
          <article v-else class="wanted-item">
            <img
              :src="getRecipeImage(item.data.coverImage)"
              :alt="item.data.recipeName"
              @click="router.push(`/recipe/${item.data.recipeId}`)"
            />
            <div @click="router.push(`/recipe/${item.data.recipeId}`)">
              <strong>{{ item.data.recipeName }}</strong>
              <span>{{ getUserName(item.data.userId) }} · 计划 {{ item.data.plannedDate }}</span>
            </div>
            <button type="button" @click="finishWanted(item.data)">
              <van-icon name="success" />
              完成
            </button>
          </article>
        </template>
      </section>
    </div>
  </section>
</template>

<style scoped>
.todo-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.overview-card {
  padding: 15px;
  border-radius: 20px;
  background: #fff;
  border: 1px solid rgba(245, 223, 199, 0.96);
  box-shadow: 0 12px 26px rgba(154, 52, 18, 0.07);
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.overview-card p,
.overview-card h1,
.overview-card span {
  margin: 0;
}

.overview-card p {
  color: var(--app-primary);
  font-size: 13px;
  font-weight: 900;
}

.overview-card h1 {
  margin-top: 4px;
  color: var(--app-text);
  font-size: 25px;
  line-height: 1.18;
}

.overview-card span {
  display: block;
  margin-top: 6px;
  color: var(--app-muted);
  font-size: 13px;
}

.overview-actions {
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.overview-actions button {
  height: 36px;
  border: 0;
  border-radius: 13px;
  padding: 0 12px;
  background: var(--app-primary);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-size: 13px;
  font-weight: 900;
  white-space: nowrap;
  box-shadow: 0 8px 16px rgba(249, 115, 22, 0.18);
}

.overview-actions button.ghost {
  background: #fff;
  color: var(--app-primary);
  border: 1px solid #fed7aa;
  box-shadow: none;
}

.filter-card {
  padding: 11px;
  border: 1px solid rgba(245, 223, 199, 0.92);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.82);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.filter-row {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
}

.filter-row > span {
  color: var(--app-muted);
  font-size: 12px;
  font-weight: 900;
}

.filter-row > div {
  display: flex;
  gap: 7px;
  overflow-x: auto;
  padding-bottom: 1px;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.filter-row > div::-webkit-scrollbar {
  display: none;
}

.filter-row button {
  flex: 0 0 auto;
  height: 31px;
  border: 1px solid var(--app-border);
  border-radius: 999px;
  padding: 0 12px;
  background: #fffaf2;
  color: #7c5c46;
  font-weight: 800;
}

.time-row button {
  background: #fff;
}

.filter-row button.active {
  background: var(--app-primary);
  border-color: var(--app-primary);
  color: #fff;
  box-shadow: 0 8px 16px rgba(249, 115, 22, 0.18);
}

.loading {
  margin-top: 30px;
}

.group-list,
.todo-group {
  display: flex;
  flex-direction: column;
  gap: 11px;
}

.todo-group h2 {
  margin: 4px 2px 0;
  color: var(--app-text);
  font-size: 18px;
  line-height: 1.2;
}

.wanted-item {
  padding: 12px;
  border-radius: 18px;
  background: linear-gradient(180deg, #fff 0%, #fffdf9 100%);
  border: 1px solid rgba(245, 223, 199, 0.96);
  box-shadow: 0 12px 26px rgba(154, 52, 18, 0.07);
  display: grid;
  grid-template-columns: 58px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
}

.wanted-item img {
  width: 58px;
  height: 58px;
  border-radius: 13px;
  object-fit: cover;
}

.wanted-item strong {
  display: block;
  color: var(--app-text);
  font-size: 17px;
  line-height: 1.25;
  overflow-wrap: anywhere;
}

.wanted-item span {
  display: block;
  margin-top: 4px;
  color: var(--app-muted);
  font-size: 12px;
}

.wanted-item button {
  box-sizing: border-box;
  min-width: 72px;
  height: 34px;
  border: 0;
  border-radius: 999px;
  padding: 0 12px;
  background: var(--app-primary);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-size: 14px;
  font-weight: 800;
  line-height: 1;
  white-space: nowrap;
  box-shadow: 0 8px 16px rgba(249, 115, 22, 0.18);
}
</style>
