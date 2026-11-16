const api = require('../../services/api')
const session = require('../../services/session')
const config = require('../../config')
const { isGuest } = require('../../utils/profile')
const kinds = { category: '分类', ingredient: '食材', seasoning: '调料' }
const imageBase = 'https://wg-recipe-1385696563.cos.ap-chengdu.myqcloud.com/'
function imageUrl(path) {
  const value = String(path || '').trim()
  if (/^https:\/\//i.test(value)) return value
  return value ? imageBase + value.replace(/^\/+/, '') : ''
}
function present(item, kind, superAdmin) {
  return { ...item, name: item[kind + 'Name'], description: item[kind + 'Desc'] || '', imageUrl: imageUrl(item[kind + 'Image']), scopeLabel: item.scope === 'platform' ? '公共' : '我的', canManage: item.scope !== 'platform' || superAdmin }
}
Page({
  data: { kind: 'category', kindLabel: '分类', items: [], page: 0, total: 0, loading: false, error: '', keyword: '', appliedKeyword: '', formOpen: false, form: {}, saving: false, uploading: false, formError: '', superAdmin: false },
  onLoad() {
    const user = session.get()
    if (!user?.token) { wx.reLaunch({ url: '/pages/login/index' }); return }
    if (isGuest(user)) { wx.navigateBack(); return }
    this.setData({ superAdmin: user.userRole === 'super_admin' || user.username === '王师傅' })
    this.load(true)
  },
  onUnload() { this.requestVersion = (this.requestVersion || 0) + 1 },
  onReachBottom() { if (!this.data.error && this.data.page && this.data.items.length < this.data.total) this.load(false) },
  async load(reset) {
    if (!reset && (this.data.loading || this.data.items.length >= this.data.total)) return
    const version = (this.requestVersion || 0) + 1
    this.requestVersion = version
    const kind = this.data.kind, current = reset ? 1 : this.data.page + 1
    const keyword = reset ? this.data.keyword.trim() : this.data.appliedKeyword
    if (reset) this.deletedSinceLoad = 0
    this.setData({ loading: true, error: '', ...(reset ? { items: [], page: 0, total: 0, appliedKeyword: keyword } : {}) })
    try {
      const start = !reset && this.deletedSinceLoad ? Math.max(1, this.data.page - Math.floor(this.deletedSinceLoad / 50)) : current
      let data, records = []
      for (let page = start; page <= current; page++) {
        data = await api.post('/' + kind + '/page', { current: page, size: 50, ...(kind !== 'category' && keyword ? { [kind + 'Name']: keyword } : {}) })
        if (version !== this.requestVersion) return
        records = records.concat((data?.records || []).map(item => present(item, kind, this.data.superAdmin)))
      }
      if (version !== this.requestVersion) return
      const items = reset ? records : this.data.items.slice()
      if (!reset) { const seen = new Set(items.map(item => item.id)); records.forEach(item => { if (!seen.has(item.id)) { seen.add(item.id); items.push(item) } }) }
      this.deletedSinceLoad = 0
      this.setData({ items, page: current, total: Number(data?.total ?? items.length) })
    } catch (error) { if (version === this.requestVersion) this.setData({ error: error.message || '加载失败' }) }
    finally { if (version === this.requestVersion) this.setData({ loading: false }) }
  },
  switchKind(e) { const kind = e.currentTarget.dataset.kind; if (!kinds[kind] || kind === this.data.kind) return; this.setData({ kind, kindLabel: kinds[kind], keyword: '' }); this.load(true) },
  inputKeyword(e) { this.setData({ keyword: e.detail.value }) },
  search() { this.load(true) },
  clearSearch() { this.setData({ keyword: '' }); this.load(true) },
  retry() { this.load(this.data.page === 0) },
  add() { this.setData({ formOpen: true, form: { name: '', code: '', sortNo: '0', description: '', image: '' }, formImageUrl: '', formError: '' }) },
  edit(e) { const item = this.data.items.find(v => v.id === e.currentTarget.dataset.id); if (!item || !item.canManage) return; const k = this.data.kind; this.setData({ formOpen: true, form: { id: item.id, name: item[k + 'Name'] || '', code: item.categoryCode || '', sortNo: String(item.sortNo ?? 0), description: item[k + 'Desc'] || '', image: item[k + 'Image'] || '' }, formImageUrl: item.imageUrl, formError: '' }) },
  closeForm() { if (!this.data.saving && !this.data.uploading) this.setData({ formOpen: false, formError: '' }) },
  inputForm(e) { const field = e.currentTarget.dataset.field; if (['name','code','sortNo','description'].includes(field)) this.setData({ ['form.' + field]: e.detail.value, formError: '' }) },
  chooseImage() {
    if (this.data.uploading || this.data.saving) return
    wx.chooseMedia({ count: 1, mediaType: ['image'], sizeType: ['compressed'], sourceType: ['album', 'camera'], success: result => { const file = result.tempFiles?.[0]; if (file?.tempFilePath) this.uploadImage(file.tempFilePath) } })
  },
  uploadImage(filePath) {
    const user = session.get(), kind = this.data.kind
    if (!user?.token || !['ingredient','seasoning'].includes(kind)) return
    this.setData({ uploading: true, formError: '' })
    wx.uploadFile({ url: config.apiBaseUrl.replace(/\/+$/, '') + '/file/upload', filePath, name: 'file', formData: { folder: kind }, header: { Authorization: 'Bearer ' + user.token }, timeout: 30000,
      success: response => {
        let body
        try { body = JSON.parse(response.data) } catch (_) { this.setData({ formError: '图片上传失败，请重试' }); return }
        if (response.statusCode === 401 || body.status === 401) { session.clear(); this.setData({ formError: '登录已失效，请重新登录' }); return }
        if (response.statusCode < 200 || response.statusCode >= 300 || body.status !== 200 || typeof body.data !== 'string') { this.setData({ formError: body.message || '图片上传失败' }); return }
        this.setData({ 'form.image': body.data, formImageUrl: imageUrl(body.data) })
      },
      fail: error => this.setData({ formError: /domain list|合法域名/i.test(error.errMsg || '') ? '上传域名未配置，请在微信后台配置 uploadFile 合法域名' : '图片上传失败，请检查网络后重试' }),
      complete: () => this.setData({ uploading: false })
    })
  },
  removeImage() { if (!this.data.uploading) this.setData({ 'form.image': '', formImageUrl: '' }) },
  async save() {
    if (this.data.saving || this.data.uploading) return
    const { kind, form } = this.data, name = form.name.trim()
    if (!name) { this.setData({ formError: '请输入' + kinds[kind] + '名称' }); return }
    if (kind === 'category' && form.sortNo !== '' && !/^-?\d+$/.test(form.sortNo)) { this.setData({ formError: '排序号请输入整数' }); return }
    const payload = { ...(form.id ? { id: form.id } : {}), [kind + 'Name']: name, [kind + 'Desc']: form.description.trim(), ...(kind === 'category' ? { categoryCode: form.code.trim(), sortNo: Number(form.sortNo || 0) } : { [kind + 'Image']: form.image }) }
    this.setData({ saving: true, formError: '' })
    try { await api.post('/' + kind + '/' + (form.id ? 'update' : 'create'), payload); this.setData({ formOpen: false }); wx.showToast({ title: '保存成功', icon: 'success' }); await this.load(true) }
    catch (error) { this.setData({ formError: error.message || '保存失败' }) }
    finally { this.setData({ saving: false }) }
  },
  remove(e) {
    const item = this.data.items.find(v => v.id === e.currentTarget.dataset.id), kind = this.data.kind
    if (!item?.canManage || this.deleteBusy) return
    this.deleteBusy = true
    wx.showModal({ title: '删除' + kinds[kind], content: '确认删除「' + item.name + '」吗？', confirmText: '删除', confirmColor: '#d64335', success: async ({ confirm }) => {
      if (!confirm) { this.deleteBusy = false; return }
      try {
        await api.post('/' + kind + '/delete', { id: item.id })
        if (this.data.kind === kind && this.data.items.some(value => value.id === item.id)) {
          this.requestVersion = (this.requestVersion || 0) + 1
          this.deletedSinceLoad = (this.deletedSinceLoad || 0) + 1
          this.setData({ items: this.data.items.filter(value => value.id !== item.id), total: Math.max(0, this.data.total - 1), loading: false, error: '' })
        }
        wx.showToast({ title: '已删除', icon: 'success' })
      }
      catch (error) { wx.showToast({ title: error.message || '删除失败', icon: 'none' }) }
      finally { this.deleteBusy = false }
    }, fail: () => { this.deleteBusy = false } })
  },
  swallow() {}
})
