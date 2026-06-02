<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showFailToast, showSuccessToast } from 'vant'
import { pageCategory } from '../api/category'
import { checkFavorite, createFavorite, deleteFavoriteByRecipeId } from '../api/favorite'
import { deleteRecipe, pageRecipe } from '../api/recipe'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()

const categories = ref([])
const list = ref([])
const loading = ref(false)
const finished = ref(false)
const total = ref(0)
const favoriteMap = ref({})
const saveMessage = ref('')

const query = reactive({
  current: 1,
  size: 8,
  recipeName: '',
  categoryId: '',
  difficulty: '',
  status: '',
})

const suggestRecipe = ref(null)

const filter = reactive({
  recipeName: '',
  categoryId: '',
  difficulty: '',
  status: '',
})

const difficultyOptions = ['入门', '简单', '普通', '困难', '大师']
const statusOptions = ['上架', '下架']

const hasData = computed(() => list.value.length > 0)
const currentCategoryName = computed(() => {
  if (!filter.categoryId) return '全部菜谱'
  return categories.value.find((item) => item.id === filter.categoryId)?.categoryName || '全部菜谱'
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
})

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
      chooseRandomRecipe()
    }
  } catch (error) {
    showFailToast(error.message || '菜谱加载失败')
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

function applyFilter() {
  query.recipeName = filter.recipeName.trim()
  query.categoryId = filter.categoryId
  query.difficulty = filter.difficulty
  query.status = filter.status
  refreshList()
}

function resetFilter() {
  filter.recipeName = ''
  filter.categoryId = ''
  filter.difficulty = ''
  filter.status = ''
  applyFilter()
}

function pickCategory(categoryId = '') {
  filter.categoryId = categoryId
  applyFilter()
}

function chooseRandomRecipe() {
  if (list.value.length === 0) {
    suggestRecipe.value = null
    return
  }
  const randomIndex = Math.floor(Math.random() * list.value.length)
  suggestRecipe.value = list.value[randomIndex]
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

async function removeRecipe(item) {
  try {
    await showConfirmDialog({
      title: '提示',
      message: `确认删除菜谱「${item.recipeName}」吗？`,
    })
    await deleteRecipe(item.id)
    showSuccessToast('删除成功')
    await refreshList()
  } catch (error) {
    if (error?.message) {
      showFailToast(error.message)
    }
  }
}
</script>

<template>
  <div class="list-layout">
    <aside class="left card-panel">
      <div class="left-title">全部菜谱</div>
      <div class="left-sub">菜单分类</div>
      <div class="category-menu">
        <button
          class="category-item"
          :class="{ active: !filter.categoryId }"
          @click="pickCategory('')"
        >
          全部
        </button>
        <button
          v-for="item in categories"
          :key="item.id"
          class="category-item"
          :class="{ active: filter.categoryId === item.id }"
          @click="pickCategory(item.id)"
        >
          {{ item.categoryName }}
        </button>
      </div>
      <div class="recommend card-panel">
        <div class="recommend-title">今天吃什么</div>
        <template v-if="suggestRecipe">
          <img
            class="recommend-cover"
            :src="suggestRecipe.coverImage || 'https://via.placeholder.com/400x260?text=No+Image'"
            :alt="suggestRecipe.recipeName"
            @click="openDetail(suggestRecipe)"
          />
          <div class="recommend-name" @click="openDetail(suggestRecipe)">
            {{ suggestRecipe.recipeName }}
          </div>
        </template>
        <div v-else class="recommend-empty">暂无推荐</div>
        <van-button round size="small" type="warning" @click="chooseRandomRecipe">
          随机推荐
        </van-button>
      </div>
    </aside>

    <section class="card-panel section">
      <div class="toolbar">
        <div>
          <div class="toolbar-title">{{ currentCategoryName }}</div>
          <div class="toolbar-sub">{{ total }} 道菜，家的味道</div>
        </div>
      </div>

      <div v-if="saveMessage" class="save-banner">
        <van-icon name="success" />
        <span>{{ saveMessage }}，已回到首页</span>
      </div>

      <div class="filters">
        <van-field v-model="filter.recipeName" clearable placeholder="搜索菜谱名" />

        <select v-model="filter.categoryId" class="select-input">
          <option value="">全部分类</option>
          <option v-for="item in categories" :key="item.id" :value="item.id">
            {{ item.categoryName }}
          </option>
        </select>

        <select v-model="filter.difficulty" class="select-input">
          <option value="">全部难度</option>
          <option v-for="item in difficultyOptions" :key="item" :value="item">
            {{ item }}
          </option>
        </select>

        <select v-model="filter.status" class="select-input">
          <option value="">全部状态</option>
          <option v-for="item in statusOptions" :key="item" :value="item">
            {{ item }}
          </option>
        </select>

        <div class="filter-actions">
          <van-button type="warning" size="small" @click="applyFilter">筛选</van-button>
          <van-button plain size="small" @click="resetFilter">重置</van-button>
        </div>
      </div>

      <van-list
        :loading="loading"
        :finished="finished"
        @load="loadMore"
      >
        <div class="cards" v-if="hasData">
          <article v-for="item in list" :key="item.id" class="recipe-card card-panel">
            <img
              class="cover"
              :src="item.coverImage || 'https://via.placeholder.com/400x260?text=No+Image'"
              :alt="item.recipeName"
              @click="openDetail(item)"
            />
            <div class="card-body">
              <div class="title-row">
                <div class="name" @click="openDetail(item)">{{ item.recipeName }}</div>
                <van-icon
                  :name="favoriteMap[item.id] ? 'like' : 'like-o'"
                  :color="favoriteMap[item.id] ? '#ef4444' : '#9ca3af'"
                  size="20"
                  @click="toggleFavorite(item)"
                />
              </div>
              <div class="meta">
                <van-tag plain type="warning">{{ item.categoryName || '未分类' }}</van-tag>
                <span>{{ item.difficulty || '普通' }}</span>
                <span>{{ item.cookingTime || '-' }}</span>
              </div>
              <div class="ops">
                <van-button size="mini" plain type="primary" @click="openDetail(item)">
                  详情
                </van-button>
                <van-button size="mini" plain type="warning" @click="router.push(`/recipe/${item.id}/edit`)">
                  编辑
                </van-button>
                <van-button size="mini" plain type="danger" @click="removeRecipe(item)">
                  删除
                </van-button>
              </div>
            </div>
          </article>
        </div>
        <van-empty v-else description="暂无菜谱数据" class="empty-box" />
      </van-list>
    </section>
  </div>
</template>

<style scoped>
.list-layout {
  display: grid;
  grid-template-columns: 220px 1fr;
  gap: 16px;
}

.left {
  padding: 12px;
  align-self: start;
}

.left-title {
  font-size: 14px;
  font-weight: 700;
  margin-bottom: 4px;
}

.left-sub {
  font-size: 12px;
  color: #9ca3af;
  margin-bottom: 8px;
}

.category-menu {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.category-item {
  height: 34px;
  border: none;
  text-align: left;
  border-radius: 8px;
  background: #f7f8fa;
  color: #374151;
  padding: 0 12px;
  cursor: pointer;
}

.category-item.active {
  color: #f59e0b;
  background: #fff7e8;
  font-weight: 600;
}

.recommend {
  margin-top: 12px;
  border: 1px solid #f3f4f6;
  border-radius: 10px;
  padding: 10px;
}

.recommend-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 6px;
}

.recommend-cover {
  width: 100%;
  height: 120px;
  object-fit: cover;
  border-radius: 8px;
  cursor: pointer;
}

.recommend-name {
  margin: 6px 0 8px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.recommend-empty {
  color: #9ca3af;
  font-size: 12px;
  margin: 8px 0 10px;
}

.section {
  padding: 18px;
}

.toolbar {
  margin-bottom: 12px;
}

.toolbar-title {
  font-size: 24px;
  font-weight: 700;
}

.toolbar-sub {
  margin-top: 2px;
  color: #9ca3af;
  font-size: 13px;
}

.save-banner {
  margin-bottom: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  background: #ecfdf3;
  color: #15803d;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
}

.filters {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 16px;
}

.select-input {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 0 10px;
  height: 44px;
  font-size: 14px;
  background: #fff;
}

.filter-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.cards {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.recipe-card {
  overflow: hidden;
}

.cover {
  width: 100%;
  height: 170px;
  object-fit: cover;
  cursor: pointer;
}

.card-body {
  padding: 10px;
}

.title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.name {
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
}

.meta {
  margin-top: 8px;
  color: #6b7280;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.ops {
  margin-top: 10px;
  display: flex;
  gap: 8px;
}

@media (max-width: 1180px) {
  .list-layout {
    grid-template-columns: 1fr;
  }

  .left {
    display: none;
  }
}

@media (max-width: 1024px) {
  .filters {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .cards {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .section {
    padding: 12px;
  }

  .toolbar-title {
    font-size: 20px;
  }

  .filters {
    grid-template-columns: 1fr;
  }

  .cards {
    grid-template-columns: 1fr;
  }

  .cover {
    height: 190px;
  }
}
</style>
