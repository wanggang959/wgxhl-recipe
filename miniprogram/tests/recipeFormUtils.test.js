const test = require('node:test'), assert = require('node:assert/strict')
const {
  validate, buildPayload, normalizeCookingTime, parseCookingMinutes, isValidVersion, fromDetail, emptyForm, emptyStep, emptyIngredient, emptySeasoning,
} = require('../utils/recipeForm')

test('recipe validation requires a name and a two-part version', () => {
  const form = emptyForm()
  assert.match(validate(form), /菜名/)
  form.recipeName = '番茄炒蛋'
  form.recipeVersion = '1'
  assert.match(validate(form), /版本号/)
  form.recipeVersion = '1.0'
  assert.equal(validate(form), '')
  form.cookingMinutes = '0'
  assert.match(validate(form), /大于0/)
  assert.equal(isValidVersion('2.10'), true)
  assert.equal(isValidVersion('2.1.0'), false)
})

test('cooking time helpers keep minutes only', () => {
  assert.equal(parseCookingMinutes('约 30 分钟'), '30')
  assert.equal(parseCookingMinutes(''), '')
  assert.equal(normalizeCookingTime('30'), '30分钟')
  assert.equal(normalizeCookingTime('0'), '')
  assert.equal(normalizeCookingTime('abc'), '')
})

test('payload trims text, drops empty rows and renumbers steps', () => {
  const form = { ...emptyForm(), recipeName: ' 番茄炒蛋 ', recipeVersion: '1.0', recipeDesc: ' 家常 ', cookingMinutes: '20', taste: ' 咸鲜 ', categoryId: 'c1', categoryName: '家常' }
  const steps = [
    { ...emptyStep(0), stepTitle: '', stepDesc: '', stepImage: '' },
    { ...emptyStep(1), stepTitle: ' 切块 ', stepDesc: ' 切好 ', stepImage: 'recipe/step/a.jpg' },
  ]
  const ingredients = [
    { ...emptyIngredient(0), ingredientId: 'i1', ingredientName: '番茄', amount: ' 2 ', unit: '个' },
    { ...emptyIngredient(1), ingredientId: '', ingredientName: '' },
  ]
  const seasonings = [{ ...emptySeasoning(0), seasoningId: 's1', seasoningName: '盐', amount: '少许' }]
  const payload = buildPayload(form, steps, ingredients, seasonings)
  assert.equal(payload.recipe.recipeName, '番茄炒蛋')
  assert.equal(payload.recipe.cookingTime, '20分钟')
  assert.equal(payload.recipe.taste, '咸鲜')
  assert.equal(payload.recipe.categoryName, '家常')
  assert.equal(payload.recipeStepList.length, 1)
  assert.equal(payload.recipeStepList[0].stepNo, 1)
  assert.equal(payload.recipeStepList[0].stepTitle, '切块')
  assert.equal(payload.ingredientList.length, 1)
  assert.equal(payload.ingredientList[0].amount, '2')
  assert.equal(payload.seasoningList[0].unit, '适量')
})

test('edit payload keeps id and offline status', () => {
  const form = { ...emptyForm(), id: 'r1', recipeName: '旧菜', status: '下架' }
  const payload = buildPayload(form, [], [], [])
  assert.equal(payload.recipe.id, 'r1')
  assert.equal(payload.recipe.status, '下架')
})

test('detail conversion restores rows, units and minutes', () => {
  const parsed = fromDetail({
    recipe: { id: 'r1', recipeName: '番茄', recipeVersion: '2.0', cookingTime: '30分钟', categoryId: 'c1', visibility: 'public' },
    recipeStepList: [{ id: 's1', stepNo: 1, stepTitle: '切', stepDesc: '切块', stepImage: 'recipe/step/a.jpg' }],
    ingredientList: [{ id: 'i1', ingredientId: 'base-i1', ingredientName: '番茄', amount: '2', unit: '个', ingredientImage: 'ingredient/a.jpg' }],
    seasoningList: [{ id: 'se1', seasoningId: 'base-s1', seasoningName: '盐', amount: '少许', unit: '少许' }],
  })
  assert.equal(parsed.form.id, 'r1')
  assert.equal(parsed.form.cookingMinutes, '30')
  assert.equal(parsed.form.visibility, 'public')
  assert.equal(parsed.steps.length, 1)
  assert.equal(parsed.ingredients[0].ingredientId, 'base-i1')
  assert.equal(parsed.ingredients[0].unitIndex, 2)
  assert.equal(parsed.seasonings[0].unitIndex, 6)
})
