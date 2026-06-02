import { post } from './request'

export function pageSeasoning(params) {
  return post('/seasoning/page', params)
}

export function createSeasoning(payload) {
  return post('/seasoning/create', payload)
}

export function updateSeasoning(payload) {
  return post('/seasoning/update', payload)
}

export function deleteSeasoning(id) {
  return post('/seasoning/delete', { id })
}
