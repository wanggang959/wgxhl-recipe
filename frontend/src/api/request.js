import axios from 'axios'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000,
})

request.interceptors.request.use((config) => {
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
