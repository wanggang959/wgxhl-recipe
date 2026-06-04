import { createApp } from 'vue'
import { createPinia } from 'pinia'
import 'vant/lib/index.css'
import './style.css'
import App from './App.vue'
import router from './router'
import { useUserStore } from './stores/user'

applyIosSafeAreaFallback()
syncThemeColor()

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
