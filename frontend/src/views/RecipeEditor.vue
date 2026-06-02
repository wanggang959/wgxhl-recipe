<script setup>
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showFailToast, showSuccessToast } from 'vant'
import { pageCategory } from '../api/category'
import { uploadImage } from '../api/file'
import { pageIngredient } from '../api/ingredient'
import { createRecipe, getRecipeDetail, updateRecipe } from '../api/recipe'
import { transcodeImageToWebp } from '../utils/image'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const saving = ref(false)

const categories = ref([])
const ingredients = ref([])

const form = reactive({
  recipe: {
    id: '',
    recipeName: '',
    recipeDesc: '',
    coverImage: '',
    difficulty: '普通',
    cookingTime: '',
    servingCount: '',
    taste: '',
    status: '上架',
    categoryId: '',
    remark: '',
  },
  recipeStepList: [],
  ingredientList: [],
  imageList: [],
})

const difficultyOptions = ['入门', '简单', '普通', '困难', '大师']
const statusOptions = ['上架', '下架']
const imageTypeOptions = ['cover', 'ingredient', 'step', 'finish']
const servingCountOptions = ['1-2人', '2-3人', '3-4人', '4-5人', '5人以上']
const ingredientUnitOptions = ['克', '千克', '个', '根']
const isEdit = computed(() => Boolean(route.params.id))
const uploading = ref(false)
const cookingMinutes = ref('')

async function bootstrap() {
  loading.value = true
  try {
    const [categoryRes, ingredientRes] = await Promise.all([
      pageCategory({ current: 1, size: 200 }),
      pageIngredient({ current: 1, size: 500 }),
    ])
    categories.value = categoryRes.data.records || []
    ingredients.value = ingredientRes.data.records || []
    if (isEdit.value) {
      const detailRes = await getRecipeDetail(route.params.id)
      const detail = detailRes.data
      Object.assign(form.recipe, detail.recipe || {})
      cookingMinutes.value = parseCookingMinutes(form.recipe.cookingTime)
      form.recipeStepList = (detail.recipeStepList || []).map((item) => ({ ...item }))
      form.ingredientList = (detail.ingredientList || []).map((item) => ({ ...item }))
      form.imageList = (detail.imageList || []).map((item) => ({ ...item }))
    } else {
      cookingMinutes.value = ''
      addStep()
      addIngredientRel()
      addImage()
    }
  } catch (error) {
    showFailToast(error.message || '数据初始化失败')
  } finally {
    loading.value = false
  }
}

function addStep() {
  form.recipeStepList.push({
    stepNo: form.recipeStepList.length + 1,
    stepTitle: '',
    stepDesc: '',
    stepImage: '',
  })
}

function removeStep(index) {
  form.recipeStepList.splice(index, 1)
}

function addIngredientRel() {
  form.ingredientList.push({
    ingredientId: '',
    amount: '',
    unit: '克',
    sortNo: form.ingredientList.length + 1,
  })
}

function removeIngredientRel(index) {
  form.ingredientList.splice(index, 1)
}

function addImage() {
  form.imageList.push({
    imageType: 'finish',
    imageUrl: '',
    sortNo: form.imageList.length + 1,
    imageDesc: '',
  })
}

function removeImage(index) {
  form.imageList.splice(index, 1)
}

function getRawFile(fileWrapper) {
  return fileWrapper?.file || fileWrapper
}

async function doUpload(fileWrapper, folder) {
  const rawFile = getRawFile(fileWrapper)
  const file = await transcodeImageToWebp(rawFile)
  if (!file) {
    throw new Error('未获取到上传文件')
  }
  uploading.value = true
  try {
    const res = await uploadImage(file, folder)
    showSuccessToast('图片上传成功')
    return res.data
  } finally {
    uploading.value = false
  }
}

async function uploadCover(fileWrapper) {
  try {
    form.recipe.coverImage = await doUpload(fileWrapper, 'recipe/cover')
  } catch (error) {
    showFailToast(error.message || '封面上传失败')
  }
}

async function uploadStepImage(index, fileWrapper) {
  try {
    form.recipeStepList[index].stepImage = await doUpload(fileWrapper, 'recipe/step')
  } catch (error) {
    showFailToast(error.message || '步骤图上传失败')
  }
}

async function uploadRecipeImage(index, fileWrapper) {
  try {
    form.imageList[index].imageUrl = await doUpload(fileWrapper, 'recipe/gallery')
  } catch (error) {
    showFailToast(error.message || '图片上传失败')
  }
}

async function submit() {
  if (!form.recipe.recipeName) {
    showFailToast('请输入菜谱名称')
    return
  }
  saving.value = true
  try {
    const normalizedCookingTime = normalizeCookingTime(cookingMinutes.value)
    if (normalizedCookingTime && Number.parseInt(normalizedCookingTime, 10) <= 0) {
      showFailToast('耗时分钟数需大于0')
      saving.value = false
      return
    }

    const payload = {
      recipe: {
        ...form.recipe,
        cookingTime: normalizedCookingTime,
      },
      recipeStepList: form.recipeStepList.map((item, idx) => ({
        ...item,
        stepNo: Number(item.stepNo || idx + 1),
      })),
      ingredientList: form.ingredientList.map((item, idx) => ({
        ...item,
        sortNo: Number(item.sortNo || idx + 1),
        unit: item.unit || '克',
      })),
      imageList: form.imageList.map((item, idx) => ({
        ...item,
        sortNo: Number(item.sortNo || idx + 1),
      })),
    }
    if (isEdit.value) {
      await updateRecipe(payload)
      showSuccessToast('菜谱更新成功')
      router.push(`/recipe/${form.recipe.id}`)
    } else {
      const res = await createRecipe(payload)
      showSuccessToast('菜谱创建成功')
      router.push(`/recipe/${res.data.recipe.id}`)
    }
  } catch (error) {
    showFailToast(error.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function parseCookingMinutes(text) {
  if (!text) return ''
  const matched = String(text).match(/\d+/)
  return matched ? matched[0] : ''
}

function normalizeCookingTime(value) {
  if (value === null || value === undefined) return ''
  const cleaned = String(value).trim()
  if (!cleaned) return ''
  return `${cleaned}分钟`
}

bootstrap()
</script>

<template>
  <div class="page-wrap">
    <section class="card-panel page">
      <van-nav-bar :title="isEdit ? '编辑菜谱' : '新增菜谱'" left-arrow @click-left="router.back()" />

      <van-loading v-if="loading" size="24px" class="loading">加载中...</van-loading>
      <div v-else class="content">
        <h3>基础信息</h3>
        <van-field v-model="form.recipe.recipeName" class="form-field" label="菜名" placeholder="请输入菜名" required />
        <van-field v-model="form.recipe.recipeDesc" class="form-field" label="简介" type="textarea" rows="2" />
        <div class="upload-line">
          <div class="upload-label">封面图</div>
          <div class="upload-area">
            <van-image
              v-if="form.recipe.coverImage"
              width="100"
              height="100"
              fit="cover"
              radius="8"
              :src="form.recipe.coverImage"
            />
            <van-uploader
              :after-read="uploadCover"
              accept="image/*"
              :disabled="uploading"
              :max-count="1"
            />
          </div>
        </div>
        <van-field v-model="form.recipe.taste" class="form-field" label="口味" placeholder="如：微辣" />
        <van-field
          v-model="cookingMinutes"
          class="form-field"
          label="耗时(分钟)"
          type="number"
          placeholder="如：30"
        />
        <label class="form-label">
          <span>人数</span>
          <select v-model="form.recipe.servingCount" class="select-input">
            <option value="">请选择人数</option>
            <option v-for="item in servingCountOptions" :key="item" :value="item">
              {{ item }}
            </option>
          </select>
        </label>
        <van-field v-model="form.recipe.remark" class="form-field" label="小贴士" type="textarea" rows="2" />

        <div class="select-grid">
          <label class="form-label">
            <span>分类</span>
            <select v-model="form.recipe.categoryId" class="select-input">
              <option value="">请选择分类</option>
              <option v-for="item in categories" :key="item.id" :value="item.id">
                {{ item.categoryName }}
              </option>
            </select>
          </label>
          <label class="form-label">
            <span>难度</span>
            <select v-model="form.recipe.difficulty" class="select-input">
              <option v-for="item in difficultyOptions" :key="item" :value="item">
                {{ item }}
              </option>
            </select>
          </label>
          <label class="form-label">
            <span>状态</span>
            <select v-model="form.recipe.status" class="select-input">
              <option v-for="item in statusOptions" :key="item" :value="item">
                {{ item }}
              </option>
            </select>
          </label>
        </div>

        <h3>食材</h3>
        <div v-for="(item, index) in form.ingredientList" :key="`ing-${index}`" class="row-panel">
          <div class="row-grid">
            <label class="form-label">
              <span>食材</span>
              <select v-model="item.ingredientId" class="select-input">
                <option value="">请选择食材</option>
                <option v-for="ing in ingredients" :key="ing.id" :value="ing.id">
                  {{ ing.ingredientName }}
                </option>
              </select>
            </label>
            <van-field v-model="item.amount" class="form-field" label="用量" placeholder="如：200" />
            <label class="form-label">
              <span>单位</span>
              <select v-model="item.unit" class="select-input">
                <option value="">请选择单位</option>
                <option v-for="unit in ingredientUnitOptions" :key="unit" :value="unit">
                  {{ unit }}
                </option>
              </select>
            </label>
          </div>
          <div class="row-op">
            <van-button size="mini" plain type="danger" @click="removeIngredientRel(index)">
              删除
            </van-button>
          </div>
        </div>
        <van-button plain size="small" type="primary" @click="addIngredientRel">+ 添加食材</van-button>

        <h3>步骤</h3>
        <div v-for="(item, index) in form.recipeStepList" :key="`step-${index}`" class="row-panel">
          <div class="row-grid">
            <van-field v-model.number="item.stepNo" class="form-field" label="序号" type="number" />
            <van-field v-model="item.stepTitle" class="form-field" label="标题" placeholder="步骤标题" />
            <div class="upload-cell">
              <span class="upload-cell-label">图片</span>
              <van-uploader
                :after-read="(file) => uploadStepImage(index, file)"
                accept="image/*"
                :disabled="uploading"
                :max-count="1"
              />
            </div>
          </div>
          <div class="upload-inline">
            <van-image
              v-if="item.stepImage"
              width="90"
              height="90"
              fit="cover"
              radius="8"
              :src="item.stepImage"
            />
          </div>
          <van-field v-model="item.stepDesc" class="form-field" label="说明" type="textarea" rows="2" />
          <div class="row-op">
            <van-button size="mini" plain type="danger" @click="removeStep(index)">删除</van-button>
          </div>
        </div>
        <van-button plain size="small" type="primary" @click="addStep">+ 添加步骤</van-button>

        <h3>图片</h3>
        <div v-for="(item, index) in form.imageList" :key="`image-${index}`" class="row-panel">
          <div class="row-grid">
            <label class="form-label">
              <span>类型</span>
              <select v-model="item.imageType" class="select-input">
                <option v-for="type in imageTypeOptions" :key="type" :value="type">
                  {{ type }}
                </option>
              </select>
            </label>
            <van-field v-model.number="item.sortNo" class="form-field" label="排序" type="number" />
            <div class="upload-cell">
              <span class="upload-cell-label">图片</span>
              <van-uploader
                :after-read="(file) => uploadRecipeImage(index, file)"
                accept="image/*"
                :disabled="uploading"
                :max-count="1"
              />
            </div>
          </div>
          <div class="upload-inline">
            <van-image
              v-if="item.imageUrl"
              width="90"
              height="90"
              fit="cover"
              radius="8"
              :src="item.imageUrl"
            />
          </div>
          <van-field v-model="item.imageDesc" class="form-field" label="描述" type="textarea" rows="2" />
          <div class="row-op">
            <van-button size="mini" plain type="danger" @click="removeImage(index)">删除</van-button>
          </div>
        </div>
        <van-button plain size="small" type="primary" @click="addImage">+ 添加图片</van-button>

        <div class="submit">
          <van-button type="warning" block :loading="saving" @click="submit">
            {{ isEdit ? '保存修改' : '创建菜谱' }}
          </van-button>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.page {
  padding-bottom: 20px;
}

.loading {
  margin-top: 24px;
}

.content {
  padding: 14px;
}

h3 {
  margin: 18px 0 10px;
}

h3:first-child {
  margin-top: 0;
}

.select-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin: 10px 0;
}

.form-label span {
  display: block;
  margin-bottom: 6px;
  font-size: 13px;
  color: #6b7280;
}

.select-input {
  width: 100%;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  height: 42px;
  padding: 0 10px;
  background: #fafafa;
}

:deep(.form-field) {
  border: 1px solid #d1d5db;
  border-radius: 8px;
  background: #fafafa;
  margin-bottom: 8px;
}

:deep(.form-field .van-field__label) {
  color: #4b5563;
  font-weight: 500;
}

:deep(.form-field .van-field__value input),
:deep(.form-field .van-field__value textarea) {
  color: #111827;
}

.row-panel {
  border: 1px solid #f0f0f0;
  border-radius: 10px;
  padding: 10px;
  margin-bottom: 10px;
}

.upload-line {
  margin: 10px 0;
}

.upload-label {
  margin-bottom: 6px;
  font-size: 13px;
  color: #6b7280;
}

.upload-area {
  display: flex;
  align-items: center;
  gap: 12px;
}

.upload-inline {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.upload-cell {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6px;
}

.upload-cell-label {
  font-size: 13px;
  color: #6b7280;
}

.row-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.row-op {
  margin-top: 8px;
  display: flex;
  justify-content: flex-end;
}

.submit {
  margin-top: 20px;
}

@media (max-width: 768px) {
  .select-grid,
  .row-grid {
    grid-template-columns: 1fr;
  }
}
</style>
