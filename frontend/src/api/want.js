import { post } from './request'

export function pageWantedRecipe(params) {
  return post('/want/page', params)
}

export function listWantedDates(userId) {
  return post('/want/dateList', { userId })
}

export function createWantedRecipe(payload) {
  return post('/want/create', payload)
}

export function updateWantedDate(payload) {
  return post('/want/updateDate', payload)
}

export function deleteWantedRecipe(id) {
  return post('/want/delete', { id })
}

export function deleteWantedRecipeByRecipeId(userId, recipeId) {
  return post('/want/deleteByRecipeId', { userId, recipeId })
}

export function checkWantedRecipe(userId, recipeId) {
  return post('/want/check', { userId, recipeId })
}
