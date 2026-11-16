const difficultyOptions = ['入门', '简单', '普通', '困难', '大师']
const servingOptions = ['1-2人', '2-3人', '3-4人', '4-5人', '5人以上']
const ingredientUnits = ['克', '千克', '个', '根', '棵', '片', '勺', '适量']
const seasoningUnits = ['克', '毫升', '勺', '小勺', '大勺', '适量', '少许']

function emptyForm() {
  return {
    id: '',
    recipeName: '',
    recipeVersion: '1.0',
    recipeDesc: '',
    coverImage: '',
    difficulty: '普通',
    cookingMinutes: '',
    servingCount: '',
    taste: '',
    visibility: 'family',
    status: '上架',
    categoryId: '',
    categoryName: '',
    remark: '',
  }
}

function emptyStep(index) {
  return { key: `step-${Date.now()}-${index}`, stepNo: index + 1, stepTitle: '', stepDesc: '', stepImage: '', imageUrl: '' }
}

function emptyIngredient(index) {
  return { key: `ingredient-${Date.now()}-${index}`, ingredientId: '', ingredientName: '', ingredientImage: '', amount: '', unit: '克', unitIndex: 0 }
}

function emptySeasoning(index) {
  return { key: `seasoning-${Date.now()}-${index}`, seasoningId: '', seasoningName: '', seasoningImage: '', amount: '', unit: '适量', unitIndex: 5 }
}

function isValidVersion(value) {
  return /^\d+\.\d+$/.test(String(value || '').trim())
}

function parseCookingMinutes(text) {
  const matched = String(text || '').match(/\d+/)
  return matched ? matched[0] : ''
}

function normalizeCookingTime(minutes) {
  const value = Number.parseInt(minutes, 10)
  return Number.isFinite(value) && value > 0 ? `${value}分钟` : ''
}

function validate(form) {
  if (!String(form.recipeName || '').trim()) return '请输入菜名'
  if (!isValidVersion(form.recipeVersion)) return '版本号请填写为 1.0、2.1 这类两位数字格式'
  if (form.cookingMinutes && !(Number.parseInt(form.cookingMinutes, 10) > 0)) return '用时分钟数需大于0'
  return ''
}

function buildPayload(form, steps = [], ingredients = [], seasonings = []) {
  return {
    recipe: {
      ...(form.id ? { id: form.id } : {}),
      recipeName: String(form.recipeName || '').trim(),
      recipeVersion: String(form.recipeVersion || '').trim(),
      recipeDesc: String(form.recipeDesc || '').trim(),
      coverImage: form.coverImage || '',
      difficulty: form.difficulty || '普通',
      cookingTime: normalizeCookingTime(form.cookingMinutes),
      servingCount: form.servingCount || '',
      taste: String(form.taste || '').trim(),
      status: form.status || '上架',
      visibility: form.visibility || 'family',
      categoryId: form.categoryId || '',
      categoryName: form.categoryName || '',
      remark: String(form.remark || '').trim(),
    },
    recipeStepList: steps
      .filter(item => item.stepTitle || item.stepDesc || item.stepImage)
      .map((item, index) => ({
        stepNo: index + 1,
        stepTitle: String(item.stepTitle || '').trim(),
        stepDesc: String(item.stepDesc || '').trim(),
        stepImage: item.stepImage || '',
      })),
    ingredientList: ingredients
      .filter(item => item.ingredientId)
      .map((item, index) => ({
        ingredientId: item.ingredientId,
        ingredientName: item.ingredientName || '',
        ingredientImage: item.ingredientImage || '',
        amount: String(item.amount || '').trim(),
        unit: item.unit || '克',
        sortNo: index + 1,
      })),
    seasoningList: seasonings
      .filter(item => item.seasoningId)
      .map((item, index) => ({
        seasoningId: item.seasoningId,
        seasoningName: item.seasoningName || '',
        seasoningImage: item.seasoningImage || '',
        amount: String(item.amount || '').trim(),
        unit: item.unit || '适量',
        sortNo: index + 1,
      })),
    imageList: [],
  }
}

function normalizeStepNo(steps) {
  steps.forEach((item, index) => { item.stepNo = index + 1 })
  return steps
}

function fromDetail(detail) {
  const recipe = detail?.recipe || {}
  return {
    form: {
      ...emptyForm(),
      ...recipe,
      id: recipe.id || '',
      cookingMinutes: parseCookingMinutes(recipe.cookingTime),
      recipeVersion: recipe.recipeVersion || '1.0',
    },
    steps: (detail?.recipeStepList || []).map((item, index) => ({
      key: `step-${index}-${item.id || ''}`,
      stepNo: index + 1,
      stepTitle: item.stepTitle || '',
      stepDesc: item.stepDesc || '',
      stepImage: item.stepImage || '',
      imageUrl: '',
    })),
    ingredients: (detail?.ingredientList || []).map((item, index) => ({
      key: `ingredient-${index}-${item.id || ''}`,
      ingredientId: item.ingredientId || '',
      ingredientName: item.ingredientName || '',
      ingredientImage: item.ingredientImage || '',
      amount: item.amount || '',
      unit: item.unit || '克',
      unitIndex: Math.max(0, ingredientUnits.indexOf(item.unit || '克')),
    })),
    seasonings: (detail?.seasoningList || []).map((item, index) => ({
      key: `seasoning-${index}-${item.id || ''}`,
      seasoningId: item.seasoningId || '',
      seasoningName: item.seasoningName || '',
      seasoningImage: item.seasoningImage || '',
      amount: item.amount || '',
      unit: item.unit || '适量',
      unitIndex: Math.max(0, seasoningUnits.indexOf(item.unit || '适量')),
    })),
  }
}

module.exports = {
  difficultyOptions,
  servingOptions,
  ingredientUnits,
  seasoningUnits,
  emptyForm,
  emptyStep,
  emptyIngredient,
  emptySeasoning,
  isValidVersion,
  parseCookingMinutes,
  normalizeCookingTime,
  validate,
  buildPayload,
  normalizeStepNo,
  fromDetail,
}
