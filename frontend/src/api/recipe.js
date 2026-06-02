import { post } from './request'

export function pageRecipe(params) {
  return post('/recipe/page', params)
}

export function getRecipeDetail(id) {
  return post('/recipe/getById', { id })
}

export function createRecipe(payload) {
  return post('/recipe/create', payload)
}

export function updateRecipe(payload) {
  return post('/recipe/update', payload)
}

export function deleteRecipe(id) {
  return post('/recipe/delete', { id })
}
