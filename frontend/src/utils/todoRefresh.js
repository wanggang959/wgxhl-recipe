const TODO_REFRESH_KEY = 'wgxhl_recipe_todo_refresh_required'

export function markTodoRefreshRequired() {
  sessionStorage.setItem(TODO_REFRESH_KEY, '1')
}

export function consumeTodoRefreshRequired() {
  const required = sessionStorage.getItem(TODO_REFRESH_KEY) === '1'
  if (required) {
    sessionStorage.removeItem(TODO_REFRESH_KEY)
  }
  return required
}
