/** 内置默认卡通头像（静态资源，与 backend DefaultAvatars 路径一致） */
export const DEFAULT_AVATARS = Array.from({ length: 15 }, (_, i) => {
  const num = String(i + 1).padStart(2, '0')
  return `/avatars/${num}.png`
})

export const DEFAULT_AVATAR_COUNT = DEFAULT_AVATARS.length
