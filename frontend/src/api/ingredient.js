import { post } from './request'

export function pageIngredient(params) {
  return post('/ingredient/page', params)
}

export function createIngredient(payload) {
  return post('/ingredient/create', payload)
}

export function updateIngredient(payload) {
  return post('/ingredient/update', payload)
}

export function deleteIngredient(id) {
  return post('/ingredient/delete', { id })
}
