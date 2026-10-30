export const ADMIN_LOGIN_URL = '/#/login?redirect=%2Fadmin'

export function loginTarget(value) {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//')
      || value.includes('\\') || /[\u0000-\u001f\u007f]/.test(value) || value.length > 2000) return '/recipes'
  return value === '/admin/' ? '/admin' : value
}

export function navigateAfterLogin(value, router, location) {
  const target = loginTarget(value)
  if (target === '/admin') location.replace('/admin')
  else router.replace(target)
}
