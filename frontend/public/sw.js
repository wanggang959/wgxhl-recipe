const CACHE_VERSION = 'v2'
const IMAGE_CACHE = `wgxhl-image-cache-${CACHE_VERSION}`
const META_CACHE = `wgxhl-image-meta-${CACHE_VERSION}`
const CACHE_PREFIX = 'wgxhl-image-cache-'
const META_PREFIX = 'wgxhl-image-meta-'
const MAX_IMAGE_ENTRIES = 300
const MAX_IMAGE_AGE_MS = 7 * 24 * 60 * 60 * 1000
const META_BASE_URL = 'https://wgxhl-meta.local/'

self.addEventListener('install', () => {
  self.skipWaiting()
})

self.addEventListener('activate', (event) => {
  event.waitUntil((async () => {
    const cacheKeys = await caches.keys()
    const staleKeys = cacheKeys.filter((key) =>
      (key.startsWith(CACHE_PREFIX) && key !== IMAGE_CACHE)
      || (key.startsWith(META_PREFIX) && key !== META_CACHE),
    )
    await Promise.all(staleKeys.map((key) => caches.delete(key)))
    await self.clients.claim()
  })())
})

self.addEventListener('fetch', (event) => {
  const { request } = event
  if (request.method !== 'GET') return

  const url = new URL(request.url)
  if (!/^https?:$/.test(url.protocol)) return
  if (!isImageRequest(request, url)) return

  event.respondWith(cacheFirstImage(request, event))
})

function isImageRequest(request, url) {
  if (request.destination === 'image') return true
  return /\.(png|jpe?g|webp|gif|svg|avif|ico)$/i.test(url.pathname)
}

async function cacheFirstImage(request, event) {
  const imageCache = await caches.open(IMAGE_CACHE)
  const metaCache = await caches.open(META_CACHE)
  const cached = await imageCache.match(request)
  const age = await getCacheAgeMs(metaCache, request.url)

  if (cached && age !== null && age > MAX_IMAGE_AGE_MS) {
    await imageCache.delete(request)
    await deleteMeta(metaCache, request.url)
  }

  const freshCached = await imageCache.match(request)
  if (freshCached) {
    // 命中缓存后异步更新，避免用户等待网络。
    event.waitUntil(updateImageCache(request))
    return freshCached
  }

  return fetchAndCacheImage(request, metaCache, imageCache)
}

async function updateImageCache(request) {
  try {
    const imageCache = await caches.open(IMAGE_CACHE)
    const metaCache = await caches.open(META_CACHE)
    await fetchAndCacheImage(request, metaCache, imageCache)
  } catch (error) {
    // 静默失败，保持当前可用缓存。
  }
}

async function fetchAndCacheImage(request, metaCache, imageCache) {
  const response = await fetch(request)
  if (response && (response.ok || response.type === 'opaque')) {
    await imageCache.put(request, response.clone())
    await saveMeta(metaCache, request.url, Date.now())
    await enforceImageCacheLimit(metaCache, imageCache)
  }
  return response
}

function getMetaRequest(url) {
  return new Request(`${META_BASE_URL}${encodeURIComponent(url)}`)
}

async function saveMeta(metaCache, url, cachedAt) {
  const metaResponse = new Response(JSON.stringify({ cachedAt }), {
    headers: { 'Content-Type': 'application/json' },
  })
  await metaCache.put(getMetaRequest(url), metaResponse)
}

async function deleteMeta(metaCache, url) {
  await metaCache.delete(getMetaRequest(url))
}

async function getCacheAgeMs(metaCache, url) {
  const metaResponse = await metaCache.match(getMetaRequest(url))
  if (!metaResponse) return null
  try {
    const meta = await metaResponse.json()
    if (!meta || typeof meta.cachedAt !== 'number') return null
    return Date.now() - meta.cachedAt
  } catch (error) {
    return null
  }
}

async function enforceImageCacheLimit(metaCache, imageCache) {
  const metaRequests = await metaCache.keys()
  if (metaRequests.length <= MAX_IMAGE_ENTRIES) return

  const entries = []
  for (const request of metaRequests) {
    const metaResponse = await metaCache.match(request)
    if (!metaResponse) continue
    try {
      const meta = await metaResponse.json()
      const encoded = request.url.replace(META_BASE_URL, '')
      entries.push({
        url: decodeURIComponent(encoded),
        cachedAt: Number(meta.cachedAt) || 0,
      })
    } catch (error) {
      // ignore invalid metadata row
    }
  }

  entries.sort((a, b) => a.cachedAt - b.cachedAt)
  const removeCount = Math.max(0, entries.length - MAX_IMAGE_ENTRIES)
  if (!removeCount) return

  const staleEntries = entries.slice(0, removeCount)
  await Promise.all(staleEntries.map(async (entry) => {
    await imageCache.delete(entry.url)
    await deleteMeta(metaCache, entry.url)
  }))
}
