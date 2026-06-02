const COS_BASE_URL = (import.meta.env.VITE_COS_BASE_URL || '').replace(/\/$/, '')

export function getImageUrl(path, fallback = '') {
  if (!path) return fallback
  const value = String(path).trim()
  if (!value) return fallback
  if (/^(https?:|data:|blob:)/i.test(value)) return value
  if (!COS_BASE_URL) return value
  return `${COS_BASE_URL}/${value.replace(/^\/+/, '')}`
}

export function getRecipeImage(path) {
  return getImageUrl(path, '/favicon.svg')
}

export function toObjectPath(path) {
  if (!path) return ''
  const value = String(path).trim()
  if (!COS_BASE_URL || !value.startsWith(COS_BASE_URL)) return value
  return value.slice(COS_BASE_URL.length).replace(/^\/+/, '')
}
