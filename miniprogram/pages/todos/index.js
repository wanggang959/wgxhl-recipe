const api = require('../../services/api')
const session = require('../../services/session')
const { isGuest } = require('../../utils/profile')
const { present } = require('../../utils/todo')
const { groupItems } = require('../../utils/todoList')
async function allPages(path, query = {}) {
  let current = 1, records = [], total = 0
  do {
    const result = await api.post(path, { ...query, current, size:50 })
    const page = result?.records || []
    records = records.concat(page); total = Number(result?.total || 0); current++
    if (!page.length) break
  } while (records.length < total)
  return records
}
Page({
  data:{ loading:false, loadingMore:false, hasSnapshot:false, error:'', items:[], summary:null, page:0, total:0, plans:[], planPage:0, planTotal:0, planError:'', planLoading:false, groups:[], category:'', range:'all', todayCount:0, dueSoonCount:0, unread:0, noticeOpen:false, ledgerTodo:null,
    ranges:[{value:'all',label:'全部'},{value:'today',label:'今日'},{value:'three',label:'近三天'},{value:'week',label:'近一周'}],
    categories:[{value:'',label:'全部'},{value:'COOK',label:'做饭'},{value:'GROCERY',label:'采购'},{value:'LIFE',label:'生活'},{value:'BIRTHDAY',label:'生日'},{value:'SERVER',label:'服务器'},{value:'DOMAIN',label:'域名'},{value:'PAYMENT',label:'缴费'},{value:'OTHER',label:'其他'}] },
  updateGroups() { this.setData(groupItems(this.data.items, this.data.plans, this.data.category, this.data.range)) },
  chooseRange(e) { this.setData({range:e.currentTarget.dataset.value}); this.updateGroups() },
  chooseCategory(e) { this.setData({category:e.currentTarget.dataset.value}); this.updateGroups() },
  onShow() {
    const user=session.get()
    if (!user?.token) { wx.reLaunch({ url:'/pages/login/index' }); return }
    if (isGuest(user)) { wx.switchTab({ url:'/pages/home/index' }); return }
    const identity=[user.id,user.householdId].join(':')
    if(this.loadedIdentity!==identity){
      this.loadedIdentity=identity
      this.todoRequestVersion=(this.todoRequestVersion||0)+1
      this.planRequestVersion=(this.planRequestVersion||0)+1
      this.setData({hasSnapshot:false,loading:false,loadingMore:false,planLoading:false,items:[],plans:[],groups:[],summary:null,page:0,total:0,planPage:0,planTotal:0,error:'',planError:''})
    }
    this.getTabBar()?.setData({selected:'todos',hidden:false})
    this.refreshAll()
    this.refreshUnread(); this.stopUnreadRefresh(); this.unreadTimer=setInterval(()=>this.refreshUnread(),60000)
  },
  onHide() { this.stopUnreadRefresh(); this.closeNotifications() },
  onUnload() { this.stopUnreadRefresh() },
  stopUnreadRefresh() { if (this.unreadTimer) clearInterval(this.unreadTimer); this.unreadTimer = null },
  async refreshUnread() { try { const count = await api.post('/notification/unreadCount'); this.setData({unread:Math.max(0, Number(count) || 0)}) } catch (_) {} },
  openNotifications() { this.setData({noticeOpen:true}); this.getTabBar()?.setData({hidden:true}) },  closeNotifications() { if (!this.data.noticeOpen) return; this.setData({noticeOpen:false}); this.getTabBar()?.setData({hidden:false}); this.refreshUnread() },
  openSummary() { wx.navigateTo({url:'/pages/todo-summary/index?range=today'}) },
  async refreshAll() {
    const identity=this.loadedIdentity
    await Promise.all([this.load(true),this.loadPlans(true)])
    if(this.loadedIdentity===identity && !this.data.loading && !this.data.planLoading && !this.data.error && !this.data.planError)this.setData({hasSnapshot:true})
  },
  onPullDownRefresh() { this.refreshAll().finally(() => wx.stopPullDownRefresh()) },
  onReachBottom() { if (this.data.items.length < this.data.total) this.load(false) },
  async load(reset) {
    if (!reset && (this.data.loading || this.data.loadingMore)) return
    const version=(this.todoRequestVersion||0)+1
    this.todoRequestVersion=version
    this.setData(reset ? { loading:true, error:'' } : { loadingMore:true })
    const page = reset ? 1 : this.data.page + 1
    try {
      const requests = [allPages('/todo/page')]
      if (reset) requests.push(api.post('/todo/summary'))
      const [result, summary] = await Promise.all(requests)
      if(version!==this.todoRequestVersion)return
      const user=session.get() || {}
      const items = result.map(todo => ({ ...present(todo), canManage: user.memberRole === 'owner' || user.memberRole === 'admin' || user.userRole === 'super_admin' || todo.createUserId === user.id || todo.ownerId === user.id || (todo.ownerIds || []).includes(user.id) }))
      this.setData({ items, page, total:items.length, ...(reset ? { summary } : {}) }); this.updateGroups()
    } catch (error) {
      if(version!==this.todoRequestVersion)return
      this.setData({ error:error.message || '加载失败' })
      if (!session.get()?.token) wx.reLaunch({ url:'/pages/login/index' })
    } finally { if(version===this.todoRequestVersion)this.setData({ loading:false, loadingMore:false }) }
  },
  retry() { this.refreshAll() },
  async loadPlans(reset) {
    if (!reset && this.data.planLoading) return
    const version=(this.planRequestVersion||0)+1
    this.planRequestVersion=version
    this.setData({planLoading:true,planError:''})
    const page=reset?1:this.data.planPage+1, d=new Date(), today=`${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`
    try {
      const [result,members]=await Promise.all([allPages('/want/page',{plannedDateStart:today}),api.post('/user/members')])
      if(version!==this.planRequestVersion)return
      const names=Object.fromEntries((members || []).map(m=>[m.id,m.nickname || m.username || '家人']))
      const plans=result.map(item=>({...item,displayOwner:names[item.userId] || '家人'}))
      this.setData({plans,planPage:page,planTotal:plans.length}); this.updateGroups()
    } catch(error) { if(version===this.planRequestVersion)this.setData({planError:error.message || '做饭计划加载失败'}) }
    finally { if(version===this.planRequestVersion)this.setData({planLoading:false}) }
  },
  morePlans(){this.loadPlans(false)},
  finishPlan(e){
    const item=this.data.plans.find(plan=>plan.id===e.currentTarget.dataset.id)
    if(!item || this.data.actionBusy)return
    wx.showModal({title:'完成做饭计划',content:`确认完成「${item.recipeName}」吗？`,confirmText:'完成',success:async ({confirm})=>{
      if(!confirm)return
      this.setData({actionBusy:true})
      try{await api.post('/want/delete',{id:item.id});wx.showToast({title:'已完成'});await this.loadPlans(true)}
      catch(error){wx.showToast({title:error.message || '操作失败',icon:'none'})}
      finally{this.setData({actionBusy:false})}
    }})
  },
  add() { wx.navigateTo({url:'/pages/todo-form/index'}) },
  openDetail(e) { wx.navigateTo({url:`/pages/todo-detail/index?id=${encodeURIComponent(e.currentTarget.dataset.id)}`}) },
  edit(e) { wx.navigateTo({url:`/pages/todo-form/index?id=${encodeURIComponent(e.currentTarget.dataset.id)}`}) },
  finish(e) {
    const item=this.data.items.find(entry=>entry.id===e.currentTarget.dataset.id)
    if (!item || this.data.actionBusy) return
    if (item.canComplete === false) { wx.showToast({title:item.completeDisabledReason || '暂时不能完成',icon:'none'}); return }
    if (item.ledgerSetting?.enabled) { this.setData({ledgerTodo:item}); this.getTabBar()?.setData({hidden:true}); return }
    const repeat=item.category === 'BIRTHDAY' || (item.repeatType && item.repeatType !== 'NONE')
    wx.showModal({title:repeat ? '进入下一周期' : '填写下个周期',content:repeat ? `确认完成「${item.title}」并进入下一周期吗？` : `确认处理「${item.title}」并填写下一次到期时间吗？`,confirmText:'确认',success:async ({confirm})=>{
      if (!confirm) return
      if (!repeat) { wx.navigateTo({url:`/pages/todo-form/index?id=${encodeURIComponent(item.id)}&nextCycle=1`}); return }
      this.setData({actionBusy:true})
      try { await api.post('/todo/complete',{id:item.id}); wx.showToast({title:'已进入下一周期'}); await this.load(true) }
      catch(error) { wx.showToast({title:error.message || '操作失败',icon:'none'}) }
      finally { this.setData({actionBusy:false}) }
    }})
  },
  closeLedger(){this.setData({ledgerTodo:null});this.getTabBar()?.setData({hidden:false})},
  ledgerDone(){this.closeLedger();this.refreshAll()},
  remove(e) {
    const item=this.data.items.find(entry=>entry.id===e.currentTarget.dataset.id)
    if (!item || this.data.actionBusy) return
    wx.showModal({title:'删除待办',content:item.ledgerSetting?`确认删除「${item.title}」吗？已产生的家庭账单会保留。`:`确认删除「${item.title}」吗？`,confirmText:'删除',confirmColor:'#d64335',success:async ({confirm})=>{
      if (!confirm) return
      this.setData({actionBusy:true})
      try { await api.post('/todo/delete',{id:item.id}); wx.showToast({title:'已删除'}); await this.load(true) }
      catch(error) { wx.showToast({title:error.message || '删除失败',icon:'none'}) }
      finally { this.setData({actionBusy:false}) }
    }})
  }
})
