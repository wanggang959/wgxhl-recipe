<script setup>
import { computed, onActivated, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { closeToast, showFailToast, showSuccessToast } from 'vant'
import { listMembers } from '../api/user'
import {
  deleteWantedRecipe,
  getWantNotifyPreview,
  listWantedDates,
  notifyWantPrepare,
  pageWantedRecipe,
  updateWantedDate,
} from '../api/want'
import EmptyState from '../components/EmptyState.vue'
import { useUserStore } from '../stores/user'
import { DATA_REFRESH_EVENT, DATA_SCOPE, hasDataChanged, hasMatchingScope, markDataStale, rememberDataVersions } from '../utils/dataRefresh'
import { getRecipeImage } from '../utils/imageUrl'

const DEFAULT_NOTIFY_USER_ID = 'admin-wangshifu'
const CACHE_MAX_AGE = 60 * 1000
const wantListCache = {
  userId: '',
  list: [],
  dateList: [],
  selectedDate: '',
  notifyPreviewText: '',
  savedAt: 0,
}

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const cacheMatchesUser = wantListCache.userId === userStore.userId
const list = ref(cacheMatchesUser ? [...wantListCache.list] : [])
const dateList = ref(cacheMatchesUser ? [...wantListCache.dateList] : [])
const selectedDate = ref(cacheMatchesUser && wantListCache.selectedDate ? wantListCache.selectedDate : formatDate(new Date()))
const editVisible = ref(false)
const editTarget = ref(null)
const editDate = ref('')
const notifyVisible = ref(false)
const notifyLoading = ref(false)
const notifyMembers = ref([])
const selectedNotifyIds = ref([])
const notifyPreviewText = ref(cacheMatchesUser ? wantListCache.notifyPreviewText : '')
const watchedDataScopes = [DATA_SCOPE.wanted, DATA_SCOPE.recipes]
const seenDataVersions = {}

const today = computed(() => formatDate(new Date()))
const tomorrow = computed(() => addDays(1))
const minDate = computed(() => today.value)
const actorName = computed(() => userStore.user?.nickname || userStore.user?.username || '家人')

/** 有今天及之后的想吃即可显示通知按钮，不依赖预览接口 */
const showNotifyBtn = computed(() => (
  userStore.canMutate
  && (dateList.value.length > 0 || list.value.length > 0)
))

const filterDates = computed(() => {
  const fixed = [
    { value: today.value, label: '今日' },
    { value: tomorrow.value, label: '明日' },
  ]
  const extra = dateList.value
    .filter((date) => !isPastDate(date) && date !== today.value && date !== tomorrow.value)
    .map((date) => ({ value: date, label: formatDateLabel(date) }))
  return fixed.concat(extra)
})

onMounted(async () => {
  window.addEventListener(DATA_REFRESH_EVENT, handleDataRefresh)
  if (list.value.length > 0 || dateList.value.length > 0) {
    if (hasDataChanged(watchedDataScopes, seenDataVersions) || isCacheStale()) {
      refresh({ keepExisting: true, silent: true })
    }
    return
  }
  await refresh()
})

onActivated(() => {
  if ((list.value.length > 0 || dateList.value.length > 0) && (hasDataChanged(watchedDataScopes, seenDataVersions) || isCacheStale())) {
    refresh({ keepExisting: true, silent: true })
  }
})

onBeforeUnmount(() => {
  saveWantCache()
  window.removeEventListener(DATA_REFRESH_EVENT, handleDataRefresh)
})

async function refresh(options = {}) {
  await Promise.all([
    loadDateList(options),
    loadWanted(options),
    loadNotifyPreview(options),
  ])
  saveWantCache()
  rememberDataVersions(watchedDataScopes, seenDataVersions)
}

async function loadDateList(options = {}) {
  if (!userStore.userId) {
    dateList.value = []
    saveWantCache()
    return
  }
  try {
    const res = await listWantedDates(userStore.userId)
    dateList.value = (res.data || []).filter((date) => !isPastDate(date))
    ensureSelectedDate()
  } catch (error) {
    if (!options.silent) showFailToast(error.message || '想吃日期加载失败')
  }
}

async function loadWanted(options = {}) {
  if (!userStore.userId) {
    list.value = []
    saveWantCache()
    return
  }
  const showLoading = !options.keepExisting && list.value.length === 0
  if (showLoading) loading.value = true
  try {
    const res = await pageWantedRecipe({
      current: 1,
      size: 200,
      userId: userStore.userId,
      plannedDate: selectedDate.value,
    })
    list.value = res.data.records || []
    saveWantCache()
  } catch (error) {
    if (!options.silent || list.value.length === 0) {
      showFailToast(error.message || '想吃列表加载失败')
    }
  } finally {
    if (showLoading) loading.value = false
  }
}

async function pickDate(date) {
  selectedDate.value = date
  list.value = []
  saveWantCache()
  await loadWanted()
}

function isCacheStale() {
  return !wantListCache.savedAt || Date.now() - wantListCache.savedAt > CACHE_MAX_AGE
}

function saveWantCache() {
  wantListCache.userId = userStore.userId || ''
  wantListCache.list = [...list.value]
  wantListCache.dateList = [...dateList.value]
  wantListCache.selectedDate = selectedDate.value
  wantListCache.notifyPreviewText = notifyPreviewText.value
  wantListCache.savedAt = Date.now()
}

function handleDataRefresh(event) {
  if (!hasMatchingScope(event, watchedDataScopes)) return
  refresh({ keepExisting: list.value.length > 0 || dateList.value.length > 0, silent: true })
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
    markDataStale([DATA_SCOPE.wanted, DATA_SCOPE.todos])
    await refresh()
  } catch (error) {
    closeToast()
    showFailToast({ message: error.message || '修改失败', duration: 1800 })
  }
}

async function remove(item) {
  try {
    await deleteWantedRecipe(item.id)
    list.value = list.value.filter((entry) => entry.id !== item.id)
    saveWantCache()
    markDataStale([DATA_SCOPE.wanted, DATA_SCOPE.todos])
    closeToast()
    showSuccessToast({ message: '已删除', duration: 1400 })
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

function isPastDate(dateText) {
  return dateText < today.value
}

function ensureSelectedDate() {
  if (isPastDate(selectedDate.value)) {
    selectedDate.value = today.value
  }
}

async function loadNotifyPreview(options = {}) {
  if (!userStore.userId) {
    notifyPreviewText.value = ''
    saveWantCache()
    return
  }
  try {
    const res = await getWantNotifyPreview()
    if (res.data?.previewBody) {
      notifyPreviewText.value = res.data.previewBody
      return
    }
  } catch (error) {
    // 后端未更新时走本地预览，避免通知按钮消失
  }
  notifyPreviewText.value = await buildNotifyPreviewFallback()
  saveWantCache()
}

async function buildNotifyPreviewFallback() {
  try {
    const res = await pageWantedRecipe({
      current: 1,
      size: 200,
      userId: userStore.userId,
      plannedDateStart: today.value,
    })
    const records = (res.data?.records || []).slice().sort((a, b) => {
      const dateCompare = String(a.plannedDate).localeCompare(String(b.plannedDate))
      if (dateCompare !== 0) return dateCompare
      return String(a.recipeName || '').localeCompare(String(b.recipeName || ''))
    })
    if (!records.length) return ''
    const date = records[0].plannedDate
    const names = records
      .filter((item) => item.plannedDate === date)
      .map((item) => item.recipeName)
      .filter(Boolean)
    return `${actorName.value}${dayLabel(date)}想吃${joinRecipeNames(names)}，点击前往备菜`
  } catch (error) {
    return ''
  }
}

function dayLabel(dateText) {
  if (dateText === today.value) return '今天'
  if (dateText === tomorrow.value) return '明天'
  const dayAfter = addDays(2)
  if (dateText === dayAfter) return '后天'
  return formatDateLabel(dateText)
}

function joinRecipeNames(names) {
  if (!names.length) return '几道菜'
  if (names.length === 1) return names[0]
  if (names.length === 2) return `${names[0]}和${names[1]}`
  return `${names.slice(0, -1).join('、')}和${names[names.length - 1]}`
}

function memberLabel(member) {
  return member.nickname || member.username || '家人'
}

function isNotifyTargetSelected(userId) {
  return selectedNotifyIds.value.includes(userId)
}

function toggleNotifyTarget(userId) {
  if (isNotifyTargetSelected(userId)) {
    selectedNotifyIds.value = selectedNotifyIds.value.filter((id) => id !== userId)
    return
  }
  selectedNotifyIds.value = [...selectedNotifyIds.value, userId]
}

async function openNotifySheet() {
  if (!showNotifyBtn.value) {
    showFailToast('今天及之后还没有想吃的菜')
    return
  }
  await loadNotifyPreview()
  if (!notifyPreviewText.value) {
    showFailToast('今天及之后还没有想吃的菜')
    return
  }
  try {
    const res = await listMembers()
    notifyMembers.value = res.data || []
    const defaults = notifyMembers.value
      .filter((member) => member.id === DEFAULT_NOTIFY_USER_ID)
      .map((member) => member.id)
    selectedNotifyIds.value = defaults.length
      ? defaults
      : notifyMembers.value.slice(0, 1).map((member) => member.id)
    notifyVisible.value = true
  } catch (error) {
    showFailToast(error.message || '家人列表加载失败')
  }
}

function closeNotifySheet() {
  notifyVisible.value = false
}

async function submitNotify() {
  if (!selectedNotifyIds.value.length) {
    showFailToast('请至少选择一位家人')
    return
  }
  notifyLoading.value = true
  try {
    const res = await notifyWantPrepare({
      targetUserIds: selectedNotifyIds.value,
    })
    closeToast()
    showSuccessToast({ message: res.message || '已发送通知', duration: 1600 })
    closeNotifySheet()
  } catch (error) {
    closeToast()
    showFailToast({ message: error.message || '通知发送失败', duration: 1800 })
  } finally {
    notifyLoading.value = false
  }
}
</script>

<template>
  <section class="want-page">
    <div class="page-head">
      <div>
        <p>我的菜单</p>
        <h1>想吃</h1>
      </div>
      <span v-if="userStore.userId" class="page-head-count">{{ list.length }} 道</span>
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

      <button
        v-if="showNotifyBtn"
        type="button"
        class="notify-bar-btn"
        @click="openNotifySheet"
      >
        <van-icon name="bell" />
        通知家人备菜
      </button>

      <van-loading v-if="loading && list.length === 0" size="24px" class="loading">加载中...</van-loading>
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
          <div v-if="userStore.canMutate" class="want-actions" @click.stop>
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

    <van-popup v-model:show="notifyVisible" position="bottom" round>
      <div class="notify-sheet">
        <h3>通知家人备菜</h3>
        <p class="notify-preview">{{ notifyPreviewText }}</p>
        <div class="notify-members">
          <button
            v-for="member in notifyMembers"
            :key="member.id"
            type="button"
            :class="{ active: isNotifyTargetSelected(member.id) }"
            @click="toggleNotifyTarget(member.id)"
          >
            {{ memberLabel(member) }}
          </button>
        </div>
        <button type="button" class="primary" :disabled="notifyLoading" @click="submitNotify">
          {{ notifyLoading ? '发送中...' : '发送通知' }}
        </button>
        <button type="button" @click="closeNotifySheet">取消</button>
      </div>
    </van-popup>

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

.page-head-count {
  color: var(--app-muted);
  font-size: 13px;
}

.notify-bar-btn {
  width: 100%;
  height: 42px;
  border: 1px solid #fed7aa;
  border-radius: 14px;
  background: linear-gradient(180deg, #fff7ed 0%, #ffedd5 100%);
  color: var(--app-primary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-weight: 800;
  font-size: 15px;
  box-shadow: 0 8px 18px rgba(234, 88, 12, 0.12);
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

.edit-sheet button.primary,
.notify-sheet button.primary {
  background: var(--app-primary);
  color: #fff;
  border-color: var(--app-primary);
}

.notify-sheet {
  padding: 18px 14px max(18px, env(safe-area-inset-bottom));
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: #fffaf2;
}

.notify-sheet h3 {
  margin: 0;
  color: var(--app-text);
  text-align: center;
  font-size: 17px;
}

.notify-preview {
  margin: 0;
  padding: 10px 12px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid var(--app-border);
  color: #7c5c46;
  font-size: 13px;
  line-height: 1.5;
}

.notify-members {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.notify-members button {
  height: 36px;
  border: 1px solid var(--app-border);
  border-radius: 999px;
  padding: 0 14px;
  background: #fff;
  color: #7c5c46;
  font-weight: 800;
}

.notify-members button.active {
  background: var(--app-primary);
  color: #fff;
  border-color: var(--app-primary);
}

.notify-sheet button {
  height: 42px;
  border: 1px solid var(--app-border);
  border-radius: 999px;
  background: #fffaf2;
  color: #7c5c46;
  font-weight: 800;
}

.notify-sheet button:disabled {
  opacity: 0.6;
}
</style>
