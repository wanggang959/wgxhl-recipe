<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { closeToast, showFailToast, showSuccessToast } from 'vant'
import { deleteWantedRecipe, listWantedDates, pageWantedRecipe, updateWantedDate } from '../api/want'
import EmptyState from '../components/EmptyState.vue'
import { useUserStore } from '../stores/user'
import { getRecipeImage } from '../utils/imageUrl'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const list = ref([])
const dateList = ref([])
const selectedDate = ref(formatDate(new Date()))
const editVisible = ref(false)
const editTarget = ref(null)
const editDate = ref('')

const today = computed(() => formatDate(new Date()))
const tomorrow = computed(() => addDays(1))
const minDate = computed(() => today.value)
const filterDates = computed(() => {
  const fixed = [
    { value: today.value, label: '今日' },
    { value: tomorrow.value, label: '明日' },
  ]
  const extra = dateList.value
    .filter((date) => date !== today.value && date !== tomorrow.value)
    .map((date) => ({ value: date, label: formatDateLabel(date) }))
  return fixed.concat(extra)
})

onMounted(async () => {
  await refresh()
})

async function refresh() {
  await Promise.all([loadDateList(), loadWanted()])
}

async function loadDateList() {
  if (!userStore.userId) {
    dateList.value = []
    return
  }
  try {
    const res = await listWantedDates(userStore.userId)
    dateList.value = res.data || []
  } catch (error) {
    showFailToast(error.message || '想吃日期加载失败')
  }
}

async function loadWanted() {
  if (!userStore.userId) {
    list.value = []
    return
  }
  loading.value = true
  try {
    const res = await pageWantedRecipe({
      current: 1,
      size: 200,
      userId: userStore.userId,
      plannedDate: selectedDate.value,
    })
    list.value = res.data.records || []
  } catch (error) {
    showFailToast(error.message || '想吃列表加载失败')
  } finally {
    loading.value = false
  }
}

async function pickDate(date) {
  selectedDate.value = date
  await loadWanted()
}

function openEdit(item) {
  editTarget.value = item
  editDate.value = item.plannedDate || today.value
  editVisible.value = true
}

function closeEdit() {
  editVisible.value = false
  editTarget.value = null
  editDate.value = ''
}

async function submitEdit() {
  if (!editTarget.value || !editDate.value) {
    showFailToast('请选择想吃日期')
    return
  }
  try {
    await updateWantedDate({
      id: editTarget.value.id,
      plannedDate: editDate.value,
    })
    closeToast()
    showSuccessToast({ message: '已修改想吃日期', duration: 1400 })
    selectedDate.value = editDate.value
    closeEdit()
    await refresh()
  } catch (error) {
    closeToast()
    showFailToast({ message: error.message || '修改失败', duration: 1800 })
  }
}

async function remove(item) {
  try {
    await deleteWantedRecipe(item.id)
    closeToast()
    showSuccessToast({ message: '已删除', duration: 1400 })
    await refresh()
  } catch (error) {
    closeToast()
    showFailToast({ message: error.message || '删除失败', duration: 1800 })
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

function formatDateLabel(dateText) {
  const [, month, day] = dateText.split('-')
  return `${Number(month)}月${Number(day)}日`
}
</script>

<template>
  <section class="want-page">
    <div class="page-head">
      <div>
        <p>我的菜单</p>
        <h1>想吃</h1>
      </div>
      <span v-if="userStore.userId">{{ list.length }} 道</span>
    </div>

    <EmptyState
      v-if="!userStore.userId"
      text="请先登录，再把想吃的菜排上日子"
      button-text="去登录"
      @action="router.push('/profile')"
    />
    <template v-else>
      <div class="date-tabs">
        <button
          v-for="item in filterDates"
          :key="item.value"
          type="button"
          :class="{ active: selectedDate === item.value }"
          @click="pickDate(item.value)"
        >
          {{ item.label }}
        </button>
      </div>

      <van-loading v-if="loading" size="24px" class="loading">加载中...</van-loading>
      <EmptyState
        v-else-if="list.length === 0"
        text="这天还没有想吃的菜"
        button-text="去首页挑菜"
        @action="router.push('/recipes')"
      />
      <div v-else class="want-list">
        <article v-for="item in list" :key="item.id" class="want-item">
          <img :src="getRecipeImage(item.coverImage)" :alt="item.recipeName" @click="router.push(`/recipe/${item.recipeId}`)" />
          <div class="want-info" @click="router.push(`/recipe/${item.recipeId}`)">
            <strong>{{ item.recipeName }}</strong>
            <span>{{ formatDateLabel(item.plannedDate) }} 想吃</span>
          </div>
          <div class="want-actions" @click.stop>
            <button type="button" @click="openEdit(item)">
              <van-icon name="edit" />
              改日期
            </button>
            <button type="button" class="danger" @click="remove(item)">
              <van-icon name="delete-o" />
              删除
            </button>
          </div>
        </article>
      </div>
    </template>

    <van-popup v-model:show="editVisible" position="bottom" round>
      <div class="edit-sheet">
        <h3>{{ editTarget?.recipeName || '修改想吃日期' }}</h3>
        <label>
          <span>想吃日期</span>
          <input v-model="editDate" type="date" :min="minDate" />
        </label>
        <button type="button" class="primary" @click="submitEdit">保存</button>
        <button type="button" @click="closeEdit">取消</button>
      </div>
    </van-popup>
  </section>
</template>

<style scoped>
.want-page {
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

.date-tabs {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 2px;
}

.date-tabs button {
  flex: 0 0 auto;
  height: 34px;
  border: 1px solid var(--app-border);
  border-radius: 999px;
  padding: 0 12px;
  background: #fff;
  color: #7c5c46;
  font-weight: 800;
}

.date-tabs button.active {
  background: var(--app-primary);
  color: #fff;
  border-color: var(--app-primary);
}

.loading {
  margin-top: 30px;
}

.want-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.want-item {
  padding: 10px;
  border-radius: 18px;
  background: #fff;
  border: 1px solid var(--app-border);
  box-shadow: 0 12px 26px rgba(154, 52, 18, 0.08);
  display: grid;
  grid-template-columns: 74px minmax(0, 1fr);
  gap: 10px;
}

.want-item img {
  width: 74px;
  height: 74px;
  border-radius: 14px;
  object-fit: cover;
}

.want-info {
  min-width: 0;
  align-self: center;
}

.want-info strong {
  display: block;
  color: var(--app-text);
  font-size: 17px;
  overflow-wrap: anywhere;
}

.want-info span {
  display: block;
  margin-top: 5px;
  color: var(--app-muted);
  font-size: 12px;
}

.want-actions {
  grid-column: 1 / -1;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.want-actions button,
.edit-sheet button {
  height: 38px;
  border: 1px solid var(--app-border);
  border-radius: 999px;
  background: #fffaf2;
  color: #7c5c46;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  font-weight: 800;
}

.want-actions button.danger {
  background: #fff1f2;
  color: #be123c;
  border-color: #ffe4e6;
}

.edit-sheet {
  padding: 18px 14px max(18px, env(safe-area-inset-bottom));
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: #fffaf2;
}

.edit-sheet h3 {
  margin: 0;
  color: var(--app-text);
  text-align: center;
  font-size: 17px;
  overflow-wrap: anywhere;
}

.edit-sheet label {
  display: grid;
  gap: 6px;
  color: var(--app-muted);
  font-size: 13px;
}

.edit-sheet input {
  height: 42px;
  border: 1px solid var(--app-border);
  border-radius: 12px;
  padding: 0 11px;
  background: #fff;
  color: var(--app-text);
  outline: 0;
}

.edit-sheet button.primary {
  background: var(--app-primary);
  color: #fff;
  border-color: var(--app-primary);
}
</style>
