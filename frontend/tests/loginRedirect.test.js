import test from 'node:test'
import assert from 'node:assert/strict'
import { ADMIN_LOGIN_URL, loginTarget, navigateAfterLogin } from '../src/utils/loginRedirect.js'

test('admin login link preserves the dedicated admin destination', () => {
  const url = new URL(ADMIN_LOGIN_URL, 'https://app.example.test')
  assert.equal(new URLSearchParams(url.hash.split('?')[1]).get('redirect'), '/admin')
})

test('admin destinations use document navigation instead of an unknown SPA route', () => {
  for (const target of ['/admin', '/admin/']) {
    const calls = []
    navigateAfterLogin(target, { replace: () => assert.fail('admin is not an SPA route') }, { replace: value => calls.push(value) })
    assert.deepEqual(calls, ['/admin'])
  }
})

test('ordinary destinations keep using the existing router', () => {
  const calls = []
  navigateAfterLogin('/todo?range=week', { replace: value => calls.push(value) }, { replace: () => assert.fail('unexpected page reload') })
  assert.deepEqual(calls, ['/todo?range=week'])
})

test('external and malformed destinations fall back to recipes', () => {
  for (const value of [null, undefined, [], '', 'https://evil.test', '//evil.test', '/\\evil.test', '/admin\n', 'javascript:alert(1)']) {
    assert.equal(loginTarget(value), '/recipes')
  }
})
