const base = 'https://wg-recipe-1385696563.cos.ap-chengdu.myqcloud.com/'
function imageUrl(path) { const value=String(path||'').trim(); return /^https:\/\//i.test(value)?value:value?base+value.replace(/^\/+/, ''):'' }
function presentRecipe(recipe) {
  const source = recipe || {}
  return {
    ...source,
    coverUrl: imageUrl(source.coverImage),
    categoryLabel: source.categoryName || '未分类',
    tasteLabel: source.taste || '家常',
    difficultyLabel: source.difficulty || '普通',
    timeLabel: source.cookingTime || '未记录',
    versionLabel: source.recipeVersion || '1.0',
    favorite: Boolean(source.favorite),
    liked: Boolean(source.liked),
    wanted: Boolean(source.wanted),
    favoriteCount: Math.max(0, Number(source.favoriteCount || 0)),
    likeCount: Math.max(0, Number(source.likeCount || 0)),
  }
}
function presentDetail(detail) {
  const convert=(list,key)=>(list||[]).map(item=>({...item,imageUrl:imageUrl(item[key])}))
  return {recipe:presentRecipe(detail?.recipe||{}),ingredients:convert(detail?.ingredientList,'ingredientImage'),seasonings:convert(detail?.seasoningList,'seasoningImage'),steps:convert(detail?.recipeStepList,'stepImage')}
}
function mergeFavorite(record, recipe) {
  const merged = presentRecipe(recipe || {})
  return {
    ...merged,
    id: record.recipeId,
    recipeId: record.recipeId,
    favoriteId: record.id,
    favorite: true,
    coverUrl: merged.coverUrl || imageUrl(record.coverImage),
    recipeName: merged.recipeName || record.recipeName || '未命名菜谱',
  }
}
function recipeImageUrls(detail) {
  const urls = []
  const push = value => { const url = imageUrl(value); if (url && !urls.includes(url)) urls.push(url) }
  push(detail?.recipe?.coverImage)
  ;(detail?.ingredientList || []).forEach(item => push(item.ingredientImage))
  ;(detail?.seasoningList || []).forEach(item => push(item.seasoningImage))
  ;(detail?.recipeStepList || []).forEach(item => push(item.stepImage))
  return urls
}
module.exports={imageUrl,presentRecipe,presentDetail,mergeFavorite,recipeImageUrls}
