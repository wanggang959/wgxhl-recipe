import { post } from './request'

export function pageExpenseProjects(params) {
  return post('/expense/project/page', params)
}

export function getExpenseProject(id) {
  return post('/expense/project/getById', { id })
}

export function createExpenseProject(payload) {
  return post('/expense/project/create', payload)
}

export function updateExpenseProject(payload) {
  return post('/expense/project/update', payload)
}

export function archiveExpenseProject(id) {
  return post('/expense/project/archive', { id })
}

export function restoreExpenseProject(id) {
  return post('/expense/project/restore', { id })
}

export function deleteExpenseProject(id) {
  return post('/expense/project/delete', { id })
}

export function getExpenseSummary(id) {
  return post('/expense/project/summary', { id })
}

export function pageExpenseItems(params) {
  return post('/expense/item/page', params)
}

export function createExpenseItem(payload) {
  return post('/expense/item/create', payload)
}

export function updateExpenseItem(payload) {
  return post('/expense/item/update', payload)
}

export function deleteExpenseItem(id) {
  return post('/expense/item/delete', { id })
}
