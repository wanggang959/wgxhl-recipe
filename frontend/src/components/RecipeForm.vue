<script setup>
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showFailToast, showSuccessToast } from 'vant'
import { pageCategory } from '../api/category'
import { uploadImage } from '../api/file'
import { createIngredient, pageIngredient } from '../api/ingredient'
import { createRecipe, getRecipeDetail, updateRecipe } from '../api/recipe'
import { createSeasoning, pageSeasoning } from '../api/seasoning'
import { transcodeImageToWebp } from '../utils/image'
import { getImageUrl, toObjectPath } from '../utils/imageUrl'
import { DEFAULT_RECIPE_VERSION, isValidRecipeVersion, normalizeRecipeVersion } from '../utils/recipeVersion'
import ImageCropper from './ImageCropper.vue'
import ImageUploader from './ImageUploader.vue'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const categories = ref([])
const ingredients = ref([])
const seasonings = ref([])
const uploading = ref(false)
const uploadingTarget = ref('')
const cookingMinutes = ref('')
const imageCropperRef = ref(null)
const showQuickIngredient = ref(false)
const showQuickSeasoning = ref(false)
const creatingBaseData = ref(false)
const basePickerVisible = ref(false)
const basePickerType = ref('ingredient')
const basePickerRow = ref(null)
const basePickerKeyword = ref('')
let stepUid = 0

const form = reactive({
  recipe: {
    id: '',
    recipeName: '',
    recipeVersion: DEFAULT_RECIPE_VERSION,
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
  seasoningList: [],
  imageList: [],
})

const quickIngredientForm = reactive({
  ingredientName: '',
  ingredientImage: '',
  ingredientDesc: '',
})

const quickSeasoningForm = reactive({
  seasoningName: '',
  seasoningImage: '',
  seasoningDesc: '',
})

const difficultyOptions = ['入门', '简单', '普通', '困难', '大师']
const servingCountOptions = ['1-2人', '2-3人', '3-4人', '4-5人', '5人以上']
const ingredientUnitOptions = ['克', '千克', '个', '根', '棵', '片', '勺', '适量']
const seasoningUnitOptions = ['克', '毫升', '勺', '小勺', '大勺', '适量', '少许']
const isEdit = computed(() => Boolean(route.params.id))
const basePickerTitle = computed(() => (basePickerType.value === 'ingredient' ? '选择食材' : '选择调料'))
const basePickerPlaceholder = computed(() =>
  basePickerType.value === 'ingredient' ? '搜索食材名称或描述' : '搜索调料名称或描述',
)
const basePickerOptions = computed(() => {
  const keyword = normalizeKeyword(basePickerKeyword.value)
  const list = basePickerType.value === 'ingredient' ? ingredients.value : seasonings.value
  if (!keyword) return list
  return list.filter((item) => {
    const text = normalizeKeyword(`${getBaseName(item)} ${getBaseDesc(item)}`)
    return text.includes(keyword)
  })
})

async function bootstrap() {
  loading.value = true
  try {
    const [categoryRes, ingredientRes, seasoningRes] = await Promise.all([
      pageCategory({ current: 1, size: 200 }),
      pageIngredient({ current: 1, size: 500 }),
      pageSeasoning({ current: 1, size: 500 }),
    ])
    categories.value = categoryRes.data.records || []
    ingredients.value = ingredientRes.data.records || []
    seasonings.value = seasoningRes.data.records || []
    if (isEdit.value) {
      const detailRes = await getRecipeDetail(route.params.id)
      const detail = detailRes.data
      Object.assign(form.recipe, detail.recipe || {})
      form.recipe.recipeVersion = normalizeRecipeVersion(form.recipe.recipeVersion)
      cookingMinutes.value = parseCookingMinutes(form.recipe.cookingTime)
      form.recipeStepList = (detail.recipeStepList || []).map((item) => ({ ...item, _uid: nextStepUid() }))
      form.ingredientList = (detail.ingredientList || []).map((item) => ({ ...item }))
      form.seasoningList = (detail.seasoningList || []).map((item) => ({ ...item }))
      form.imageList = (detail.imageList || []).map((item) => ({ ...item }))
    } else {
      cookingMinutes.value = ''
      addStep()
      addIngredientRel()
      addSeasoningRel()
    }
  } catch (error) {
    showFailToast(error.message || '数据初始化失败')
  } finally {
    loading.value = false
  }
}

function addStep() {
  form.recipeStepList.push({
    _uid: nextStepUid(),
    stepNo: form.recipeStepList.length + 1,
    stepTitle: '',
    stepDesc: '',
    stepImage: '',
  })
}

function nextStepUid() {
  stepUid += 1
  return `step-${Date.now()}-${stepUid}`
}

function removeStep(index) {
  form.recipeStepList.splice(index, 1)
  normalizeStepNo()
}

function moveStep(index, direction) {
  const targetIndex = index + direction
  if (targetIndex < 0 || targetIndex >= form.recipeStepList.length) return
  const [target] = form.recipeStepList.splice(index, 1)
  form.recipeStepList.splice(targetIndex, 0, target)
  normalizeStepNo()
}

function normalizeStepNo() {
  form.recipeStepList.forEach((item, idx) => {
    item.stepNo = idx + 1
  })
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

function addSeasoningRel() {
  form.seasoningList.push({
    seasoningId: '',
    amount: '',
    unit: '适量',
    sortNo: form.seasoningList.length + 1,
  })
}

function removeSeasoningRel(index) {
  form.seasoningList.splice(index, 1)
}

function getRawFile(fileWrapper) {
  return fileWrapper?.file || fileWrapper
}

async function doUpload(fileWrapper, folder, cropOptions = {}, uploadKey = '') {
  const rawFile = getRawFile(fileWrapper)
  const croppedFile = await imageCropperRef.value?.open(rawFile, cropOptions)
  if (!croppedFile) return ''

  const file = await transcodeImageToWebp(croppedFile, {
    maxLongEdge: 1600,
    quality: 0.84,
    minCompressBytes: 0,
  })
  if (!file) throw new Error('未获取到上传文件')

  uploading.value = true
  uploadingTarget.value = uploadKey
  try {
    const res = await uploadImage(file, folder)
    return toObjectPath(res.data)
  } finally {
    uploading.value = false
    uploadingTarget.value = ''
  }
}

async function uploadCover(fileWrapper) {
  try {
    const imageUrl = await doUpload(fileWrapper, 'recipe/cover', { aspectRatio: 4 / 3 }, 'cover')
    if (imageUrl) form.recipe.coverImage = imageUrl
  } catch (error) {
    showFailToast(error.message || '封面上传失败')
  }
}

async function uploadStepImage(index, fileWrapper) {
  try {
    const imageUrl = await doUpload(fileWrapper, 'recipe/step', { aspectRatio: 4 / 3 }, `step-${index}`)
    if (imageUrl) form.recipeStepList[index].stepImage = imageUrl
  } catch (error) {
    showFailToast(error.message || '步骤图上传失败')
  }
}

async function uploadQuickIngredientImage(fileWrapper) {
  try {
    const imageUrl = await doUpload(fileWrapper, 'ingredient', { aspectRatio: 1 }, 'quick-ingredient')
    if (imageUrl) quickIngredientForm.ingredientImage = imageUrl
  } catch (error) {
    showFailToast(error.message || '食材图片上传失败')
  }
}

async function uploadQuickSeasoningImage(fileWrapper) {
  try {
    const imageUrl = await doUpload(fileWrapper, 'seasoning', { aspectRatio: 1 }, 'quick-seasoning')
    if (imageUrl) quickSeasoningForm.seasoningImage = imageUrl
  } catch (error) {
    showFailToast(error.message || '调料图片上传失败')
  }
}

function resetQuickIngredientForm() {
  quickIngredientForm.ingredientName = ''
  quickIngredientForm.ingredientImage = ''
  quickIngredientForm.ingredientDesc = ''
}

function resetQuickSeasoningForm() {
  quickSeasoningForm.seasoningName = ''
  quickSeasoningForm.seasoningImage = ''
  quickSeasoningForm.seasoningDesc = ''
}

function selectCreatedIngredient(id) {
  let target = form.ingredientList.find((item) => !item.ingredientId)
  if (!target) {
    addIngredientRel()
    target = form.ingredientList[form.ingredientList.length - 1]
  }
  target.ingredientId = id
}

function selectCreatedSeasoning(id) {
  let target = form.seasoningList.find((item) => !item.seasoningId)
  if (!target) {
    addSeasoningRel()
    target = form.seasoningList[form.seasoningList.length - 1]
  }
  target.seasoningId = id
}

async function createQuickIngredient() {
  if (!quickIngredientForm.ingredientName.trim()) {
    showFailToast('请输入食材名')
    return
  }
  creatingBaseData.value = true
  try {
    const res = await createIngredient({
      ...quickIngredientForm,
      ingredientName: quickIngredientForm.ingredientName.trim(),
    })
    const created = res.data
    ingredients.value = [created, ...ingredients.value.filter((item) => item.id !== created.id)]
    selectCreatedIngredient(created.id)
    resetQuickIngredientForm()
    showQuickIngredient.value = false
    showSuccessToast('食材已添加')
  } catch (error) {
    showFailToast(error.message || '食材添加失败')
  } finally {
    creatingBaseData.value = false
  }
}

async function createQuickSeasoning() {
  if (!quickSeasoningForm.seasoningName.trim()) {
    showFailToast('请输入调料名')
    return
  }
  creatingBaseData.value = true
  try {
    const res = await createSeasoning({
      ...quickSeasoningForm,
      seasoningName: quickSeasoningForm.seasoningName.trim(),
    })
    const created = res.data
    seasonings.value = [created, ...seasonings.value.filter((item) => item.id !== created.id)]
    selectCreatedSeasoning(created.id)
    resetQuickSeasoningForm()
    showQuickSeasoning.value = false
    showSuccessToast('调料已添加')
  } catch (error) {
    showFailToast(error.message || '调料添加失败')
  } finally {
    creatingBaseData.value = false
  }
}

function normalizeKeyword(text) {
  return String(text || '').trim().toLowerCase()
}

function getBaseName(item, type = basePickerType.value) {
  return type === 'ingredient' ? item?.ingredientName || '' : item?.seasoningName || ''
}

function getBaseImage(item, type = basePickerType.value) {
  return type === 'ingredient' ? item?.ingredientImage || '' : item?.seasoningImage || ''
}

function getBaseDesc(item, type = basePickerType.value) {
  return type === 'ingredient' ? item?.ingredientDesc || '' : item?.seasoningDesc || ''
}

function findIngredient(id) {
  return ingredients.value.find((item) => item.id === id)
}

function findSeasoning(id) {
  return seasonings.value.find((item) => item.id === id)
}

function getSelectedIngredientName(row) {
  return getBaseName(findIngredient(row.ingredientId), 'ingredient') || row.ingredientName || ''
}

function getSelectedIngredientImage(row) {
  return getBaseImage(findIngredient(row.ingredientId), 'ingredient') || row.ingredientImage || ''
}

function getSelectedSeasoningName(row) {
  return getBaseName(findSeasoning(row.seasoningId), 'seasoning') || row.seasoningName || ''
}

function getSelectedSeasoningImage(row) {
  return getBaseImage(findSeasoning(row.seasoningId), 'seasoning') || row.seasoningImage || ''
}

function openBasePicker(type, row) {
  basePickerType.value = type
  basePickerRow.value = row
  basePickerKeyword.value = ''
  basePickerVisible.value = true
}

function selectBaseOption(option) {
  if (!basePickerRow.value) return
  if (basePickerType.value === 'ingredient') {
    basePickerRow.value.ingredientId = option.id
    basePickerRow.value.ingredientName = option.ingredientName
    basePickerRow.value.ingredientImage = option.ingredientImage
  } else {
    basePickerRow.value.seasoningId = option.id
    basePickerRow.value.seasoningName = option.seasoningName
    basePickerRow.value.seasoningImage = option.seasoningImage
  }
  basePickerVisible.value = false
}

function isSelectedBaseOption(option) {
  if (!basePickerRow.value) return false
  const selectedId = basePickerType.value === 'ingredient'
    ? basePickerRow.value.ingredientId
    : basePickerRow.value.seasoningId
  return selectedId === option.id
}

async function submit() {
  if (!form.recipe.recipeName) {
    showFailToast('请输入菜名')
    return
  }
  form.recipe.recipeVersion = normalizeRecipeVersion(form.recipe.recipeVersion)
  if (!isValidRecipeVersion(form.recipe.recipeVersion)) {
    showFailToast('版本号请填写为 1.0、2.1 这类两位数字格式')
    return
  }
  if (saving.value) return

  saving.value = true
  try {
    const normalizedCookingTime = normalizeCookingTime(cookingMinutes.value)
    if (normalizedCookingTime && Number.parseInt(normalizedCookingTime, 10) <= 0) {
      showFailToast('用时分钟数需大于0')
      return
    }

    const payload = {
      recipe: {
        ...form.recipe,
        cookingTime: normalizedCookingTime,
      },
      recipeStepList: form.recipeStepList.map((item, idx) => {
        const { _uid, ...step } = item
        return {
          ...step,
          stepNo: idx + 1,
        }
      }),
      ingredientList: form.ingredientList.map((item, idx) => ({
        ...item,
        sortNo: Number(item.sortNo || idx + 1),
        unit: item.unit || '克',
      })),
      seasoningList: form.seasoningList.map((item, idx) => ({
        ...item,
        sortNo: Number(item.sortNo || idx + 1),
        unit: item.unit || '适量',
      })),
      imageList: form.imageList.map((item, idx) => ({
        ...item,
        sortNo: Number(item.sortNo || idx + 1),
      })),
    }
    let targetRecipeId = form.recipe.id || route.params.id
    if (isEdit.value) {
      await updateRecipe(payload)
      targetRecipeId = form.recipe.id || route.params.id
    } else {
      const res = await createRecipe(payload)
      targetRecipeId = res.data?.recipe?.id || res.data?.id || payload.recipe.id
    }
    if (targetRecipeId) {
      await router.replace(`/recipe/${targetRecipeId}`)
    } else {
      await router.replace('/recipes')
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
  <div class="recipe-form-page">
    <ImageCropper ref="imageCropperRef" />
    <van-nav-bar :title="isEdit ? '编辑菜谱' : '添加菜谱'" left-arrow @click-left="router.back()" />

    <van-loading v-if="loading" size="24px" class="loading">加载中...</van-loading>
    <form v-else class="recipe-form" @submit.prevent="submit">
      <section class="form-section">
        <h2>基础信息</h2>
        <van-field v-model="form.recipe.recipeName" class="form-field" label="菜名" placeholder="请输入菜名" required />
        <van-field
          v-model="form.recipe.recipeVersion"
          class="form-field"
          label="版本"
          placeholder="默认 1.0，如 2.0"
          maxlength="5"
        />
        <p class="field-hint">同一上传者下，菜名 + 版本 唯一（例如王师傅的「青椒肉丝 1.0」与「青椒肉丝 2.0」可并存）</p>
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
            <span>口味</span>
            <input v-model="form.recipe.taste" class="text-input" placeholder="如：微辣" />
          </label>
        </div>
        <div class="select-grid">
          <label class="form-label">
            <span>难度</span>
            <select v-model="form.recipe.difficulty" class="select-input">
              <option v-for="item in difficultyOptions" :key="item" :value="item">
                {{ item }}
              </option>
            </select>
          </label>
          <label class="form-label">
            <span>用时(分钟)</span>
            <input v-model="cookingMinutes" class="text-input" inputmode="numeric" placeholder="如：30" />
          </label>
        </div>
        <label class="form-label">
          <span>人数</span>
          <select v-model="form.recipe.servingCount" class="select-input">
            <option value="">请选择人数</option>
            <option v-for="item in servingCountOptions" :key="item" :value="item">
              {{ item }}
            </option>
          </select>
        </label>
        <ImageUploader
          v-model="form.recipe.coverImage"
          label="成品图"
          :disabled="uploading"
          :loading="uploadingTarget === 'cover'"
          @upload="uploadCover"
        />
      </section>

      <section class="form-section">
        <div class="section-head">
          <h2>食材列表</h2>
        </div>
        <article v-for="(item, index) in form.ingredientList" :key="`ing-${index}`" class="row-panel">
          <div class="row-title">
            <span>食材 {{ index + 1 }}</span>
            <button class="delete-row" type="button" @click="removeIngredientRel(index)">
              <van-icon name="delete-o" />
              删除
            </button>
          </div>
          <label class="form-label">
            <span>食材</span>
            <button type="button" class="base-select-trigger" @click="openBasePicker('ingredient', item)">
              <img
                v-if="getSelectedIngredientImage(item)"
                :src="getImageUrl(getSelectedIngredientImage(item))"
                :alt="getSelectedIngredientName(item)"
              />
              <span v-else class="base-select-placeholder">
                <van-icon name="photo-o" />
              </span>
              <strong :class="{ empty: !getSelectedIngredientName(item) }">
                {{ getSelectedIngredientName(item) || '请选择食材' }}
              </strong>
              <van-icon name="arrow" />
            </button>
          </label>
          <div class="select-grid">
            <label class="form-label">
              <span>用量</span>
              <input v-model="item.amount" class="text-input" placeholder="如：1" />
            </label>
            <label class="form-label">
              <span>单位</span>
              <select v-model="item.unit" class="select-input">
                <option v-for="unit in ingredientUnitOptions" :key="unit" :value="unit">
                  {{ unit }}
                </option>
              </select>
            </label>
          </div>
        </article>
        <div class="list-tail-actions">
          <button class="tail-action primary" type="button" @click="addIngredientRel">
            <van-icon name="plus" />
            添加食材
          </button>
          <button class="tail-action" type="button" @click="showQuickIngredient = !showQuickIngredient">
            <van-icon name="plus" />
            新建食材
          </button>
        </div>
        <div v-if="showQuickIngredient" class="quick-create">
          <van-field v-model="quickIngredientForm.ingredientName" class="form-field" label="食材名" placeholder="如：青椒" />
          <ImageUploader
            v-model="quickIngredientForm.ingredientImage"
            label="食材图片"
            :disabled="uploading"
            :loading="uploadingTarget === 'quick-ingredient'"
            :size="76"
            @upload="uploadQuickIngredientImage"
          />
          <van-field v-model="quickIngredientForm.ingredientDesc" class="form-field" label="描述" type="textarea" rows="2" />
          <div class="quick-actions">
            <van-button size="small" :disabled="creatingBaseData" @click="showQuickIngredient = false">取消</van-button>
            <van-button size="small" type="warning" :loading="creatingBaseData" @click="createQuickIngredient">保存并选中</van-button>
          </div>
        </div>
      </section>

      <section class="form-section">
        <div class="section-head">
          <h2>调料列表</h2>
        </div>
        <article v-for="(item, index) in form.seasoningList" :key="`seasoning-${index}`" class="row-panel">
          <div class="row-title">
            <span>调料 {{ index + 1 }}</span>
            <button class="delete-row" type="button" @click="removeSeasoningRel(index)">
              <van-icon name="delete-o" />
              删除
            </button>
          </div>
          <label class="form-label">
            <span>调料</span>
            <button type="button" class="base-select-trigger" @click="openBasePicker('seasoning', item)">
              <img
                v-if="getSelectedSeasoningImage(item)"
                :src="getImageUrl(getSelectedSeasoningImage(item))"
                :alt="getSelectedSeasoningName(item)"
              />
              <span v-else class="base-select-placeholder">
                <van-icon name="photo-o" />
              </span>
              <strong :class="{ empty: !getSelectedSeasoningName(item) }">
                {{ getSelectedSeasoningName(item) || '请选择调料' }}
              </strong>
              <van-icon name="arrow" />
            </button>
          </label>
          <div class="select-grid">
            <label class="form-label">
              <span>用量</span>
              <input v-model="item.amount" class="text-input" placeholder="如：200" />
            </label>
            <label class="form-label">
              <span>单位</span>
              <select v-model="item.unit" class="select-input">
                <option v-for="unit in seasoningUnitOptions" :key="unit" :value="unit">
                  {{ unit }}
                </option>
              </select>
            </label>
          </div>
        </article>
        <div class="list-tail-actions">
          <button class="tail-action primary" type="button" @click="addSeasoningRel">
            <van-icon name="plus" />
            添加调料
          </button>
          <button class="tail-action" type="button" @click="showQuickSeasoning = !showQuickSeasoning">
            <van-icon name="plus" />
            新建调料
          </button>
        </div>
        <div v-if="showQuickSeasoning" class="quick-create">
          <van-field v-model="quickSeasoningForm.seasoningName" class="form-field" label="调料名" placeholder="如：生抽" />
          <ImageUploader
            v-model="quickSeasoningForm.seasoningImage"
            label="调料图片"
            :disabled="uploading"
            :loading="uploadingTarget === 'quick-seasoning'"
            :size="76"
            @upload="uploadQuickSeasoningImage"
          />
          <van-field v-model="quickSeasoningForm.seasoningDesc" class="form-field" label="描述" type="textarea" rows="2" />
          <div class="quick-actions">
            <van-button size="small" :disabled="creatingBaseData" @click="showQuickSeasoning = false">取消</van-button>
            <van-button size="small" type="warning" :loading="creatingBaseData" @click="createQuickSeasoning">保存并选中</van-button>
          </div>
        </div>
      </section>

      <section class="form-section">
        <div class="section-head">
          <h2>制作步骤</h2>
        </div>
        <article v-for="(item, index) in form.recipeStepList" :key="item._uid || `step-${index}`" class="row-panel step-panel">
          <div class="step-head">
            <span>{{ index + 1 }}</span>
            <input v-model="item.stepTitle" class="step-title-input" placeholder="步骤标题" />
            <div class="step-move-actions">
              <button type="button" :disabled="uploading || index === 0" @click="moveStep(index, -1)">
                <van-icon name="arrow-up" />
              </button>
              <button type="button" :disabled="uploading || index === form.recipeStepList.length - 1" @click="moveStep(index, 1)">
                <van-icon name="arrow-down" />
              </button>
            </div>
          </div>
          <van-field v-model="item.stepDesc" class="form-field" type="textarea" rows="3" placeholder="写下这一步怎么做" />
          <ImageUploader
            v-model="item.stepImage"
            label="步骤图"
            :disabled="uploading"
            :loading="uploadingTarget === `step-${index}`"
            :size="86"
            @upload="(file) => uploadStepImage(index, file)"
          />
          <button class="delete-row" type="button" @click="removeStep(index)">
            <van-icon name="delete-o" />
            删除步骤
          </button>
        </article>
        <div class="list-tail-actions single">
          <button class="tail-action primary" type="button" @click="addStep">
            <van-icon name="plus" />
            添加步骤
          </button>
        </div>
      </section>

      <section class="form-section">
        <h2>小贴士</h2>
        <van-field v-model="form.recipe.remark" class="form-field" type="textarea" rows="3" placeholder="比如火候、替代食材、家里人的口味偏好" />
      </section>

      <div class="submit-spacer"></div>
      <div class="fixed-submit">
        <van-button
          type="warning"
          block
          round
          native-type="submit"
          :disabled="uploading"
          :loading="saving"
          loading-text="保存中..."
        >
          {{ isEdit ? '完成修改' : '保存菜谱' }}
        </van-button>
      </div>
    </form>

    <van-popup v-model:show="basePickerVisible" position="bottom" round>
      <div class="base-picker">
        <div class="base-picker-head">
          <h3>{{ basePickerTitle }}</h3>
          <button type="button" @click="basePickerVisible = false">
            <van-icon name="cross" />
          </button>
        </div>
        <label class="base-picker-search">
          <van-icon name="search" />
          <input v-model="basePickerKeyword" :placeholder="basePickerPlaceholder" />
        </label>
        <div class="base-option-list">
          <button
            v-for="option in basePickerOptions"
            :key="option.id"
            type="button"
            class="base-option"
            :class="{ active: isSelectedBaseOption(option) }"
            @click="selectBaseOption(option)"
          >
            <img v-if="getBaseImage(option)" :src="getImageUrl(getBaseImage(option))" :alt="getBaseName(option)" />
            <span v-else class="base-option-placeholder">
              <van-icon name="photo-o" />
            </span>
            <span class="base-option-main">
              <strong>{{ getBaseName(option) }}</strong>
              <em v-if="getBaseDesc(option)">{{ getBaseDesc(option) }}</em>
            </span>
            <van-icon v-if="isSelectedBaseOption(option)" name="success" />
          </button>
          <div v-if="basePickerOptions.length === 0" class="base-option-empty">
            没有匹配的{{ basePickerType === 'ingredient' ? '食材' : '调料' }}
          </div>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.recipe-form-page {
  min-height: 100vh;
}

.loading {
  margin-top: 30px;
}

.recipe-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.form-section,
.row-panel {
  padding: 14px;
  border-radius: 18px;
  background: #fff;
  border: 1px solid var(--app-border);
  box-shadow: 0 10px 24px rgba(154, 52, 18, 0.06);
}

.form-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.field-hint {
  margin: -4px 4px 0;
  color: var(--app-muted);
  font-size: 12px;
  line-height: 1.5;
}

.row-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.row-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.row-title span {
  color: var(--app-text);
  font-size: 14px;
  font-weight: 800;
}

h2 {
  margin: 0;
  color: var(--app-text);
  font-size: 17px;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.quick-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.quick-create {
  padding: 12px;
  border-radius: 16px;
  background: #fff7e8;
  border: 1px dashed #fed7aa;
}

.quick-actions {
  justify-content: flex-end;
  margin-top: 8px;
}

.list-tail-actions {
  margin-top: -2px;
  padding: 10px;
  border: 1px dashed #fed7aa;
  border-radius: 16px;
  background: linear-gradient(180deg, rgba(255, 247, 232, 0.48), rgba(255, 250, 242, 0.88));
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.list-tail-actions.single {
  grid-template-columns: 1fr;
}

.tail-action {
  min-width: 0;
  height: 38px;
  border: 1px solid var(--app-border);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
  color: #7c5c46;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  font-size: 13px;
  font-weight: 800;
}

.tail-action.primary {
  border-color: rgba(249, 115, 22, 0.42);
  background: #fff7e8;
  color: #c2410c;
}

.select-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.form-label span {
  display: block;
  margin-bottom: 6px;
  color: var(--app-muted);
  font-size: 13px;
}

.select-input,
.text-input {
  width: 100%;
  height: 42px;
  border: 1px solid var(--app-border);
  border-radius: 12px;
  padding: 0 11px;
  background: #fffaf2;
  color: var(--app-text);
  outline: 0;
}

.base-select-trigger {
  width: 100%;
  min-height: 48px;
  border: 1px solid var(--app-border);
  border-radius: 12px;
  padding: 6px 9px;
  background: #fffaf2;
  color: var(--app-text);
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) 16px;
  align-items: center;
  gap: 9px;
  text-align: left;
}

.base-select-trigger img,
.base-select-placeholder,
.base-option img,
.base-option-placeholder {
  width: 34px;
  height: 34px;
  border-radius: 10px;
}

.base-select-trigger img,
.base-option img {
  object-fit: cover;
}

.base-select-placeholder,
.base-option-placeholder {
  background: #fff1df;
  color: var(--app-primary);
  display: grid;
  place-items: center;
}

.base-select-trigger strong {
  min-width: 0;
  color: var(--app-text);
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.base-select-trigger strong.empty {
  color: var(--app-muted);
  font-weight: 500;
}

.base-picker {
  max-height: min(76vh, 620px);
  padding: 14px 14px max(16px, env(safe-area-inset-bottom));
  background: #fffaf2;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.base-picker-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.base-picker-head h3 {
  margin: 0;
  color: var(--app-text);
  font-size: 18px;
}

.base-picker-head button {
  width: 34px;
  height: 34px;
  border: 0;
  border-radius: 50%;
  background: #fff1df;
  color: #9b7d66;
  display: grid;
  place-items: center;
}

.base-picker-search {
  height: 42px;
  border: 1px solid var(--app-border);
  border-radius: 999px;
  padding: 0 12px;
  background: #fff;
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--app-primary);
}

.base-picker-search input {
  min-width: 0;
  flex: 1;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--app-text);
}

.base-option-list {
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.base-option {
  width: 100%;
  border: 1px solid var(--app-border);
  border-radius: 14px;
  padding: 8px;
  background: #fff;
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) 18px;
  align-items: center;
  gap: 10px;
  text-align: left;
}

.base-option img,
.base-option-placeholder {
  width: 42px;
  height: 42px;
  border-radius: 12px;
}

.base-option.active {
  border-color: var(--app-primary);
  background: #fff7e8;
}

.base-option-main {
  min-width: 0;
}

.base-option-main strong,
.base-option-main em {
  display: block;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.base-option-main strong {
  color: var(--app-text);
  font-size: 15px;
}

.base-option-main em {
  margin-top: 3px;
  color: var(--app-muted);
  font-size: 12px;
  font-style: normal;
}

.base-option > .van-icon {
  color: var(--app-primary);
}

.base-option-empty {
  padding: 28px 0;
  color: var(--app-muted);
  text-align: center;
  font-size: 14px;
}

:deep(.form-field) {
  border: 1px solid var(--app-border);
  border-radius: 12px;
  background: #fffaf2;
}

:deep(.form-field .van-field__label) {
  color: var(--app-muted);
}

.delete-row {
  align-self: flex-end;
  height: 32px;
  border: 0;
  border-radius: 999px;
  padding: 0 10px;
  background: #fff1f2;
  color: #be123c;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
}

.step-head {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
}

.step-head span {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--app-primary);
  color: #fff;
  display: grid;
  place-items: center;
  font-weight: 800;
}

.step-title-input {
  min-width: 0;
  height: 40px;
  border: 0;
  border-bottom: 1px solid var(--app-border);
  background: transparent;
  color: var(--app-text);
  outline: 0;
  font-weight: 700;
}

.step-move-actions {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.step-move-actions button {
  width: 30px;
  height: 30px;
  border: 1px solid var(--app-border);
  border-radius: 50%;
  background: #fffaf2;
  color: #9b7d66;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.step-move-actions button:disabled {
  opacity: 0.38;
}

.submit-spacer {
  height: 74px;
}

.fixed-submit {
  position: fixed;
  left: 50%;
  bottom: 0;
  z-index: 60;
  width: min(100%, 430px);
  transform: translateX(-50%);
  padding: 10px 12px max(10px, env(safe-area-inset-bottom));
  background: rgba(255, 250, 242, 0.96);
  border-top: 1px solid var(--app-border);
  box-shadow: 0 -10px 28px rgba(154, 52, 18, 0.1);
}

@media (max-width: 360px) {
  .select-grid {
    grid-template-columns: 1fr;
  }
}
</style>
