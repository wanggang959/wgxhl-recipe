const MAX_AGE = 5 * 60 * 1000
let snapshot = null

export function readTodoDetail(userId, householdId, todoId, now = Date.now()) {
  if (!userId || !householdId || !todoId || !snapshot) return null
  if (snapshot.userId !== userId || snapshot.householdId !== householdId || snapshot.todoId !== String(todoId)) return null
  if (now - snapshot.savedAt > MAX_AGE) return null
  return snapshot.todo
}

export function saveTodoDetail(userId, householdId, todoId, todo, now = Date.now()) {
  if (!userId || !householdId || !todoId || !todo) return
  snapshot = { userId, householdId, todoId: String(todoId), todo, savedAt: now }
}

export function clearTodoDetail() {
  snapshot = null
}
