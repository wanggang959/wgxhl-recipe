<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showFailToast, showSuccessToast } from 'vant'
import { getRecipeDetail } from '../api/recipe'
import { createTodo } from '../api/todo'
import { pageWantedRecipe } from '../api/want'
import EmptyState from '../components/EmptyState.vue'
import { getImageUrl } from '../utils/imageUrl'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const wantedList = ref([])
const recipeDetailMap = ref({})
const activeRange = ref(route.query.range || 'today')
const markMap = ref({})
const markCacheReady = ref(false)
const buyEditorVisible = ref(false)
const buyEditorTarget = ref(null)
const buyQuantity = ref('')

const MARK_CACHE_PREFIX = 'wgxhl_recipe_purchase_summary_marks_v1:'

const rangeOptions = [
  { key: 'today', label: '今日' },
  { key: 'tomorrow', label: '明日' },
  { key: 'next2', label: '近两日' },
  { key: 'next3', label: '近三日' },
  { key: 'all', label: '全部' },
]

const filteredWantedList = computed(() =>
  wantedList.value.filter((item) => isInRange(item.plannedDate, activeRange.value)),
)

const selectedRecipeDetails = computed(() => {
  return filteredWantedList.value
    .map((item) => recipeDetailMap.value[item.recipeId])
    .filter(Boolean)
})

const ingredientSummary = computed(() => buildIngredientSummary(selectedRecipeDetails.value))
const seasoningSummary = computed(() => buildSeasoningSummary(selectedRecipeDetails.value))
const summarySignature = computed(() => JSON.stringify({
  ingredients: ingredientSummary.value.map((item) => [item.name, item.amount]).sort(),
  seasonings: seasoningSummary.value.map((item) => item.name).sort(),
}))

onMounted(() => {
  loadSummary()
})

watch(
  () => route.query.range,
  (range) => {
    activeRange.value = range || 'today'
  },
)

watch(
  [summarySignature, activeRange],
  () => {
    loadMarkCache()
  },
  { immediate: true },
)

watch(
  markMap,
  () => {
    saveMarkCache()
  },
  { deep: true },
)

function changeRange(range) {
  activeRange.value = range
  router.replace({
    path: '/todo/summary',
    query: { range },
  })
}

async function addPurchaseTodo() {
  const lines = [
    ...ingredientSummary.value.map((item) => `${item.name} ${item.amount}`),
    ...seasoningSummary.value.map((item) => item.name),
  ]
  if (lines.length === 0) {
    showFailToast('暂无可加入待办的采购项')
    return
  }
  try {
    await createTodo({
      title: '采购任务',
      category: 'GROCERY',
      description: lines.join('\n'),
      dueTime: `${formatDate(new Date())}T18:00:00`,
      repeatType: 'NONE',
      notifySite: true,
      notifyEmail: false,
      noticeMinutes: [0],
    })
    showSuccessToast('已加入待办')
  } catch (error) {
    showFailToast(error.message || '加入待办失败')
  }
}

async function loadSummary() {
  loading.value = true
  try {
    const wantedRes = await pageWantedRecipe({
      current: 1,
      size: 500,
    })
    wantedList.value = wantedRes.data.records || []
    const recipeIds = [...new Set(wantedList.value.map((item) => item.recipeId).filter(Boolean))]
    const detailResults = await Promise.allSettled(recipeIds.map((id) => getRecipeDetail(id)))
    const nextMap = {}
    detailResults
      .filter((item) => item.status === 'fulfilled')
      .map((item) => item.value.data)
      .filter(Boolean)
      .forEach((detail) => {
        nextMap[detail.recipe?.id] = detail
      })
    recipeDetailMap.value = nextMap
  } catch (error) {
    showFailToast(error.message || '采购汇总加载失败')
  } finally {
    loading.value = false
  }
}

function buildIngredientSummary(details) {
  const map = new Map()
  details.forEach((detail) => {
    ;(detail.ingredientList || []).forEach((item) => {
      const name = item.ingredientName || '未命名食材'
      const unit = item.unit || ''
      const amountText = String(item.amount || '').trim()
      const numeric = parseNumber(amountText)
      if (!map.has(name)) {
        map.set(name, {
          name,
          image: item.ingredientImage || '',
          unitParts: new Map(),
        })
      }
      const target = map.get(name)
      if (!target.image && item.ingredientImage) {
        target.image = item.ingredientImage
      }
      if (!target.unitParts.has(unit)) {
        target.unitParts.set(unit, {
          unit,
          total: 0,
          hasNumeric: false,
          extras: [],
        })
      }
      const unitPart = target.unitParts.get(unit)
      if (numeric !== null) {
        unitPart.total += numeric
        unitPart.hasNumeric = true
      } else if (amountText) {
        unitPart.extras.push(amountText)
      }
    })
  })
  return [...map.values()].map((item) => ({
    key: `ingredient:${item.name}:${formatAmount(item)}`,
    name: item.name,
    image: item.image,
    amount: formatAmount(item),
  }))
}

function buildSeasoningSummary(details) {
  const map = new Map()
  details.forEach((detail) => {
    ;(detail.seasoningList || []).forEach((item) => {
      const name = item.seasoningName || '未命名调料'
      if (!map.has(name)) {
        map.set(name, {
          key: `seasoning:${name}`,
          name,
          image: item.seasoningImage || '',
        })
      }
    })
  })
  return [...map.values()]
}

function parseNumber(text) {
  if (!text) return null
  if (!/^\d+(\.\d+)?$/.test(text)) return null
  return Number(text)
}

function formatAmount(item) {
  const parts = []
  item.unitParts.forEach((unitPart) => {
    if (unitPart.hasNumeric) {
      const total = Number.isInteger(unitPart.total)
        ? String(unitPart.total)
        : String(Number(unitPart.total.toFixed(2)))
      parts.push(`${total}${unitPart.unit}`)
    }
    unitPart.extras.forEach((text) => {
      parts.push(`${text}${unitPart.unit}`)
    })
  })
  return parts.length > 0 ? parts.join('+') : '适量'
}

function getMark(key) {
  return markMap.value[key] || { status: '', quantity: '' }
}

function setMark(key, status) {
  const current = getMark(key)
  markMap.value = {
    ...markMap.value,
    [key]: {
      status,
      quantity: status === 'buy' ? current.quantity : '',
    },
  }
}

function setBuyMark(key, quantity) {
  markMap.value = {
    ...markMap.value,
    [key]: {
      status: 'buy',
      quantity,
    },
  }
}

function clearMark(key) {
  const nextMap = { ...markMap.value }
  delete nextMap[key]
  markMap.value = nextMap
}

function openBuyEditor(item) {
  buyEditorTarget.value = item
  buyQuantity.value = getMark(item.key).quantity || ''
  buyEditorVisible.value = true
}

function submitBuyEditor() {
  if (!buyEditorTarget.value) return
  setBuyMark(buyEditorTarget.value.key, buyQuantity.value.trim())
  buyEditorVisible.value = false
  buyEditorTarget.value = null
  buyQuantity.value = ''
}

function statusClass(key) {
  const status = getMark(key).status
  return {
    'is-enough': status === 'enough',
    'is-buy': status === 'buy',
  }
}

function statusLabel(key) {
  const mark = getMark(key)
  if (mark.status === 'enough') return '已够'
  if (mark.status === 'buy') return mark.quantity ? `待购 ${mark.quantity}` : '待购'
  return ''
}

function markSeasoningBuy(key) {
  setBuyMark(key, '')
}

function cacheKey() {
  return `${MARK_CACHE_PREFIX}${activeRange.value}`
}

function loadMarkCache() {
  markCacheReady.value = false
  try {
    const raw = localStorage.getItem(cacheKey())
    if (!raw) {
      markMap.value = {}
      return
    }
    const cache = JSON.parse(raw)
    markMap.value = cache.signature === summarySignature.value ? (cache.marks || {}) : {}
  } catch (error) {
    markMap.value = {}
  } finally {
    markCacheReady.value = true
  }
}

function saveMarkCache() {
  if (!markCacheReady.value) return
  localStorage.setItem(cacheKey(), JSON.stringify({
    signature: summarySignature.value,
    marks: markMap.value,
  }))
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

function isInRange(dateText, range) {
  if (!dateText) return range === 'all'
  const today = formatDate(new Date())
  if (range === 'all') return true
  if (range === 'today') return dateText === today
  if (range === 'tomorrow') return dateText === addDays(1)

  const endMap = {
    next2: addDays(1),
    next3: addDays(2),
  }
  const end = endMap[range]
  return Boolean(end) && dateText >= today && dateText <= end
}
</script>

<template>
  <section class="summary-page">
    <div class="page-head">
      <button type="button" @click="router.push('/todo')">
        <van-icon name="arrow-left" />
      </button>
      <div>
        <p>做饭安排</p>
        <h1>采购汇总</h1>
      </div>
      <button type="button" class="add-todo-btn" @click="addPurchaseTodo">
        <van-icon name="plus" />
        待办
      </button>
    </div>

    <div class="range-tabs">
      <button
        v-for="item in rangeOptions"
        :key="item.key"
        type="button"
        :class="{ active: activeRange === item.key }"
        @click="changeRange(item.key)"
      >
        {{ item.label }}
      </button>
    </div>

    <van-loading v-if="loading" size="24px" class="loading">汇总中...</van-loading>
    <EmptyState
      v-else-if="filteredWantedList.length === 0"
      text="这个时间范围里还没有待办菜"
      button-text="返回待办"
      @action="router.push('/todo')"
    />
    <template v-else>
      <section class="summary-section">
        <h2>食材</h2>
        <div v-if="ingredientSummary.length === 0" class="simple-empty">暂无食材</div>
        <van-swipe-cell v-for="item in ingredientSummary" v-else :key="item.key" class="summary-swipe">
          <article class="summary-item" :class="statusClass(item.key)">
            <img v-if="item.image" :src="getImageUrl(item.image)" :alt="item.name" />
            <div v-else class="item-placeholder">
              <van-icon name="photo-o" />
            </div>
            <div class="item-main">
              <strong>{{ item.name }}</strong>
              <span>{{ item.amount }}</span>
              <em v-if="statusLabel(item.key)">{{ statusLabel(item.key) }}</em>
            </div>
          </article>
          <template #right>
            <div class="swipe-actions">
              <button type="button" class="enough" @click="setMark(item.key, 'enough')">已够</button>
              <button type="button" class="buy" @click="openBuyEditor(item)">待购</button>
              <button type="button" class="clear" @click="clearMark(item.key)">清除</button>
            </div>
          </template>
        </van-swipe-cell>
      </section>

      <section class="summary-section">
        <h2>调料</h2>
        <div v-if="seasoningSummary.length === 0" class="simple-empty">暂无调料</div>
        <div v-else class="seasoning-list">
          <van-swipe-cell v-for="item in seasoningSummary" :key="item.key" class="seasoning-swipe">
            <article class="seasoning-item" :class="statusClass(item.key)">
              <img v-if="item.image" :src="getImageUrl(item.image)" :alt="item.name" />
              <div v-else class="item-placeholder">
                <van-icon name="photo-o" />
              </div>
              <div class="item-main">
                <strong>{{ item.name }}</strong>
                <em v-if="statusLabel(item.key)">{{ statusLabel(item.key) }}</em>
              </div>
            </article>
            <template #right>
              <div class="swipe-actions">
                <button type="button" class="enough" @click="setMark(item.key, 'enough')">已够</button>
                <button type="button" class="buy" @click="markSeasoningBuy(item.key)">待购</button>
                <button type="button" class="clear" @click="clearMark(item.key)">清除</button>
              </div>
            </template>
          </van-swipe-cell>
        </div>
      </section>
    </template>

    <van-popup v-model:show="buyEditorVisible" position="bottom" round>
      <div class="buy-sheet">
        <h3>{{ buyEditorTarget?.name || '待购数量' }}</h3>
        <label>
          <span>待购数量</span>
          <input v-model="buyQuantity" placeholder="比如：2袋、500克、1瓶" />
        </label>
        <button type="button" class="primary" @click="submitBuyEditor">保存</button>
        <button type="button" @click="buyEditorVisible = false">取消</button>
      </div>
    </van-popup>
  </section>
</template>

<style scoped>
.summary-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.page-head {
  padding: 14px;
  border-radius: 20px;
  background: #fff;
  border: 1px solid var(--app-border);
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
}

.page-head button {
  width: 34px;
  height: 34px;
  border: 0;
  border-radius: 50%;
  background: #fff7e8;
  color: var(--app-primary);
  display: grid;
  place-items: center;
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
  color: var(--app-text);
  font-size: 25px;
}

.page-head span {
  color: var(--app-muted);
  font-size: 13px;
}

.add-todo-btn {
  height: 34px;
  border: 0;
  border-radius: 999px;
  padding: 0 10px;
  background: var(--app-primary);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-size: 13px;
  font-weight: 800;
}

.range-tabs {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 2px;
}

.range-tabs button {
  flex: 0 0 auto;
  height: 34px;
  border: 1px solid var(--app-border);
  border-radius: 999px;
  padding: 0 12px;
  background: #fff;
  color: #7c5c46;
  font-weight: 800;
}

.range-tabs button.active {
  background: var(--app-primary);
  color: #fff;
  border-color: var(--app-primary);
}

.loading {
  margin-top: 30px;
}

.summary-section {
  padding: 14px;
  border-radius: 18px;
  background: #fff;
  border: 1px solid var(--app-border);
  box-shadow: 0 12px 26px rgba(154, 52, 18, 0.08);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.summary-section h2 {
  margin: 0;
  color: var(--app-text);
  font-size: 18px;
}

.summary-swipe {
  border-radius: 14px;
  overflow: hidden;
}

.summary-item {
  min-height: 62px;
  border: 1px solid var(--app-border);
  border-radius: 14px;
  background: #fff;
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  padding: 8px;
}

.summary-item img,
.seasoning-item img,
.item-placeholder {
  width: 42px;
  height: 42px;
  border-radius: 12px;
}

.summary-item img,
.seasoning-item img {
  object-fit: cover;
}

.item-placeholder {
  background: #fff7e8;
  color: var(--app-primary);
  display: grid;
  place-items: center;
}

.item-main {
  min-width: 0;
}

.summary-item strong,
.seasoning-item strong {
  display: block;
  min-width: 0;
  color: var(--app-text);
  overflow-wrap: anywhere;
}

.item-main span {
  display: block;
  margin-top: 3px;
  color: var(--app-primary);
  font-size: 13px;
  font-weight: 800;
}

.item-main em {
  display: inline-flex;
  max-width: 100%;
  margin-top: 6px;
  padding: 4px 8px;
  border-radius: 999px;
  background: #fff7e8;
  color: #c2410c;
  font-size: 12px;
  font-style: normal;
  font-weight: 900;
}

.summary-item.is-enough,
.seasoning-item.is-enough {
  background: #f3f4f6;
  border-color: #e5e7eb;
  color: #6b7280;
}

.summary-item.is-enough img,
.seasoning-item.is-enough img {
  opacity: 0.52;
  filter: grayscale(0.35);
}

.summary-item.is-enough .item-main em,
.seasoning-item.is-enough .item-main em {
  background: #e5e7eb;
  color: #4b5563;
}

.summary-item.is-buy,
.seasoning-item.is-buy {
  background: #fff7e8;
  border-color: #fed7aa;
  box-shadow: inset 3px 0 0 #f97316;
}

.summary-item.is-buy .item-main em,
.seasoning-item.is-buy .item-main em {
  background: var(--app-primary);
  color: #fff;
}

.swipe-actions {
  height: 100%;
  display: flex;
}

.swipe-actions button {
  width: 58px;
  border: 0;
  color: #fff;
  font-size: 13px;
  font-weight: 900;
}

.swipe-actions button.enough {
  background: #6b7280;
}

.swipe-actions button.buy {
  background: var(--app-primary);
}

.swipe-actions button.clear {
  background: #9b7d66;
}

.seasoning-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.seasoning-swipe {
  min-width: 0;
  border-radius: 14px;
  overflow: hidden;
}

.seasoning-item {
  min-width: 0;
  padding: 8px;
  border-radius: 14px;
  background: #fffaf2;
  border: 1px solid var(--app-border);
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
}

.seasoning-item strong {
  font-size: 14px;
  font-weight: 800;
}

.simple-empty {
  color: var(--app-muted);
  font-size: 13px;
}

.buy-sheet {
  padding: 18px 14px max(18px, env(safe-area-inset-bottom));
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: #fffaf2;
}

.buy-sheet h3 {
  margin: 0;
  color: var(--app-text);
  text-align: center;
  font-size: 17px;
  overflow-wrap: anywhere;
}

.buy-sheet label {
  display: grid;
  gap: 6px;
  color: var(--app-muted);
  font-size: 13px;
}

.buy-sheet input {
  height: 42px;
  border: 1px solid var(--app-border);
  border-radius: 12px;
  padding: 0 11px;
  background: #fff;
  color: var(--app-text);
  outline: 0;
}

.buy-sheet button {
  height: 40px;
  border: 1px solid var(--app-border);
  border-radius: 999px;
  background: #fff;
  color: #7c5c46;
  font-weight: 800;
}

.buy-sheet button.primary {
  border-color: var(--app-primary);
  background: var(--app-primary);
  color: #fff;
}
</style>
