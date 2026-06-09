import { post } from './request'

export function pageTodo(params) {
  return post('/todo/page', params)
}

export function getUpcomingTodos(limit = 3) {
  return post('/todo/upcoming', { size: limit })
}

export function getTodoSummary() {
  return post('/todo/summary')
}

export function getTodoDetail(id) {
  return post('/todo/getById', { id })
}

export function createTodo(payload) {
  return post('/todo/create', payload)
}

export function updateTodo(payload) {
  return post('/todo/update', payload)
}

export function completeTodo(id) {
  return post('/todo/complete', { id })
}

export function deleteTodo(id) {
  return post('/todo/delete', { id })
}
