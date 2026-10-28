import test from 'node:test'
import assert from 'node:assert/strict'
import { isDeviceSubscriptionUnknown, pushStatusText } from '../src/utils/pushStatusText.js'

const base = { supported: true, needsStandalone: false, permission: 'granted', configKnown: true, configured: true }

test('known permission denial is not hidden by a loading error', () => {
  assert.equal(pushStatusText({ ...base, permission: 'denied', loadError: '状态加载失败' }), '系统权限已关闭')
})

test('known server configuration failure is not hidden by subscription failure', () => {
  assert.equal(pushStatusText({ ...base, configured: false, loadError: '订阅查询失败' }), '服务端未配置')
})

test('a failed configuration request does not claim the server is unconfigured', () => {
  assert.equal(pushStatusText({ ...base, configured: false, configKnown: false, loadError: '连接失败' }), '连接失败')
})

test('an iOS subscription without a device endpoint is unknown, not disabled or enabled', () => {
  assert.equal(isDeviceSubscriptionUnknown({ hasRegistration: true, isIos: true, permission: 'granted' }), true)
  assert.equal(isDeviceSubscriptionUnknown({ hasRegistration: true, isIos: false, permission: 'granted' }), false)
  assert.equal(isDeviceSubscriptionUnknown({ hasRegistration: false, isIos: false, permission: 'granted' }), true)
  assert.equal(isDeviceSubscriptionUnknown({ hasRegistration: true, isIos: true, permission: 'default' }), false)
  assert.equal(pushStatusText({ ...base, statusUnknown: true, subscribed: false }), '当前设备通知状态暂时无法确认')
  assert.equal(pushStatusText({ ...base, subscribed: true }), '已开启')
  assert.equal(pushStatusText({ ...base, subscribed: false }), '未开启')
})
