const api = require('../../services/api')
const session = require('../../services/session')
const { avatarUrl } = require('../../utils/profile')
const REMEMBER_USERNAME_KEY = 'wgxhl_recipe_login_username'
Page({
  data: {
    statusBarHeight: 0,
    username: '',
    password: '',
    passwordVisible: false,
    rememberMe: false,
    loading: false,
    statusText: '',
    statusType: 'success',
    previewAvatar: '',
  },
  onLoad() {
    if (session.get()?.token) { wx.switchTab({ url: '/pages/home/index' }); return }
    const info = typeof wx.getWindowInfo === 'function' ? wx.getWindowInfo() : wx.getSystemInfoSync()
    this.setData({ statusBarHeight: Number(info?.statusBarHeight || 0) })
    const remembered = wx.getStorageSync(REMEMBER_USERNAME_KEY) || ''
    if (remembered) {
      this.setData({ username: remembered, rememberMe: true })
      this.fetchPreview(remembered)
    }
  },
  onUnload() { clearTimeout(this.previewTimer) },
  onUsername(e) {
    const value = e.detail.value
    this.setData({ username: value, statusText: '', previewAvatar: '' })
    clearTimeout(this.previewTimer)
    const name = value.trim()
    if (!name || name.includes('@')) {
      this.previewRequestId = (this.previewRequestId || 0) + 1
      return
    }
    this.previewTimer = setTimeout(() => this.fetchPreview(name), 320)
  },
  onPassword(e) { this.setData({ password: e.detail.value, statusText: '' }) },
  togglePassword() { this.setData({ passwordVisible: !this.data.passwordVisible }) },
  toggleRemember() { this.setData({ rememberMe: !this.data.rememberMe }) },
  async fetchPreview(username) {
    const name = String(username || '').trim()
    if (!name || name.includes('@')) {
      this.previewRequestId = (this.previewRequestId || 0) + 1
      this.setData({ previewAvatar: '' })
      return
    }
    const requestId = (this.previewRequestId || 0) + 1
    this.previewRequestId = requestId
    try {
      const user = await api.post('/user/preview', { username: name }, { public: true })
      if (requestId !== this.previewRequestId) return
      this.setData({ previewAvatar: user ? avatarUrl(user) : '' })
    } catch (_) {
      if (requestId === this.previewRequestId) this.setData({ previewAvatar: '' })
    }
  },
  async submit() {
    if (this.data.loading) return
    const username = this.data.username.trim(), password = this.data.password
    if (!username || !password) { this.showStatus('请输入用户名或邮箱和密码', 'error'); return }
    this.setData({ loading: true })
    try {
      const user = await api.post('/user/login', { username, password }, { public: true })
      if (!user?.id || !user?.token) throw new Error('登录结果不完整，请稍后重试')
      session.set(user)
      this.saveRememberedUsername()
      this.showStatus('登录成功，正在进入生活助手...')
      setTimeout(() => wx.switchTab({ url: '/pages/home/index' }), 260)
    } catch (error) {
      this.showStatus(error.message || '您的帐号已被禁用，请联系王师傅处理', 'error')
    } finally {
      this.setData({ loading: false })
    }
  },
  async guestLogin() {
    if (this.data.loading) return
    this.setData({ loading: true })
    try {
      const user = await api.post('/user/guestLogin', {}, { public: true })
      if (!user?.id || !user?.token) throw new Error('游客登录失败，请稍后重试')
      session.set(user)
      this.showStatus('欢迎随便逛逛～')
      setTimeout(() => wx.switchTab({ url: '/pages/home/index' }), 200)
    } catch (error) {
      this.showStatus(error.message || '当前系统已禁止游客登录，请联系管理员处理', 'error')
    } finally {
      this.setData({ loading: false })
    }
  },
  saveRememberedUsername() {
    try {
      if (this.data.rememberMe) wx.setStorageSync(REMEMBER_USERNAME_KEY, this.data.username.trim())
      else wx.removeStorageSync(REMEMBER_USERNAME_KEY)
    } catch (_) {}
  },
  forgotPassword() {
    wx.navigateTo({ url: '/pages/reset-password/index' })
  },
  register() { wx.navigateTo({ url: '/pages/register/index' }) },
  showStatus(message, type = 'success') { this.setData({ statusText: message, statusType: type }) },
})
