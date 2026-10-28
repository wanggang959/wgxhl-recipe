import { getPushStatus, subscribePush, unsubscribePush } from '../api/push'

const SW_READY_TIMEOUT_MS = 2500
const SW_REG_TIMEOUT_MS = 1500

function withTimeout(promise, ms, fallback = undefined) {
  return Promise.race([
    promise,
    new Promise((resolve) => {
      window.setTimeout(() => resolve(fallback), ms)
    }),
  ])
}

async function getServiceWorkerRegistration() {
  if (!isPushSupported()) return null
  return withTimeout(navigator.serviceWorker.getRegistration('/'), SW_REG_TIMEOUT_MS, null)
}

export function isPushSupported() {
  return typeof window !== 'undefined'
    && 'serviceWorker' in navigator
    && 'PushManager' in window
    && 'Notification' in window
}

export function isStandaloneApp() {
  if (typeof window === 'undefined') return false
  return window.navigator.standalone === true
    || window.matchMedia?.('(display-mode: standalone)').matches
}

export function isIosLike() {
  if (typeof window === 'undefined') return false
  const ua = window.navigator.userAgent || ''
  return /iP(hone|ad|od)/.test(ua)
    || (ua.includes('Macintosh') && window.navigator.maxTouchPoints > 1)
}

export async function loadPushState() {
  const state = {
    supported: isPushSupported(),
    permission: typeof Notification === 'undefined' ? 'unsupported' : Notification.permission,
    subscribed: false,
    configured: false,
    publicKey: '',
    needsStandalone: isIosLike() && !isStandaloneApp(),
  }

  if (!state.supported) return state

  try {
    const res = await getPushStatus()
    state.configured = Boolean(res.data?.configured)
    state.publicKey = res.data?.publicKey || ''
  } catch (error) {
    state.loadError = error.message || '通知状态加载失败'
    state.configured = false
  }

  try {
    await withTimeout(navigator.serviceWorker.ready, SW_READY_TIMEOUT_MS)
    const registration = await getServiceWorkerRegistration()
    const subscription = await registration?.pushManager?.getSubscription?.()
    if (subscription?.endpoint) {
      const res = await getPushStatus(subscription.endpoint)
      state.subscribed = Boolean(res.data?.subscribed)
    }
  } catch (error) {
    state.loadError = '当前设备通知状态暂时无法确认'
  }

  return state
}

export async function enablePushNotification() {
  if (!isPushSupported()) {
    throw new Error('当前浏览器不支持通知')
  }
  if (isIosLike() && !isStandaloneApp()) {
    throw new Error('请先添加到主屏幕，再从主屏幕打开后开启通知')
  }

  const status = await getPushStatus()
  const publicKey = status.data?.publicKey || ''
  if (!status.data?.configured || !publicKey) {
    throw new Error('服务端还没有配置推送密钥')
  }

  const permission = await Notification.requestPermission()
  if (permission !== 'granted') {
    throw new Error('通知权限未开启')
  }

  const registration = await ensureServiceWorkerRegistration()
  let subscription = await registration.pushManager.getSubscription()
  if (subscription) {
    const currentStatus = await getPushStatus(subscription.endpoint)
    if (currentStatus.data?.subscribed) return loadPushState()
    await subscription.unsubscribe()
    subscription = null
  }
  try {
    subscription = await registration.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: urlBase64ToUint8Array(publicKey),
    })
  } catch (error) {
    if (error?.name === 'AbortError' || /push service error/i.test(error?.message || '')) {
      throw new Error('系统已授权，但浏览器推送服务注册失败。可能与手机系统、浏览器或网络有关；站内通知仍可使用。')
    }
    if (error?.name === 'NotAllowedError') {
      throw new Error('浏览器未允许通知，请检查浏览器和系统通知设置。')
    }
    throw new Error('浏览器通知订阅失败，请检查网络和通知设置后重试。')
  }

  const payload = subscriptionToPayload(subscription)
  await subscribePush(payload)
  return loadPushState()
}

/** 退出登录时调用：带超时，不等待 loadPushState，避免 iOS PWA 卡住 */
export async function teardownPushOnLogout() {
  if (!isPushSupported()) return

  const registration = await getServiceWorkerRegistration()
  const subscription = registration
    ? await withTimeout(registration.pushManager.getSubscription(), SW_REG_TIMEOUT_MS, null)
    : null
  const payload = subscription ? subscriptionToPayload(subscription) : {}

  if (payload.endpoint) {
    await withTimeout(unsubscribePush(payload), 3000).catch(() => {})
  }
  if (subscription) {
    await withTimeout(subscription.unsubscribe(), 2000).catch(() => {})
  }
}

export async function disablePushNotification() {
  if (!isPushSupported()) {
    return loadPushState()
  }

  const registration = await getServiceWorkerRegistration()
  const subscription = registration
    ? await registration.pushManager.getSubscription()
    : null
  const payload = subscription ? subscriptionToPayload(subscription) : {}

  try {
    if (payload.endpoint) await withTimeout(unsubscribePush(payload), 8000)
  } catch (error) {
    // 服务端失败时仍尝试本地退订
  } finally {
    if (subscription) {
      await withTimeout(subscription.unsubscribe(), 2000).catch(() => {})
    }
  }

  return loadPushState()
}

async function ensureServiceWorkerRegistration() {
  let registration = await getServiceWorkerRegistration()
  if (!registration) {
    registration = await navigator.serviceWorker.register('/sw.js')
  }
  await withTimeout(navigator.serviceWorker.ready, SW_READY_TIMEOUT_MS)
  return registration || await getServiceWorkerRegistration()
}

function subscriptionToPayload(subscription) {
  const data = subscription.toJSON()
  return {
    endpoint: data.endpoint,
    p256dh: data.keys?.p256dh || '',
    auth: data.keys?.auth || '',
    userAgent: window.navigator.userAgent || '',
  }
}

function urlBase64ToUint8Array(base64String) {
  const padding = '='.repeat((4 - (base64String.length % 4)) % 4)
  const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/')
  const rawData = window.atob(base64)
  const outputArray = new Uint8Array(rawData.length)

  for (let i = 0; i < rawData.length; i += 1) {
    outputArray[i] = rawData.charCodeAt(i)
  }
  return outputArray
}
