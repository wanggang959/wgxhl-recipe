const base = 'https://wg-recipe-1385696563.cos.ap-chengdu.myqcloud.com/'
function imageUrl(path) { const value=String(path||'').trim(); return /^https:\/\//i.test(value)?value:value?base+value.replace(/^\/+/, ''):'' }
function presentRecipe(recipe) { return {...recipe,coverUrl:imageUrl(recipe.coverImage),categoryLabel:recipe.categoryName||'未分类',tasteLabel:recipe.taste||'家常',difficultyLabel:recipe.difficulty||'普通',timeLabel:recipe.cookingTime||'未记录'} }
function presentDetail(detail) {
  const convert=(list,key)=>(list||[]).map(item=>({...item,imageUrl:imageUrl(item[key])}))
  return {recipe:presentRecipe(detail?.recipe||{}),ingredients:convert(detail?.ingredientList,'ingredientImage'),seasonings:convert(detail?.seasoningList,'seasoningImage'),steps:convert(detail?.recipeStepList,'stepImage')}
}
module.exports={imageUrl,presentRecipe,presentDetail}
