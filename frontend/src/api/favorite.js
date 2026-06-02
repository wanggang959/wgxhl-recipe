import { post } from './request'

export function pageFavorite(params) {
  return post('/favorite/page', params)
}

export function createFavorite(payload) {
  return post('/favorite/create', payload)
}

export function deleteFavorite(id) {
  return post('/favorite/delete', { id })
}

export function deleteFavoriteByRecipeId(userId, recipeId) {
  return post('/favorite/deleteByRecipeId', { userId, recipeId })
}

export function checkFavorite(userId, recipeId) {
  return post('/favorite/check', { userId, recipeId })
}
