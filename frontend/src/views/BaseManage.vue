<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showFailToast, showSuccessToast } from 'vant'
import { createCategory, deleteCategory, pageCategory, updateCategory } from '../api/category'
import { uploadImage } from '../api/file'
import { createIngredient, deleteIngredient, pageIngredient, updateIngredient } from '../api/ingredient'
import ImageCropper from '../components/ImageCropper.vue'
import { transcodeImageToWebp } from '../utils/image'

const router = useRouter()
const active = ref('category')
const uploading = ref(false)
const imageCropperRef = ref(null)

const categoryList = ref([])
const ingredientList = ref([])

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

function editCategory(item) {
  Object.assign(categoryForm, item)
}

function editIngredient(item) {
  Object.assign(ingredientForm, item)
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
  try {
    const file = await transcodeImageToWebp(croppedFile, { minCompressBytes: 0 })
    const res = await uploadImage(file, 'ingredient')
    ingredientForm.ingredientImage = res.data
    showSuccessToast('图片上传成功')
  } catch (error) {
    showFailToast(error.message || '图片上传失败')
  } finally {
    uploading.value = false
  }
}

async function loadCategory() {
  const res = await pageCategory({ current: 1, size: 200 })
  categoryList.value = res.data.records || []
}

async function loadIngredient() {
  const res = await pageIngredient({ current: 1, size: 200 })
  ingredientList.value = res.data.records || []
}

async function saveCategory() {
  try {
    if (categoryForm.id) {
      await updateCategory({ ...categoryForm })
      showSuccessToast('分类已更新')
    } else {
      await createCategory({ ...categoryForm })
      showSuccessToast('分类已新增')
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
      showSuccessToast('食材已更新')
    } else {
      await createIngredient({ ...ingredientForm })
      showSuccessToast('食材已新增')
    }
    resetIngredientForm()
    await loadIngredient()
  } catch (error) {
    showFailToast(error.message || '食材保存失败')
  }
}

async function removeCategory(item) {
  try {
    await showConfirmDialog({
      title: '删除分类',
      message: `确认删除「${item.categoryName}」吗？`,
    })
    await deleteCategory(item.id)
    showSuccessToast('分类已删除')
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
    showSuccessToast('食材已删除')
    await loadIngredient()
  } catch (error) {
    if (error?.message) showFailToast(error.message)
  }
}

loadCategory().catch((e) => showFailToast(e.message || '分类加载失败'))
loadIngredient().catch((e) => showFailToast(e.message || '食材加载失败'))
</script>

<template>
  <div class="page-wrap">
    <ImageCropper ref="imageCropperRef" />
    <section class="card-panel page">
      <van-nav-bar title="基础数据管理" left-arrow @click-left="router.back()" />
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
                  :src="ingredientForm.ingredientImage"
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
          <van-cell-group inset>
            <van-cell
              v-for="item in ingredientList"
              :key="item.id"
              :title="item.ingredientName"
              :label="item.ingredientDesc || '暂无描述'"
            >
              <template #right-icon>
                <div class="cell-actions">
                  <van-button size="mini" plain type="primary" @click="editIngredient(item)">编辑</van-button>
                  <van-button size="mini" plain type="danger" @click="removeIngredient(item)">删除</van-button>
                </div>
              </template>
            </van-cell>
          </van-cell-group>
        </van-tab>
      </van-tabs>
    </section>
  </div>
</template>

<style scoped>
.page {
  padding-bottom: 20px;
}

.form-panel {
  padding: 12px;
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
</style>
