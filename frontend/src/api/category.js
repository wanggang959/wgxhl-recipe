import { post } from './request'

export function pageCategory(params) {
  return post('/category/page', params)
}

export function createCategory(payload) {
  return post('/category/create', payload)
}

export function updateCategory(payload) {
  return post('/category/update', payload)
}

export function deleteCategory(id) {
  return post('/category/delete', { id })
}
