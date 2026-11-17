const config = require('../config')
const session = require('./session')

function apiBase() {
  const base = String(config.apiBaseUrl || '').replace(/\/+$/, '')
  if (!/^https:\/\/[^/]+\/recipe$/.test(base)) throw new Error('尚未配置测试服务地址')
  return base
}

function networkError(error) {
  const message = String(error?.errMsg || '')
  if (/url not in domain list|not in domain list|不在.*合法域名/i.test(message)) {
    return new Error('请求域名未配置，请在微信后台添加合法域名')
  }
  if (/timeout|timed out/i.test(message)) return new Error('连接超时，请检查网络后重试')
  return new Error('网络连接失败，请检查网络或服务配置')
}

function request(method, path, data = {}, options = {}) {
  let base
  try { base = apiBase() } catch (error) { return Promise.reject(error) }
  const user = session.get()
  if (!options.public && !user?.token) return Promise.reject(new Error('请先登录'))
  return new Promise((resolve, reject) => wx.request({
    url: base + path, method, data, timeout: options.timeout || 15000,
    header: { 'content-type': 'application/json', ...(!options.public ? { Authorization: `Bearer ${user.token}` } : {}) },
    success(response) {
      const body = response.data || {}
      if (response.statusCode === 401 || body.status === 401) {
        if (!options.public) session.clear()
        reject(new Error(body.message || '登录已失效，请重新登录'))
      } else if (response.statusCode < 200 || response.statusCode >= 300 || body.status !== 200) {
        const error = new Error(body.message || '请求失败，请稍后重试')
        error.status = body.status
        error.httpStatus = response.statusCode
        reject(error)
      } else resolve(body.data)
    },
    fail(error) { reject(networkError(error)) }
  }))
}

function post(path, data = {}, options = {}) { return request('POST', path, data, options) }
function get(path, data = {}, options = {}) { return request('GET', path, data, options) }

function upload(path, filePath, formData = {}, options = {}) {
  let base
  try { base = apiBase() } catch (error) { return Promise.reject(error) }
  const user = session.get()
  if (!options.public && !user?.token) return Promise.reject(new Error('请先登录'))
  return new Promise((resolve, reject) => wx.uploadFile({
    url: base + path, filePath, name: 'file', formData, timeout: 60000,
    header: { ...(!options.public ? { Authorization: `Bearer ${user.token}` } : {}) },
    success(response) {
      let body = {}
      try { body = JSON.parse(response.data || '{}') } catch (_) { body = {} }
      if (response.statusCode === 401 || body.status === 401) {
        if (!options.public) session.clear()
        reject(new Error(body.message || '登录已失效，请重新登录'))
      } else if (response.statusCode < 200 || response.statusCode >= 300 || body.status !== 200) {
        reject(new Error(body.message || '上传失败，请稍后重试'))
      } else resolve(body.data)
    },
    fail(error) { reject(networkError(error)) }
  }))
}

module.exports = { post, get, upload }
