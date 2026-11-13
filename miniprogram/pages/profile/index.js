const session = require('../../services/session')
const api = require('../../services/api')
const {displayMember,isGuest} = require('../../utils/profile')
Page({
  data:{ user:null, profile:null, members:[], membersLoading:false, membersError:'', isGuest:false, canManage:false, unread:0, noticeOpen:false, passwordOpen:false, passwordBusy:false, passwordError:'', oldPassword:'', newPassword:'', confirmPassword:'' },
  onShow() { const user=session.get(); if (!user?.token) { wx.reLaunch({url:'/pages/login/index'}); return } this.getTabBar()?.setData({selected:'profile',hidden:false}); const guest=isGuest(user); this.setData({user,profile:displayMember(user),isGuest:guest,canManage:!guest && (['owner','admin'].includes(user.memberRole) || user.userRole==='super_admin')}); if(!guest)this.loadMembers(); this.refreshUnread(); this.unreadTimer=setInterval(()=>this.refreshUnread(),60000) },
  onHide() { this.stopUnreadRefresh(); this.closeNotifications(); this.closePassword() },
  onUnload() { this.stopUnreadRefresh() },
  stopUnreadRefresh() { if(this.unreadTimer)clearInterval(this.unreadTimer); this.unreadTimer=null },
  async refreshUnread() { try { const count=await api.post('/notification/unreadCount'); this.setData({unread:Math.max(0,Number(count)||0)}) } catch(_) {} },
  openNotifications() { this.setData({noticeOpen:true}); this.getTabBar()?.setData({hidden:true}); wx.hideTabBar({animation:false,fail:()=>{}}) },
  closeNotifications() { if(!this.data.noticeOpen)return; this.setData({noticeOpen:false}); this.getTabBar()?.setData({hidden:false}); wx.showTabBar({animation:false,fail:()=>{}}); this.refreshUnread() },
  add() { wx.navigateTo({url:'/pages/todo-form/index'}) },
  async loadMembers() {
    this.setData({membersLoading:true,membersError:''})
    try {
      const result=await api.post('/user/members')
      if(!Array.isArray(result)) throw new Error('成员数据格式异常')
      const members=result.map(displayMember)
      const self=members.find(member=>member.id===this.data.user?.id)
      this.setData({members,...(self ? {profile:displayMember({...this.data.user,...self})} : {})})
    } catch(error) { this.setData({membersError:error.message || '家庭成员加载失败'}) }
    finally { this.setData({membersLoading:false}) }
  },
  unavailable() { wx.showToast({title:'功能开发中',icon:'none'}) },
  openPassword() {
    if(this.data.isGuest)return
    this.setData({passwordOpen:true,passwordError:'',oldPassword:'',newPassword:'',confirmPassword:''})
    this.getTabBar()?.setData({hidden:true})
    wx.hideTabBar({animation:false,fail:()=>{}})
  },
  closePassword() {
    if(!this.data.passwordOpen || this.data.passwordBusy)return
    this.setData({passwordOpen:false,passwordError:'',oldPassword:'',newPassword:'',confirmPassword:''})
    this.getTabBar()?.setData({hidden:false})
    wx.showTabBar({animation:false,fail:()=>{}})
  },
  onPasswordInput(e) {
    const field=e.currentTarget.dataset.field
    if(['oldPassword','newPassword','confirmPassword'].includes(field))this.setData({[field]:e.detail.value,passwordError:''})
  },
  async submitPassword() {
    if(this.data.passwordBusy || this.data.isGuest)return
    const {oldPassword,newPassword,confirmPassword}=this.data
    let error=''
    if(!oldPassword)error='请输入原密码'
    else if(newPassword.length<8 || newPassword.length>64)error='新密码长度须为 8 至 64 位'
    else if(newPassword!==confirmPassword)error='两次输入的新密码不一致'
    if(error){this.setData({passwordError:error});return}
    this.setData({passwordBusy:true,passwordError:''})
    try {
      await api.post('/user/changePassword',{oldPassword,newPassword})
      session.clear()
      this.setData({passwordOpen:false,oldPassword:'',newPassword:'',confirmPassword:''})
      this.getTabBar()?.setData({hidden:false})
      wx.showTabBar({animation:false,fail:()=>{}})
      wx.showToast({title:'密码已更新，请重新登录',icon:'none'})
      wx.reLaunch({url:'/pages/login/index'})
    } catch(error) { this.setData({passwordError:error.message || '密码修改失败'}) }
    finally { this.setData({passwordBusy:false}) }
  },
  swallow() {},
  logout() { wx.showModal({ title:'退出登录', content:'确定退出当前账号吗？', success:({confirm})=>{ if(confirm){session.clear();this.setData({user:null});wx.reLaunch({url:'/pages/login/index'})} } }) }
})
