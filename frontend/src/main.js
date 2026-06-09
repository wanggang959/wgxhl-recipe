import { createApp } from 'vue'
import { createPinia } from 'pinia'
import 'vant/lib/index.css'
import './style.css'
import App from './App.vue'
import router from './router'
import { useUserStore } from './stores/user'

applyIosSafeAreaFallback()
syncThemeColor()
setupShareMetadata()
setupMobileZoomLock()
setupViewportMetrics()

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)

const userStore = useUserStore(pinia)
userStore.restore()

let pendingNotificationPath = readPendingNotificationPath()

setupNotificationNavigation()

app.mount('#app')

if (pendingNotificationPath) {
  navigateFromNotification(pendingNotificationPath)
  pendingNotificationPath = null
}

if ('serviceWorker' in navigator && import.meta.env.PROD) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch(() => {})
  })
}

function setupNotificationNavigation() {
  if (typeof window === 'undefined' || !('serviceWorker' in navigator)) return

  navigator.serviceWorker.addEventListener('message', (event) => {
    const data = event.data || {}
    if (data.type === 'reload-to' && data.url) {
      window.location.replace(String(data.url))
      return
    }
    if (data.type !== 'navigate' || !data.url) return
    refreshViewportMetrics()
    navigateFromNotification(String(data.url))
  })

  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') {
      refreshViewportMetrics()
      syncRouteFromHash()
    }
  })

  window.addEventListener('focus', () => {
    refreshViewportMetrics()
    syncRouteFromHash()
  })
  window.addEventListener('hashchange', () => {
    refreshViewportMetrics()
    syncRouteFromHash()
  })
}

function getPathFromHash() {
  if (typeof window === 'undefined') return null
  const hash = window.location.hash.replace(/^#/, '')
  if (!hash) return null
  return hash.startsWith('/') ? hash : `/${hash}`
}

function readPendingNotificationPath() {
  const path = getPathFromHash()
  if (!path) return null
  return path === '/todo' || path.startsWith('/todo/') ? path : null
}

function syncRouteFromHash() {
  const path = getPathFromHash()
  if (!path || router.currentRoute.value.path === path) return
  router.replace(path).then(() => {
    window.dispatchEvent(new CustomEvent('app:route-refresh', { detail: { path } }))
  }).catch(() => {
    window.location.hash = path
  })
}

function navigateFromNotification(url) {
  const path = url.startsWith('/') ? url : `/${url}`
  const go = () => {
    refreshViewportMetrics()
    router.replace(path).then(() => {
      refreshViewportMetrics()
      window.dispatchEvent(new CustomEvent('app:route-refresh', { detail: { path } }))
    }).catch(() => {
      window.location.replace(`${window.location.origin}${window.location.pathname}#${path}`)
    })
  }

  if (typeof router.isReady === 'function') {
    router.isReady().then(() => {
      go()
      window.setTimeout(go, 120)
    })
    return
  }
  go()
}

function setupViewportMetrics() {
  if (typeof window === 'undefined' || typeof document === 'undefined') return

  refreshViewportMetrics()
  window.addEventListener('resize', refreshViewportMetrics)
  window.addEventListener('orientationchange', () => {
    refreshViewportMetrics()
    window.setTimeout(refreshViewportMetrics, 180)
  })

  if (window.visualViewport) {
    window.visualViewport.addEventListener('resize', refreshViewportMetrics)
    window.visualViewport.addEventListener('scroll', refreshViewportMetrics)
  }
}

function refreshViewportMetrics() {
  if (typeof window === 'undefined' || typeof document === 'undefined') return

  const viewport = window.visualViewport
  const height = Math.round(viewport?.height || window.innerHeight || document.documentElement.clientHeight || 0)
  if (height > 0) {
    document.documentElement.style.setProperty('--app-viewport-height', `${height}px`)
  }
}

function applyIosSafeAreaFallback() {
  if (typeof window === 'undefined') return

  const ua = window.navigator.userAgent || ''
  const isIos = /iP(hone|ad|od)/.test(ua)
    || (ua.includes('Macintosh') && window.navigator.maxTouchPoints > 1)
  if (!isIos) return

  const isStandalone = window.navigator.standalone === true
    || window.matchMedia?.('(display-mode: standalone)').matches
  if (!isStandalone) return

  const narrowSide = Math.min(window.screen.width, window.screen.height)
  const longSide = Math.max(window.screen.width, window.screen.height)
  const looksLikeProMax = narrowSide >= 428 && longSide >= 900
  if (!looksLikeProMax) return

  const root = document.documentElement
  root.style.setProperty('--ios-safe-area-top-fallback', '59px')
  root.style.setProperty('--ios-safe-area-bottom-fallback', '34px')
}

function syncThemeColor() {
  if (typeof document === 'undefined') return
  const themeColor = '#fffaf2'
  const meta = document.querySelector('meta[name="theme-color"]')
  if (meta) {
    meta.setAttribute('content', themeColor)
  }
}

function setupShareMetadata() {
  if (typeof window === 'undefined' || typeof document === 'undefined') return

  const imageUrl = new URL('/wechat-share.png', window.location.origin).href
  const pageUrl = window.location.href
  const imageMetaSelectors = [
    'meta[property="og:image"]',
    'meta[property="og:image:secure_url"]',
    'meta[itemprop="image"]',
    'meta[name="twitter:image"]',
    'meta[name="thumbnail"]',
  ]

  imageMetaSelectors.forEach((selector) => {
    const meta = document.querySelector(selector)
    if (meta) {
      meta.setAttribute('content', imageUrl)
    }
  })

  const imageLink = document.querySelector('link[rel="image_src"]')
  if (imageLink) {
    imageLink.setAttribute('href', imageUrl)
  }

  let urlMeta = document.querySelector('meta[property="og:url"]')
  if (!urlMeta) {
    urlMeta = document.createElement('meta')
    urlMeta.setAttribute('property', 'og:url')
    document.head.appendChild(urlMeta)
  }
  urlMeta.setAttribute('content', pageUrl)
}

function setupMobileZoomLock() {
  if (typeof window === 'undefined' || typeof document === 'undefined') return

  const isTouchDevice = window.navigator.maxTouchPoints > 0
  if (!isTouchDevice) return

  const preventGesture = (event) => {
    event.preventDefault()
  }

  document.addEventListener('gesturestart', preventGesture, { passive: false })
  document.addEventListener('gesturechange', preventGesture, { passive: false })
  document.addEventListener('gestureend', preventGesture, { passive: false })
  document.addEventListener('touchmove', (event) => {
    if (event.touches.length > 1) {
      event.preventDefault()
    }
  }, { passive: false })
}
