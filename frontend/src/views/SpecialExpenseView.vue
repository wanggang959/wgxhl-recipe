<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { showConfirmDialog, showFailToast, showSuccessToast } from 'vant'
import {
  archiveExpenseProject,
  createExpenseItem,
  createExpenseProject,
  deleteExpenseItem,
  getExpenseSummary,
  pageExpenseItems,
  pageExpenseProjects,
  restoreExpenseProject,
  updateExpenseItem,
  updateExpenseProject,
} from '../api/expense'
import { listMembers } from '../api/user'
import EmptyState from '../components/EmptyState.vue'
import { useUserStore } from '../stores/user'
import { DATA_SCOPE, markDataChanged } from '../utils/dataRefresh'

const userStore = useUserStore()
const loading = ref(false)
const projects = ref([])
const items = ref([])
const members = ref([])
const selectedProjectId = ref('')
const itemStatusFilter = ref('')
const itemCategoryFilter = ref('')
const projectPopupVisible = ref(false)
const itemPopupVisible = ref(false)
const savingProject = ref(false)
const savingItem = ref(false)
const showArchived = ref(false)
const summary = ref({
  totalBudget: 0,
  actualAmount: 0,
  remainingBudget: 0,
  itemCount: 0,
  pendingCount: 0,
  doneCount: 0,
  overBudgetCount: 0,
})

const projectForm = reactive({
  id: '',
  projectName: '',
  projectType: 'WEDDING',
  totalBudget: '',
  startDate: '',
  endDate: '',
  remark: '',
})

const itemForm = reactive({
  id: '',
  projectId: '',
  itemName: '',
  category: 'OTHER',
  budgetAmount: '',
  actualAmount: '',
  quantity: '',
  status: 'TODO',
  payerId: '',
  ownerId: '',
  purchaseDate: '',
  plannedTime: '',
  purchaseChannel: '',
  purchaseLink: '',
  remark: '',
  imageUrl: '',
})

const projectTypes = [
  ['WEDDING', '婚礼'],
  ['DECORATION', '装修'],
  ['MOVE', '搬家'],
  ['TRAVEL', '旅行'],
  ['BABY', '备产'],
  ['OTHER', '其他'],
]

const itemCategories = [
  ['', '全部'],
  ['FOOD', '食品'],
  ['CLOTHES', '服饰'],
  ['JEWELRY', '首饰'],
  ['HOME_TEXTILE', '家纺'],
  ['APPLIANCE', '家电'],
  ['BANQUET', '酒席'],
  ['HOME', '婚房'],
  ['DOCUMENT', '证件'],
  ['SUPPLY', '用品'],
  ['OTHER', '其他'],
]

const itemStatuses = [
  ['', '全部'],
  ['CONFIRM', '待确认'],
  ['TODO', '待购买'],
  ['ORDERED', '已下单'],
  ['RECEIVED', '已到货'],
  ['DONE', '已完成'],
  ['CANCELLED', '已取消'],
]

const activeProjects = computed(() => projects.value.filter((item) => showArchived.value || !item.archived))
const selectedProject = computed(() => projects.value.find((item) => item.id === selectedProjectId.value) || null)
const isEditingProject = computed(() => Boolean(projectForm.id))
const isEditingItem = computed(() => Boolean(itemForm.id))
const canWrite = computed(() => userStore.isLogin && !userStore.isGuest)
const displayItems = computed(() => [...items.value].sort((left, right) => {
  const leftDone = left.status === 'DONE' ? 1 : 0
  const rightDone = right.status === 'DONE' ? 1 : 0
  return leftDone - rightDone
}))
const spendPercent = computed(() => {
  const total = Number(summary.value.totalBudget || 0)
  if (total <= 0) return 0
  return Math.min(100, Math.round((Number(summary.value.actualAmount || 0) / total) * 100))
})

onMounted(async () => {
  await Promise.all([loadMembers(), loadProjects()])
})

watch(selectedProjectId, () => {
  loadProjectData()
})

async function loadMembers() {
  try {
    const res = await listMembers()
    members.value = res.data || []
  } catch (error) {
    members.value = []
  }
}

async function loadProjects() {
  loading.value = true
  try {
    const res = await pageExpenseProjects({
      current: 1,
      size: 100,
      archived: showArchived.value ? null : false,
    })
    projects.value = res.data.records || []
    if (!selectedProjectId.value || !projects.value.some((item) => item.id === selectedProjectId.value)) {
      selectedProjectId.value = projects.value[0]?.id || ''
    }
    await loadProjectData()
  } catch (error) {
    showFailToast(error.message || '专项开支加载失败')
  } finally {
    loading.value = false
  }
}

async function loadProjectData() {
  if (!selectedProjectId.value) {
    items.value = []
    summary.value = {
      totalBudget: 0,
      actualAmount: 0,
      remainingBudget: 0,
      itemCount: 0,
      pendingCount: 0,
      doneCount: 0,
      overBudgetCount: 0,
    }
    return
  }
  const [itemRes, summaryRes] = await Promise.all([
    pageExpenseItems({
      current: 1,
      size: 300,
      projectId: selectedProjectId.value,
      category: itemCategoryFilter.value,
      status: itemStatusFilter.value,
    }),
    getExpenseSummary(selectedProjectId.value),
  ])
  items.value = itemRes.data.records || []
  summary.value = summaryRes.data || summary.value
}

function resetProjectForm() {
  projectForm.id = ''
  projectForm.projectName = ''
  projectForm.projectType = 'WEDDING'
  projectForm.totalBudget = ''
  projectForm.startDate = ''
  projectForm.endDate = ''
  projectForm.remark = ''
}

function openProjectForm(project = null) {
  if (!canWrite.value) return
  if (project) {
    projectForm.id = project.id
    projectForm.projectName = project.projectName || ''
    projectForm.projectType = project.projectType || 'OTHER'
    projectForm.totalBudget = amountInput(project.totalBudget)
    projectForm.startDate = project.startDate || ''
    projectForm.endDate = project.endDate || ''
    projectForm.remark = project.remark || ''
  } else {
    resetProjectForm()
  }
  projectPopupVisible.value = true
}

async function saveProject() {
  if (!projectForm.projectName.trim()) {
    showFailToast('请输入专项名称')
    return
  }
  savingProject.value = true
  try {
    const payload = {
      ...projectForm,
      projectName: projectForm.projectName.trim(),
      totalBudget: numberOrZero(projectForm.totalBudget),
      startDate: projectForm.startDate || null,
      endDate: projectForm.endDate || null,
      remark: projectForm.remark.trim(),
    }
    if (isEditingProject.value) {
      await updateExpenseProject(payload)
      showSuccessToast('专项已更新')
    } else {
      const res = await createExpenseProject(payload)
      selectedProjectId.value = res.data.id
      showSuccessToast('专项已创建')
    }
    projectPopupVisible.value = false
    await loadProjects()
  } catch (error) {
    showFailToast(error.message || '专项保存失败')
  } finally {
    savingProject.value = false
  }
}

async function toggleArchiveProject(project) {
  if (!project || !canWrite.value) return
  try {
    await showConfirmDialog({
      title: project.archived ? '恢复专项' : '归档专项',
      message: project.archived
        ? `确认恢复「${project.projectName}」吗？`
        : `确认归档「${project.projectName}」吗？归档后默认不再展示。`,
    })
    if (project.archived) {
      await restoreExpenseProject(project.id)
    } else {
      await archiveExpenseProject(project.id)
    }
    showSuccessToast(project.archived ? '已恢复专项' : '已归档专项')
    await loadProjects()
  } catch (error) {
    if (error?.message) showFailToast(error.message)
  }
}

function resetItemForm() {
  itemForm.id = ''
  itemForm.projectId = selectedProjectId.value
  itemForm.itemName = ''
  itemForm.category = 'OTHER'
  itemForm.budgetAmount = ''
  itemForm.actualAmount = ''
  itemForm.quantity = ''
  itemForm.status = 'TODO'
  itemForm.payerId = userStore.userId || ''
  itemForm.ownerId = userStore.userId || ''
  itemForm.purchaseDate = ''
  itemForm.plannedTime = ''
  itemForm.purchaseChannel = ''
  itemForm.purchaseLink = ''
  itemForm.remark = ''
  itemForm.imageUrl = ''
}

function openItemForm(item = null) {
  if (!canWrite.value || !selectedProjectId.value) return
  if (item) {
    itemForm.id = item.id
    itemForm.projectId = item.projectId
    itemForm.itemName = item.itemName || ''
    itemForm.category = item.category || 'OTHER'
    itemForm.budgetAmount = amountInput(item.budgetAmount)
    itemForm.actualAmount = amountInput(item.actualAmount)
    itemForm.quantity = item.quantity || ''
    itemForm.status = item.status || 'TODO'
    itemForm.payerId = item.payerId || ''
    itemForm.ownerId = item.ownerId || ''
    itemForm.purchaseDate = item.purchaseDate || ''
    itemForm.plannedTime = toDateTimeInput(item.plannedTime)
    itemForm.purchaseChannel = item.purchaseChannel || ''
    itemForm.purchaseLink = item.purchaseLink || ''
    itemForm.remark = item.remark || ''
    itemForm.imageUrl = item.imageUrl || ''
  } else {
    resetItemForm()
  }
  itemPopupVisible.value = true
}

async function saveItem() {
  if (!itemForm.itemName.trim()) {
    showFailToast('请输入开支名称')
    return
  }
  savingItem.value = true
  try {
    const payload = {
      ...itemForm,
      projectId: selectedProjectId.value,
      itemName: itemForm.itemName.trim(),
      budgetAmount: numberOrZero(itemForm.budgetAmount),
      actualAmount: numberOrZero(itemForm.actualAmount),
      quantity: itemForm.quantity.trim(),
      payerId: itemForm.payerId || null,
      ownerId: itemForm.ownerId || null,
      purchaseDate: itemForm.purchaseDate || null,
      plannedTime: fromDateTimeInput(itemForm.plannedTime),
      purchaseChannel: itemForm.purchaseChannel.trim(),
      purchaseLink: itemForm.purchaseLink.trim(),
      remark: itemForm.remark.trim(),
      imageUrl: itemForm.imageUrl.trim(),
    }
    if (isEditingItem.value) {
      await updateExpenseItem(payload)
      showSuccessToast('开支项已更新')
    } else {
      await createExpenseItem(payload)
      showSuccessToast('开支项已新增')
    }
    itemPopupVisible.value = false
    markDataChanged([DATA_SCOPE.expenses, DATA_SCOPE.todos])
    await Promise.all([loadProjectData(), loadProjects()])
  } catch (error) {
    showFailToast(error.message || '开支项保存失败')
  } finally {
    savingItem.value = false
  }
}

async function quickStatus(item, status) {
  if (!canWrite.value) return
  try {
    await updateExpenseItem({
      ...item,
      status,
      purchaseDate: status === 'DONE' ? (item.purchaseDate || formatDate(new Date())) : item.purchaseDate,
    })
    markDataChanged([DATA_SCOPE.expenses])
    await Promise.all([loadProjectData(), loadProjects()])
  } catch (error) {
    showFailToast(error.message || '状态更新失败')
  }
}

async function removeItem(item) {
  if (!canWrite.value) return
  try {
    await showConfirmDialog({
      title: '删除开支项',
      message: `确认删除「${item.itemName}」吗？`,
    })
    await deleteExpenseItem(item.id)
    showSuccessToast('已删除')
    markDataChanged([DATA_SCOPE.expenses, DATA_SCOPE.todos])
    await Promise.all([loadProjectData(), loadProjects()])
  } catch (error) {
    if (error?.message) showFailToast(error.message)
  }
}

function typeLabel(value) {
  return projectTypes.find((item) => item[0] === value)?.[1] || '其他'
}

function categoryLabel(value) {
  return itemCategories.find((item) => item[0] === value)?.[1] || '其他'
}

function statusLabel(value) {
  return itemStatuses.find((item) => item[0] === value)?.[1] || '待购买'
}

function formatMoney(value) {
  return Number(value || 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  })
}

function numberOrZero(value) {
  const num = Number(value)
  return Number.isFinite(num) ? num : 0
}

function amountInput(value) {
  const num = Number(value || 0)
  return num > 0 ? String(num) : ''
}

function toDateTimeInput(value) {
  return value ? String(value).slice(0, 16) : ''
}

function fromDateTimeInput(value) {
  return value ? `${String(value).slice(0, 16)}:00` : null
}

function formatDate(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function dateText(value) {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 16)
}
</script>

<template>
  <section class="expense-page">
    <header class="expense-hero">
      <div>
        <p>家庭专项</p>
        <h1>专项开支</h1>
        <span>记录阶段性大事的预算、购买和完成情况</span>
      </div>
      <button v-if="canWrite" type="button" @click="openProjectForm()">
        <van-icon name="plus" />
        专项
      </button>
    </header>

    <div class="project-strip">
      <button
        v-for="project in activeProjects"
        :key="project.id"
        type="button"
        :class="{ active: selectedProjectId === project.id }"
        @click="selectedProjectId = project.id"
      >
        <strong>{{ project.projectName }}</strong>
        <span>{{ typeLabel(project.projectType) }} · 已花 {{ formatMoney(project.actualAmount) }}</span>
      </button>
      <button v-if="canWrite" type="button" class="ghost-project" @click="openProjectForm()">
        <van-icon name="plus" />
        新专项
      </button>
    </div>

    <EmptyState
      v-if="!loading && projects.length === 0"
      text="还没有专项，先建一个婚礼采购，慢慢把要买的东西放进来。"
      button-text="新增专项"
      :show-button="canWrite"
      @action="openProjectForm()"
    />

    <template v-if="selectedProject">
      <section class="summary-card">
        <div class="summary-head">
          <div>
            <p>{{ typeLabel(selectedProject.projectType) }}</p>
            <h2>{{ selectedProject.projectName }}</h2>
          </div>
          <div class="summary-actions">
            <button v-if="canWrite" type="button" @click="openProjectForm(selectedProject)">
              <van-icon name="edit" />
            </button>
            <button v-if="canWrite" type="button" @click="toggleArchiveProject(selectedProject)">
              <van-icon :name="selectedProject.archived ? 'replay' : 'completed-o'" />
            </button>
          </div>
        </div>
        <div class="money-grid">
          <div>
            <span>总预算</span>
            <strong>¥{{ formatMoney(summary.totalBudget) }}</strong>
          </div>
          <div>
            <span>已花费</span>
            <strong>¥{{ formatMoney(summary.actualAmount) }}</strong>
          </div>
          <div>
            <span>剩余</span>
            <strong :class="{ danger: Number(summary.remainingBudget || 0) < 0 }">
              ¥{{ formatMoney(summary.remainingBudget) }}
            </strong>
          </div>
        </div>
        <div class="progress-line">
          <i :style="{ width: `${spendPercent}%` }"></i>
        </div>
        <div class="summary-foot">
          <span>待处理 {{ summary.pendingCount || 0 }}</span>
          <span>已完成 {{ summary.doneCount || 0 }}</span>
          <span>超预算 {{ summary.overBudgetCount || 0 }}</span>
        </div>
      </section>

      <section class="filter-card">
        <div class="filter-row">
          <span>分类</span>
          <div>
            <button
              v-for="item in itemCategories"
              :key="item[0]"
              type="button"
              :class="{ active: itemCategoryFilter === item[0] }"
              @click="itemCategoryFilter = item[0]; loadProjectData()"
            >
              {{ item[1] }}
            </button>
          </div>
        </div>
        <div class="filter-row">
          <span>状态</span>
          <div>
            <button
              v-for="item in itemStatuses"
              :key="item[0]"
              type="button"
              :class="{ active: itemStatusFilter === item[0] }"
              @click="itemStatusFilter = item[0]; loadProjectData()"
            >
              {{ item[1] }}
            </button>
          </div>
        </div>
      </section>

      <div class="list-head">
        <h2>开支清单</h2>
        <button v-if="canWrite" type="button" @click="openItemForm()">
          <van-icon name="plus" />
          新增
        </button>
      </div>

      <EmptyState
        v-if="items.length === 0"
        text="暂无开支项，可以先从婚礼用品、服饰、酒席这些大项开始记。"
        button-text="新增开支"
        :show-button="canWrite"
        @action="openItemForm()"
      />

      <div v-else class="expense-list">
        <article v-for="item in displayItems" :key="item.id" class="expense-card" :class="{ 'is-done': item.status === 'DONE' }">
          <div class="expense-icon">
            <van-icon name="balance-list-o" />
          </div>
          <div class="expense-main">
            <div class="expense-title">
              <h3>{{ item.itemName }}</h3>
              <span :class="{ completed: item.status === 'DONE' }">{{ statusLabel(item.status) }}</span>
            </div>
            <div class="expense-meta">
              <span>{{ categoryLabel(item.category) }}</span>
              <span v-if="item.quantity">{{ item.quantity }}</span>
              <span v-if="item.ownerName">负责人：{{ item.ownerName }}</span>
              <span v-if="item.payerName">付款：{{ item.payerName }}</span>
            </div>
            <div class="expense-money">
              <span>预算 ¥{{ formatMoney(item.budgetAmount) }}</span>
              <strong>实际 ¥{{ formatMoney(item.actualAmount) }}</strong>
            </div>
            <div class="expense-extra">
              <span v-if="item.plannedTime">计划 {{ dateText(item.plannedTime) }}</span>
              <span v-if="item.purchaseDate">购买 {{ item.purchaseDate }}</span>
              <span v-if="item.purchaseChannel">{{ item.purchaseChannel }}</span>
            </div>
            <p v-if="item.remark" class="expense-remark">{{ item.remark }}</p>
            <div v-if="canWrite" class="expense-actions">
              <button v-if="item.status !== 'DONE'" type="button" class="primary" @click="quickStatus(item, 'DONE')">
                <van-icon name="success" />
                完成
              </button>
              <button type="button" @click="openItemForm(item)">
                <van-icon name="edit" />
                编辑
              </button>
              <button type="button" class="danger" @click="removeItem(item)">
                <van-icon name="delete-o" />
              </button>
            </div>
          </div>
        </article>
      </div>
    </template>

    <van-popup v-model:show="projectPopupVisible" position="bottom" round>
      <div class="sheet">
        <h2>{{ isEditingProject ? '编辑专项' : '新增专项' }}</h2>
        <van-field v-model="projectForm.projectName" label="名称" placeholder="例如：婚礼采购" />
        <label>
          <span>类型</span>
          <select v-model="projectForm.projectType">
            <option v-for="item in projectTypes" :key="item[0]" :value="item[0]">{{ item[1] }}</option>
          </select>
        </label>
        <van-field v-model="projectForm.totalBudget" label="总预算" type="number" placeholder="例如：30000" />
        <div class="sheet-grid">
          <van-field v-model="projectForm.startDate" label="开始" type="date" />
          <van-field v-model="projectForm.endDate" label="结束" type="date" />
        </div>
        <van-field v-model="projectForm.remark" label="备注" type="textarea" rows="2" placeholder="补充说明" />
        <div class="sheet-actions">
          <van-button round type="warning" block :loading="savingProject" @click="saveProject">保存</van-button>
          <van-button round block @click="projectPopupVisible = false">取消</van-button>
        </div>
      </div>
    </van-popup>

    <van-popup v-model:show="itemPopupVisible" position="bottom" round>
      <div class="sheet item-sheet">
        <h2>{{ isEditingItem ? '编辑开支项' : '新增开支项' }}</h2>
        <van-field v-model="itemForm.itemName" label="名称" placeholder="例如：喜糖、婚纱、床品" />
        <div class="sheet-grid">
          <label>
            <span>分类</span>
            <select v-model="itemForm.category">
              <option v-for="item in itemCategories.filter((entry) => entry[0])" :key="item[0]" :value="item[0]">
                {{ item[1] }}
              </option>
            </select>
          </label>
          <label>
            <span>状态</span>
            <select v-model="itemForm.status">
              <option v-for="item in itemStatuses.filter((entry) => entry[0])" :key="item[0]" :value="item[0]">
                {{ item[1] }}
              </option>
            </select>
          </label>
        </div>
        <div class="sheet-grid">
          <van-field v-model="itemForm.budgetAmount" label="预算" type="number" />
          <van-field v-model="itemForm.actualAmount" label="实际" type="number" />
        </div>
        <van-field v-model="itemForm.quantity" label="数量" placeholder="例如：100份、1套" />
        <div class="sheet-grid">
          <label>
            <span>负责人</span>
            <select v-model="itemForm.ownerId">
              <option value="">不指定</option>
              <option v-for="member in members" :key="member.id" :value="member.id">
                {{ member.nickname || member.username }}
              </option>
            </select>
          </label>
          <label>
            <span>付款人</span>
            <select v-model="itemForm.payerId">
              <option value="">不指定</option>
              <option v-for="member in members" :key="member.id" :value="member.id">
                {{ member.nickname || member.username }}
              </option>
            </select>
          </label>
        </div>
        <van-field v-model="itemForm.plannedTime" label="计划购买" type="datetime-local" />
        <van-field v-model="itemForm.purchaseDate" label="购买日期" type="date" />
        <van-field v-model="itemForm.purchaseChannel" label="渠道" placeholder="淘宝、京东、线下门店等" />
        <van-field v-model="itemForm.purchaseLink" label="链接" placeholder="商品或订单链接" />
        <van-field v-model="itemForm.remark" label="备注" type="textarea" rows="2" />
        <div class="sheet-actions">
          <van-button round type="warning" block :loading="savingItem" @click="saveItem">保存</van-button>
          <van-button round block @click="itemPopupVisible = false">取消</van-button>
        </div>
      </div>
    </van-popup>
  </section>
</template>

<style scoped>
.expense-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.expense-hero,
.summary-card,
.filter-card,
.expense-card {
  border: 1px solid var(--app-border);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 12px 26px rgba(154, 52, 18, 0.07);
}

.expense-hero {
  padding: 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.expense-hero p,
.expense-hero h1,
.expense-hero span,
.summary-head p,
.summary-head h2 {
  margin: 0;
}

.expense-hero p,
.summary-head p {
  color: var(--app-primary);
  font-size: 12px;
  font-weight: 800;
}

.expense-hero h1 {
  margin-top: 2px;
  font-size: 24px;
  line-height: 1.1;
}

.expense-hero span {
  display: block;
  margin-top: 5px;
  color: var(--app-muted);
  font-size: 12px;
}

.expense-hero button,
.list-head button {
  border: 0;
  border-radius: 999px;
  background: var(--app-primary);
  color: #fff;
  height: 38px;
  padding: 0 14px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-weight: 800;
}

.project-strip {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  padding-bottom: 2px;
  scrollbar-width: none;
}

.project-strip::-webkit-scrollbar {
  display: none;
}

.project-strip button {
  flex: 0 0 150px;
  min-height: 70px;
  border: 1px solid var(--app-border);
  border-radius: 16px;
  background: #fff;
  padding: 12px;
  text-align: left;
  color: var(--app-text);
}

.project-strip button.active {
  border-color: var(--app-primary);
  background: #fff7ed;
  box-shadow: 0 10px 20px rgba(249, 115, 22, 0.12);
}

.project-strip strong,
.project-strip span {
  display: block;
}

.project-strip strong {
  font-size: 15px;
}

.project-strip span {
  margin-top: 5px;
  color: var(--app-muted);
  font-size: 12px;
}

.project-strip .ghost-project {
  flex-basis: 108px;
  color: var(--app-primary);
  display: grid;
  place-items: center;
  text-align: center;
  font-weight: 800;
}

.summary-card {
  padding: 16px;
}

.summary-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.summary-head h2,
.list-head h2 {
  font-size: 20px;
}

.summary-actions {
  display: flex;
  gap: 8px;
}

.summary-actions button {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  border: 1px solid var(--app-border);
  background: #fffaf2;
  color: var(--app-primary);
}

.money-grid {
  margin-top: 14px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.money-grid div {
  min-width: 0;
  padding: 10px;
  border-radius: 14px;
  background: #fff7ed;
}

.money-grid span,
.money-grid strong {
  display: block;
}

.money-grid span {
  color: var(--app-muted);
  font-size: 11px;
}

.money-grid strong {
  margin-top: 4px;
  font-size: 15px;
  overflow-wrap: anywhere;
}

.money-grid .danger {
  color: var(--app-danger);
}

.progress-line {
  height: 8px;
  margin-top: 12px;
  border-radius: 999px;
  background: #ffedd5;
  overflow: hidden;
}

.progress-line i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--app-primary);
}

.summary-foot {
  margin-top: 10px;
  display: flex;
  gap: 12px;
  color: var(--app-muted);
  font-size: 12px;
}

.filter-card {
  padding: 10px 12px;
}

.filter-row {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  align-items: start;
  gap: 8px;
  margin: 6px 0;
}

.filter-row > span {
  padding-top: 8px;
  color: #7c5c46;
  font-size: 12px;
  font-weight: 800;
}

.filter-row > div {
  display: flex;
  gap: 7px;
  overflow-x: auto;
  scrollbar-width: none;
}

.filter-row > div::-webkit-scrollbar {
  display: none;
}

.filter-row button {
  flex: 0 0 auto;
  height: 32px;
  border: 1px solid var(--app-border);
  border-radius: 999px;
  background: #fff;
  color: #7c5c46;
  padding: 0 12px;
  font-weight: 700;
}

.filter-row button.active {
  border-color: var(--app-primary);
  background: var(--app-primary);
  color: #fff;
}

.list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 2px;
}

.list-head h2 {
  margin: 0;
}

.expense-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.expense-card {
  padding: 14px;
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  gap: 12px;
}

.expense-card.is-done {
  background: #fffdfb;
}

.expense-icon {
  width: 42px;
  height: 42px;
  border-radius: 13px;
  border: 1px solid #ffedd5;
  background: #fff7ed;
  color: var(--app-primary);
  display: grid;
  place-items: center;
  font-size: 20px;
}

.expense-main {
  min-width: 0;
}

.expense-title {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}

.expense-title h3 {
  margin: 0;
  font-size: 17px;
  line-height: 1.25;
}

.expense-title span {
  flex: 0 0 auto;
  height: 24px;
  border-radius: 999px;
  background: #fff7ed;
  color: var(--app-primary);
  padding: 4px 8px;
  font-size: 12px;
  font-weight: 800;
}

.expense-title span.completed {
  background: #fff1f2;
  color: #dc2626;
  border: 1px solid #fecdd3;
}

.expense-meta,
.expense-extra {
  margin-top: 7px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px 10px;
  color: var(--app-muted);
  font-size: 12px;
}

.expense-money {
  margin-top: 8px;
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 13px;
}

.expense-money strong {
  color: var(--app-primary);
}

.expense-remark {
  margin: 8px 0 0;
  color: #7c5c46;
  font-size: 12px;
  line-height: 1.5;
}

.expense-actions {
  margin-top: 12px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.expense-actions button {
  min-width: 42px;
  height: 32px;
  border: 1px solid var(--app-border);
  border-radius: 999px;
  background: #fff;
  color: #7c5c46;
  padding: 0 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-weight: 800;
}

.expense-actions .primary {
  border: 0;
  background: var(--app-primary);
  color: #fff;
}

.expense-actions .danger {
  padding: 0;
  color: var(--app-danger);
  background: #fff7f7;
}

.sheet {
  max-height: min(86dvh, 720px);
  overflow-y: auto;
  padding: 18px 16px calc(18px + var(--safe-area-bottom));
  background: #fffaf2;
}

.sheet h2 {
  margin: 0 0 14px;
  font-size: 20px;
}

.sheet label {
  display: block;
  margin-bottom: 10px;
}

.sheet label span {
  display: block;
  margin: 0 0 6px;
  color: #7c5c46;
  font-size: 13px;
  font-weight: 800;
}

.sheet select {
  width: 100%;
  height: 44px;
  border: 1px solid var(--app-border);
  border-radius: 12px;
  background: #fff;
  padding: 0 12px;
}

.sheet :deep(.van-cell) {
  margin-bottom: 10px;
  border: 1px solid var(--app-border);
  border-radius: 12px;
  background: #fff;
}

.sheet-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.sheet-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-top: 8px;
}
</style>
