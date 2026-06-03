<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showFailToast } from 'vant'
import { createCategory, deleteCategory, pageCategory, updateCategory } from '../api/category'
import { uploadImage } from '../api/file'
import { createIngredient, deleteIngredient, pageIngredient, updateIngredient } from '../api/ingredient'
import { createSeasoning, deleteSeasoning, pageSeasoning, updateSeasoning } from '../api/seasoning'
import BottomNav from '../components/BottomNav.vue'
import ImageCropper from '../components/ImageCropper.vue'
import { useUserStore } from '../stores/user'
import { transcodeImageToWebp } from '../utils/image'
import { getImageUrl, toObjectPath } from '../utils/imageUrl'

const router = useRouter()
const userStore = useUserStore()
const active = ref('category')
const uploading = ref(false)
const imageCropperRef = ref(null)
const uploadStatus = ref('')
const actionStatus = ref('')
let uploadStatusTimer = null
let actionStatusTimer = null

const categoryList = ref([])
const ingredientList = ref([])
const seasoningList = ref([])

const categoryForm = reactive({
  id: '',
  categoryName: '',
  categoryCode: '',
  sortNo: 0,
  categoryDesc: '',
})

const ingredientForm = reactive({
  id: '',
  ingredientName: '',
  ingredientImage: '',
  ingredientDesc: '',
})

const seasoningForm = reactive({
  id: '',
  seasoningName: '',
  seasoningImage: '',
  seasoningDesc: '',
})

function resetCategoryForm() {
  categoryForm.id = ''
  categoryForm.categoryName = ''
  categoryForm.categoryCode = ''
  categoryForm.sortNo = 0
  categoryForm.categoryDesc = ''
}

function resetIngredientForm() {
  ingredientForm.id = ''
  ingredientForm.ingredientName = ''
  ingredientForm.ingredientImage = ''
  ingredientForm.ingredientDesc = ''
}

function resetSeasoningForm() {
  seasoningForm.id = ''
  seasoningForm.seasoningName = ''
  seasoningForm.seasoningImage = ''
  seasoningForm.seasoningDesc = ''
}

function editCategory(item) {
  Object.assign(categoryForm, item)
}

function editIngredient(item) {
  Object.assign(ingredientForm, item)
}

function editSeasoning(item) {
  Object.assign(seasoningForm, item)
}

function getRawFile(fileWrapper) {
  return fileWrapper?.file || fileWrapper
}

async function uploadIngredientImage(fileWrapper) {
  const rawFile = getRawFile(fileWrapper)
  if (!rawFile) {
    showFailToast('未获取到图片文件')
    return
  }
  const croppedFile = await imageCropperRef.value?.open(rawFile, { aspectRatio: 1 })
  if (!croppedFile) {
    return
  }

  uploading.value = true
  uploadStatus.value = '图片上传中...'
  try {
    const file = await transcodeImageToWebp(croppedFile, { minCompressBytes: 0 })
    const res = await uploadImage(file, 'ingredient')
    ingredientForm.ingredientImage = toObjectPath(res.data)
    showUploadStatus('图片上传成功')
  } catch (error) {
    showFailToast(error.message || '图片上传失败')
  } finally {
    uploading.value = false
  }
}

async function uploadSeasoningImage(fileWrapper) {
  const rawFile = getRawFile(fileWrapper)
  if (!rawFile) {
    showFailToast('未获取到图片文件')
    return
  }
  const croppedFile = await imageCropperRef.value?.open(rawFile, { aspectRatio: 1 })
  if (!croppedFile) {
    return
  }

  uploading.value = true
  uploadStatus.value = '图片上传中...'
  try {
    const file = await transcodeImageToWebp(croppedFile, { minCompressBytes: 0 })
    const res = await uploadImage(file, 'seasoning')
    seasoningForm.seasoningImage = toObjectPath(res.data)
    showUploadStatus('图片上传成功')
  } catch (error) {
    showFailToast(error.message || '图片上传失败')
  } finally {
    uploading.value = false
  }
}

function showUploadStatus(message) {
  uploadStatus.value = message
  window.clearTimeout(uploadStatusTimer)
  uploadStatusTimer = window.setTimeout(() => {
    if (!uploading.value) {
      uploadStatus.value = ''
    }
  }, 2200)
}

function showActionStatus(message) {
  actionStatus.value = message
  window.clearTimeout(actionStatusTimer)
  actionStatusTimer = window.setTimeout(() => {
    actionStatus.value = ''
  }, 2000)
}

async function loadCategory() {
  const res = await pageCategory({ current: 1, size: 200 })
  categoryList.value = res.data.records || []
}

async function loadIngredient() {
  const res = await pageIngredient({ current: 1, size: 200 })
  ingredientList.value = res.data.records || []
}

async function loadSeasoning() {
  const res = await pageSeasoning({ current: 1, size: 200 })
  seasoningList.value = res.data.records || []
}

async function saveCategory() {
  try {
    if (categoryForm.id) {
      await updateCategory({ ...categoryForm })
      showActionStatus('分类已更新')
    } else {
      await createCategory({ ...categoryForm })
      showActionStatus('分类已新增')
    }
    resetCategoryForm()
    await loadCategory()
  } catch (error) {
    showFailToast(error.message || '分类保存失败')
  }
}

async function saveIngredient() {
  try {
    if (ingredientForm.id) {
      await updateIngredient({ ...ingredientForm })
      showActionStatus('食材已更新')
    } else {
      await createIngredient({ ...ingredientForm })
      showActionStatus('食材已新增')
    }
    resetIngredientForm()
    await loadIngredient()
  } catch (error) {
    showFailToast(error.message || '食材保存失败')
  }
}

async function saveSeasoning() {
  try {
    if (seasoningForm.id) {
      await updateSeasoning({ ...seasoningForm })
      showActionStatus('调料已更新')
    } else {
      await createSeasoning({ ...seasoningForm })
      showActionStatus('调料已新增')
    }
    resetSeasoningForm()
    await loadSeasoning()
  } catch (error) {
    showFailToast(error.message || '调料保存失败')
  }
}

async function removeCategory(item) {
  try {
    await showConfirmDialog({
      title: '删除分类',
      message: `确认删除「${item.categoryName}」吗？`,
    })
    await deleteCategory(item.id)
    showActionStatus('分类已删除')
    await loadCategory()
  } catch (error) {
    if (error?.message) showFailToast(error.message)
  }
}

async function removeIngredient(item) {
  try {
    await showConfirmDialog({
      title: '删除食材',
      message: `确认删除「${item.ingredientName}」吗？`,
    })
    await deleteIngredient(item.id)
    showActionStatus('食材已删除')
    await loadIngredient()
  } catch (error) {
    if (error?.message) showFailToast(error.message)
  }
}

async function removeSeasoning(item) {
  try {
    await showConfirmDialog({
      title: '删除调料',
      message: `确认删除「${item.seasoningName}」吗？`,
    })
    await deleteSeasoning(item.id)
    showActionStatus('调料已删除')
    await loadSeasoning()
  } catch (error) {
    if (error?.message) showFailToast(error.message)
  }
}

if (userStore.isAdmin) {
  loadCategory().catch((e) => showFailToast(e.message || '分类加载失败'))
  loadIngredient().catch((e) => showFailToast(e.message || '食材加载失败'))
  loadSeasoning().catch((e) => showFailToast(e.message || '调料加载失败'))
}
</script>

<template>
  <div class="app-shell">
    <ImageCropper ref="imageCropperRef" />
    <main class="page-wrap">
    <section class="page">
      <van-nav-bar title="基础数据管理" left-arrow @click-left="router.back()" />
      <div v-if="!userStore.isAdmin" class="permission-card">
        <van-icon name="warning-o" size="24" />
        <strong>需要管理员权限</strong>
        <p>分类、食材、调料管理只能由管理员操作。</p>
        <van-button round type="warning" size="small" @click="router.push('/profile')">去登录</van-button>
      </div>
      <template v-else>
      <div v-if="uploadStatus" class="upload-status" :class="{ success: !uploading }">
        <van-loading v-if="uploading" size="16" color="#f59e0b" />
        <van-icon v-else name="success" />
        <span>{{ uploadStatus }}</span>
      </div>
      <div v-if="actionStatus" class="action-status">
        <van-icon name="success" />
        <span>{{ actionStatus }}</span>
      </div>
      <van-tabs v-model:active="active">
        <van-tab title="分类管理" name="category">
          <div class="form-panel">
            <van-field v-model="categoryForm.categoryName" label="分类名" placeholder="如：家常菜" />
            <van-field v-model="categoryForm.categoryCode" label="分类编码" placeholder="如：home" />
            <van-field v-model.number="categoryForm.sortNo" label="排序号" type="number" />
            <van-field v-model="categoryForm.categoryDesc" label="描述" type="textarea" rows="2" />
            <div class="form-actions">
              <van-button type="warning" size="small" @click="saveCategory">保存</van-button>
              <van-button size="small" @click="resetCategoryForm">清空</van-button>
            </div>
          </div>
          <van-cell-group inset>
            <van-cell
              v-for="item in categoryList"
              :key="item.id"
              :title="item.categoryName"
              :label="`${item.categoryCode} / 排序:${item.sortNo ?? '-'}`"
            >
              <template #right-icon>
                <div class="cell-actions">
                  <van-button size="mini" plain type="primary" @click="editCategory(item)">编辑</van-button>
                  <van-button size="mini" plain type="danger" @click="removeCategory(item)">删除</van-button>
                </div>
              </template>
            </van-cell>
          </van-cell-group>
        </van-tab>
        <van-tab title="食材管理" name="ingredient">
          <div class="form-panel">
            <van-field v-model="ingredientForm.ingredientName" label="食材名" placeholder="如：番茄" />
            <div class="upload-line">
              <div class="upload-label">食材图片</div>
              <div class="upload-area">
                <van-image
                  v-if="ingredientForm.ingredientImage"
                  width="90"
                  height="90"
                  fit="cover"
                  radius="8"
                  :src="getImageUrl(ingredientForm.ingredientImage)"
                />
                <van-uploader
                  :after-read="uploadIngredientImage"
                  accept="image/*"
                  :disabled="uploading"
                  :max-count="1"
                />
              </div>
            </div>
            <van-field v-model="ingredientForm.ingredientDesc" label="描述" type="textarea" rows="2" />
            <div class="form-actions">
              <van-button type="warning" size="small" @click="saveIngredient">保存</van-button>
              <van-button size="small" @click="resetIngredientForm">清空</van-button>
            </div>
          </div>
          <div class="ingredient-list">
            <article v-for="item in ingredientList" :key="item.id" class="ingredient-card">
              <img
                v-if="item.ingredientImage"
                class="ingredient-image"
                :src="getImageUrl(item.ingredientImage)"
                :alt="item.ingredientName"
              />
              <div v-else class="ingredient-image placeholder">
                <van-icon name="photo-o" size="20" />
              </div>
              <div class="ingredient-info">
                <div class="ingredient-name">{{ item.ingredientName }}</div>
                <div class="ingredient-desc">{{ item.ingredientDesc || '暂无描述' }}</div>
              </div>
              <div class="cell-actions">
                <van-button size="mini" plain type="primary" @click="editIngredient(item)">编辑</van-button>
                <van-button size="mini" plain type="danger" @click="removeIngredient(item)">删除</van-button>
              </div>
            </article>
          </div>
        </van-tab>
        <van-tab title="调料管理" name="seasoning">
          <div class="form-panel">
            <van-field v-model="seasoningForm.seasoningName" label="调料名" placeholder="如：生抽" />
            <div class="upload-line">
              <div class="upload-label">调料图片</div>
              <div class="upload-area">
                <van-image
                  v-if="seasoningForm.seasoningImage"
                  width="90"
                  height="90"
                  fit="cover"
                  radius="8"
                  :src="getImageUrl(seasoningForm.seasoningImage)"
                />
                <van-uploader
                  :after-read="uploadSeasoningImage"
                  accept="image/*"
                  :disabled="uploading"
                  :max-count="1"
                />
              </div>
            </div>
            <van-field v-model="seasoningForm.seasoningDesc" label="描述" type="textarea" rows="2" />
            <div class="form-actions">
              <van-button type="warning" size="small" @click="saveSeasoning">保存</van-button>
              <van-button size="small" @click="resetSeasoningForm">清空</van-button>
            </div>
          </div>
          <div class="ingredient-list">
            <article v-for="item in seasoningList" :key="item.id" class="ingredient-card">
              <img
                v-if="item.seasoningImage"
                class="ingredient-image"
                :src="getImageUrl(item.seasoningImage)"
                :alt="item.seasoningName"
              />
              <div v-else class="ingredient-image placeholder">
                <van-icon name="photo-o" size="20" />
              </div>
              <div class="ingredient-info">
                <div class="ingredient-name">{{ item.seasoningName }}</div>
                <div class="ingredient-desc">{{ item.seasoningDesc || '暂无描述' }}</div>
              </div>
              <div class="cell-actions">
                <van-button size="mini" plain type="primary" @click="editSeasoning(item)">编辑</van-button>
                <van-button size="mini" plain type="danger" @click="removeSeasoning(item)">删除</van-button>
              </div>
            </article>
          </div>
        </van-tab>
      </van-tabs>
      </template>
    </section>
    </main>
    <BottomNav />
  </div>
</template>

<style scoped>
.page {
  padding-bottom: 88px;
  border-radius: 20px;
  background: #fff;
  border: 1px solid var(--app-border);
  box-shadow: 0 10px 24px rgba(154, 52, 18, 0.06);
}

.form-panel {
  padding: 12px;
}

.permission-card {
  margin: 16px;
  min-height: 220px;
  border-radius: 18px;
  background: #fff7ed;
  color: #7c5c46;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  text-align: center;
}

.permission-card strong {
  color: var(--app-text);
  font-size: 18px;
}

.permission-card p {
  margin: 0;
  max-width: 260px;
  line-height: 1.6;
}

.upload-status {
  margin: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  background: #fff7e8;
  color: #b45309;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

.upload-status.success {
  background: #ecfdf3;
  color: #15803d;
}

.action-status {
  margin: 12px;
  padding: 10px 12px;
  border-radius: 12px;
  background: #ecfdf3;
  color: #15803d;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

.form-actions {
  margin-top: 10px;
  display: flex;
  gap: 8px;
}

.cell-actions {
  display: flex;
  gap: 6px;
}

.ingredient-list {
  padding: 0 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ingredient-card {
  min-width: 0;
  padding: 10px;
  border-radius: 14px;
  background: #fffaf2;
  border: 1px solid var(--app-border);
  display: grid;
  grid-template-columns: 56px 1fr auto;
  align-items: center;
  gap: 10px;
}

.ingredient-image {
  width: 56px;
  height: 56px;
  border-radius: 13px;
  object-fit: cover;
  background: var(--app-primary-soft);
}

.ingredient-image.placeholder {
  color: var(--app-primary);
  display: grid;
  place-items: center;
}

.ingredient-info {
  min-width: 0;
}

.ingredient-name {
  color: var(--app-text);
  font-size: 15px;
  font-weight: 700;
  overflow-wrap: anywhere;
}

.ingredient-desc {
  margin-top: 4px;
  color: var(--app-muted);
  font-size: 12px;
  line-height: 1.35;
  overflow-wrap: anywhere;
}

.upload-line {
  margin: 8px 0 12px;
}

.upload-label {
  font-size: 13px;
  color: #6b7280;
  margin-bottom: 6px;
}

.upload-area {
  display: flex;
  align-items: center;
  gap: 12px;
}

@media (max-width: 380px) {
  .ingredient-card {
    grid-template-columns: 52px 1fr;
  }

  .ingredient-card .cell-actions {
    grid-column: 2;
    justify-content: flex-start;
  }
}
</style>
