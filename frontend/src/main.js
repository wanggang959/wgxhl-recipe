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

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)

const userStore = useUserStore(pinia)
userStore.restore()

app.mount('#app')

if ('serviceWorker' in navigator && import.meta.env.PROD) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch(() => {})
  })
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
  root.style.setProperty('--ios-safe-area-top-fallback', '47px')
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
