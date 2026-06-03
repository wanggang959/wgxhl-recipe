import { DEFAULT_AVATARS } from '../constants/defaultAvatars'

function hashString(value) {
  let hash = 0
  const text = String(value || '')
  for (let i = 0; i < text.length; i += 1) {
    hash = (hash * 31 + text.charCodeAt(i)) | 0
  }
  return Math.abs(hash)
}

export function pickDefaultAvatar(seed) {
  const index = hashString(seed) % DEFAULT_AVATARS.length
  return DEFAULT_AVATARS[index]
}

/** 展示用头像：优先用户已选，否则按 id/用户名稳定分配默认图 */
export function userAvatarSrc(user) {
  const custom = user?.avatar?.trim()
  if (custom) return custom
  const seed = user?.id || user?.username || 'guest'
  return pickDefaultAvatar(seed)
}

export function isBuiltinAvatar(path) {
  return DEFAULT_AVATARS.includes(path)
}
