<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { closeToast, showFailToast, showSuccessToast } from 'vant'
import { pageUser } from '../api/user'
import { deleteWantedRecipe, pageWantedRecipe } from '../api/want'
import EmptyState from '../components/EmptyState.vue'
import { useUserStore } from '../stores/user'
import { getRecipeImage } from '../utils/imageUrl'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const list = ref([])
const userNameMap = ref({})

const groupedList = computed(() => {
  const groups = []
  list.value.forEach((item) => {
    let group = groups.find((entry) => entry.date === item.plannedDate)
    if (!group) {
      group = {
        date: item.plannedDate,
        label: formatDateLabel(item.plannedDate),
        items: [],
      }
      groups.push(group)
    }
    group.items.push(item)
  })
  return groups
})

onMounted(() => {
  loadTodo()
})

async function loadTodo() {
  if (!userStore.userId) {
    list.value = []
    return
  }
  loading.value = true
  try {
    const [wantedRes] = await Promise.all([
      pageWantedRecipe({
        current: 1,
        size: 200,
      }),
      loadFamilyMembers(),
    ])
    list.value = wantedRes.data.records || []
  } catch (error) {
    showFailToast(error.message || '待办加载失败')
  } finally {
    loading.value = false
  }
}

async function loadFamilyMembers() {
  const res = await pageUser({
    current: 1,
    size: 200,
  })
  const nextMap = {}
  ;(res.data.records || []).forEach((user) => {
    nextMap[user.id] = user.nickname || user.username || '家人'
  })
  userNameMap.value = nextMap
}

async function finish(item) {
  try {
    await deleteWantedRecipe(item.id)
    closeToast()
    showSuccessToast({ message: '已完成', duration: 1400 })
    await loadTodo()
  } catch (error) {
    closeToast()
    showFailToast({ message: error.message || '操作失败', duration: 1800 })
  }
}

function formatDateLabel(dateText) {
  if (!dateText) return '未定日期'
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
</script>

<template>
  <section class="todo-page">
    <div class="page-head">
      <div>
        <p>做饭安排</p>
        <h1>家庭待办</h1>
      </div>
      <div class="head-side">
        <span v-if="userStore.userId">{{ list.length }} 项</span>
        <button v-if="userStore.userId && list.length > 0" type="button" @click="router.push('/todo/summary?range=today')">
          <van-icon name="records-o" />
          采购汇总
        </button>
      </div>
    </div>

    <EmptyState
      v-if="!userStore.userId"
      text="请先登录，再查看家庭待办"
      button-text="去登录"
      @action="router.push('/profile')"
    />
    <van-loading v-else-if="loading" size="24px" class="loading">加载中...</van-loading>
    <EmptyState
      v-if="userStore.userId && !loading && list.length === 0"
      text="家里还没有待办，去首页把想吃的菜加进来吧"
      button-text="去首页"
      @action="router.push('/recipes')"
    />
    <div v-else-if="userStore.userId && !loading" class="todo-groups">
      <section v-for="group in groupedList" :key="group.date" class="todo-group">
        <h2>{{ group.label }}</h2>
        <article v-for="item in group.items" :key="item.id" class="todo-item">
          <img :src="getRecipeImage(item.coverImage)" :alt="item.recipeName" @click="router.push(`/recipe/${item.recipeId}`)" />
          <div @click="router.push(`/recipe/${item.recipeId}`)">
            <strong>{{ item.recipeName }}</strong>
            <span>{{ getUserName(item.userId) }}想吃 · {{ item.plannedDate }}</span>
          </div>
          <button type="button" @click="finish(item)">
            <van-icon name="success" />
            完成
          </button>
        </article>
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

.head-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.head-side button {
  height: 32px;
  border: 0;
  border-radius: 999px;
  padding: 0 10px;
  background: var(--app-primary);
  color: #fff;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  font-weight: 800;
}

.loading {
  margin-top: 30px;
}

.todo-groups,
.todo-group {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.todo-group h2 {
  margin: 0;
  color: var(--app-text);
  font-size: 18px;
}

.todo-item {
  padding: 10px;
  border-radius: 18px;
  background: #fff;
  border: 1px solid var(--app-border);
  box-shadow: 0 12px 26px rgba(154, 52, 18, 0.08);
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
}

.todo-item img {
  width: 64px;
  height: 64px;
  border-radius: 14px;
  object-fit: cover;
}

.todo-item strong {
  display: block;
  color: var(--app-text);
  font-size: 16px;
  overflow-wrap: anywhere;
}

.todo-item span {
  display: block;
  margin-top: 4px;
  color: var(--app-muted);
  font-size: 12px;
}

.todo-item button {
  box-sizing: border-box;
  min-width: 78px;
  height: 36px;
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
  appearance: none;
  -webkit-appearance: none;
}

.todo-item button :deep(.van-icon) {
  flex: 0 0 auto;
  line-height: 1;
}
</style>
