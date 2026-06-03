import axios from 'axios'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000,
})

/** 无需登录的接口（登录页预览头像等不能带过期 token） */
const PUBLIC_API_PATHS = ['/user/login', '/user/guestLogin', '/user/preview']

function isPublicApi(url) {
  if (!url) return false
  const path = String(url).replace(/\?.*$/, '')
  return PUBLIC_API_PATHS.some((item) => path === item || path.endsWith(item))
}

request.interceptors.request.use((config) => {
  if (isPublicApi(config.url)) {
    return config
  }
  const raw = localStorage.getItem('wgxhl_recipe_user')
  if (raw) {
    try {
      const user = JSON.parse(raw)
      if (user?.token) {
        config.headers.Authorization = `Bearer ${user.token}`
      }
    } catch (error) {
      localStorage.removeItem('wgxhl_recipe_user')
    }
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.status !== 200) {
      if (res.status === 403 && /禁用/.test(res.message || '')) {
        localStorage.removeItem('wgxhl_recipe_user')
        if (!window.location.hash.includes('/login')) {
          window.location.hash = '#/login'
        }
      }
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res
  },
  (error) => Promise.reject(error)
)

export default request

export function post(url, data = {}) {
  return request.post(url, data)
}
