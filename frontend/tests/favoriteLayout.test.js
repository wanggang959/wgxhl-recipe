import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

test('favorite cards keep their content height when the list is short', () => {
  const source = readFileSync(new URL('../src/views/FavoriteList.vue', import.meta.url), 'utf8')
  const styles = source.match(/\.cards\s*\{([^}]*)\}/)?.[1] || ''
  assert.match(styles, /grid-template-columns:\s*repeat\(2,/)
  assert.match(styles, /grid-auto-rows:\s*max-content/)
  assert.match(styles, /align-content:\s*start/)
  assert.match(styles, /align-items:\s*start/)
})
