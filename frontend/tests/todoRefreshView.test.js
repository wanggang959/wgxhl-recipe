import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { clearTodoDetail, readTodoDetail, saveTodoDetail } from '../src/utils/todoDetailCache.js'

test('detail snapshot belongs to the same account, household, and todo and expires', () => {
  clearTodoDetail()
  const todo = { id: 't1', title: '待办' }
  saveTodoDetail('u1', 'h1', 't1', todo, 1000)
  assert.equal(readTodoDetail('u1', 'h1', 't1', 2000), todo)
  assert.equal(readTodoDetail('u2', 'h1', 't1', 2000), null)
  assert.equal(readTodoDetail('u1', 'h2', 't1', 2000), null)
  assert.equal(readTodoDetail('u1', 'h1', 't2', 2000), null)
  assert.equal(readTodoDetail('u1', 'h1', 't1', 301001), null)
  clearTodoDetail()
  assert.equal(readTodoDetail('u1', 'h1', 't1', 2000), null)
})

test('todo pages keep snapshots while refreshing and invalidate across households', () => {
  const source = name => readFileSync(new URL(`../src/views/${name}.vue`, import.meta.url), 'utf8')
  const list = source('TodoList'), detail = source('TodoDetailView')
  assert.match(list, /const hasData = hasSnapshot\.value/)
  assert.match(list, /if \(loadingPromise\) \{ pendingRefresh = true; return \}/)
  assert.match(list, /userStore\.user\?\.householdId/)
  assert.match(list, /if \(version !== requestVersion\) return/)
  assert.match(detail, /loading\.value = !todo\.value/)
  assert.match(detail, /onBeforeUnmount\(\(\) => \{ requestVersion \+= 1 \}\)/)
})
