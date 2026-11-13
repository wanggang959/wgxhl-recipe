const api = require('../../services/api')
const session = require('../../services/session')
Page({
  data: { username:'', password:'', loading:false, error:'' },
  onLoad() { if (session.get()?.token) wx.switchTab({ url:'/pages/todos/index' }) },
  onUsername(e) { this.setData({ username:e.detail.value, error:'' }) },
  onPassword(e) { this.setData({ password:e.detail.value, error:'' }) },
  async submit() {
    if (this.data.loading) return
    const username = this.data.username.trim(), password = this.data.password
    if (!username || !password) { this.setData({ error:'请输入用户名或邮箱和密码' }); return }
    this.setData({ loading:true, error:'' })
    try {
      const user = await api.post('/user/login', { username, password }, { public:true })
      if (!user?.id || !user?.token) throw new Error('登录结果不完整，请稍后重试')
      session.set(user); this.setData({ password:'' }); wx.switchTab({ url:'/pages/todos/index' })
    } catch (error) { this.setData({ error:error.message || '登录失败' }) }
    finally { this.setData({ loading:false }) }
  }
})
