const api = require('../../services/api')
const session = require('../../services/session')
const {present} = require('../../utils/todo')
const {repeats} = require('../../utils/todoForm')
Page({
  data:{todo:null,loading:false,error:'',canManage:false,repeatLabel:''},
  onLoad(options) { this.id=options.id || '' },
  onShow() {
    const user=session.get() || {},identity=[user.id,user.householdId].join(':')
    if(this.loadedIdentity && this.loadedIdentity!==identity){this.requestVersion=(this.requestVersion||0)+1;this.setData({todo:null,error:'',loading:false})}
    this.loadedIdentity=identity
    this.load()
  },
  async load() {
    if (!session.get()?.token) { wx.reLaunch({url:'/pages/login/index'}); return }
    if (!this.id) { this.setData({error:'未找到待办'}); return }
    const version=(this.requestVersion||0)+1
    this.requestVersion=version
    this.setData({loading:true,error:''})
    try {
      const todo=await api.post('/todo/getById',{id:this.id})
      if(version!==this.requestVersion)return
      if (!todo) throw new Error('待办不存在或已删除')
      const user=session.get() || {}
      const guest=String(user.userRole || '').toLowerCase()==='guest' || String(user.memberRole || '').toLowerCase()==='guest'
      const canManage=!guest && (user.memberRole==='owner' || user.memberRole==='admin' || user.userRole==='super_admin' || todo.createUserId===user.id || todo.ownerId===user.id || (todo.ownerIds || []).includes(user.id))
      const repeatLabel=todo.repeatType==='CUSTOM_DAYS' && todo.repeatIntervalDays ? `每 ${todo.repeatIntervalDays} 天` : repeats.find(item=>item.value===todo.repeatType)?.label || '不重复'
      this.setData({todo:present(todo),canManage,repeatLabel})
    } catch(error) { if(version===this.requestVersion)this.setData({error:error.message || '待办加载失败'}) }
    finally { if(version===this.requestVersion)this.setData({loading:false}) }
  },
  retry() { this.load() },
  edit() { if (this.data.canManage) wx.navigateTo({url:`/pages/todo-form/index?id=${encodeURIComponent(this.id)}`}) },
  openLedger() { wx.navigateTo({url:`/pages/ledger/index?todoId=${encodeURIComponent(this.id)}`}) }
})

