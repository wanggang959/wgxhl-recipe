const session = require('../../services/session')
const api = require('../../services/api')
const {displayMember,isGuest} = require('../../utils/profile')
Page({
  data:{ user:null, profile:null, members:[], membersPage:0, membersTotal:0, membersLoading:false, membersError:'', guestStatusBusy:false, isGuest:false, canManage:false, unread:0, noticeOpen:false, passwordOpen:false, passwordBusy:false, passwordError:'', oldPassword:'', newPassword:'', confirmPassword:'', wechatBound:null, wechatStatusLoading:false, wechatStatusError:false, wechatOpen:false, wechatBusy:false, wechatPassword:'', wechatError:'' },
  onShow() { const user=session.get(); if (!user?.token) { wx.reLaunch({url:'/pages/login/index'}); return } this.getTabBar()?.setData({selected:'profile',hidden:false}); const guest=isGuest(user); this.setData({user,profile:displayMember(user),isGuest:guest,canManage:!guest && (['owner','admin'].includes(user.memberRole) || user.userRole==='super_admin')}); if(!guest){this.loadMembers(true);this.loadWechatStatus()} else { this.membersRequestVersion=(this.membersRequestVersion||0)+1; this.setData({members:[],membersPage:0,membersTotal:0,membersLoading:false,membersError:''}) } this.checkAdminAccess(); this.refreshUnread(); this.stopUnreadRefresh(); this.unreadTimer=setInterval(()=>this.refreshUnread(),60000) },
  onReachBottom() { if(!this.data.isGuest && !this.data.membersError && this.data.membersPage>0 && this.data.members.length<this.data.membersTotal)this.loadMembers(false) },
  onHide() { this.stopUnreadRefresh(); this.closeNotifications(); this.closePassword(); this.closeWechat(); this.setData({wechatPassword:''}) },
  onUnload() { this.stopUnreadRefresh() },
  stopUnreadRefresh() { if(this.unreadTimer)clearInterval(this.unreadTimer); this.unreadTimer=null },
  async refreshUnread() { try { const count=await api.post('/notification/unreadCount'); this.setData({unread:Math.max(0,Number(count)||0)}) } catch(_) {} },
  openNotifications() { this.setData({noticeOpen:true}); this.getTabBar()?.setData({hidden:true}) },
  closeNotifications() { if(!this.data.noticeOpen)return; this.setData({noticeOpen:false}); this.getTabBar()?.setData({hidden:false}); this.refreshUnread() },
  async loadMembers(reset = false) {
    if(!reset && (this.data.membersLoading || (this.data.membersPage>0 && this.data.members.length>=this.data.membersTotal)))return
    const version=(this.membersRequestVersion||0)+1
    this.membersRequestVersion=version
    const current=reset?1:this.data.membersPage+1
    this.setData({membersLoading:true,membersError:'',...(reset?{members:[],membersPage:0,membersTotal:0}:{})})
    try {
      const result=await api.post('/user/page',{current,size:20})
      if(version!==this.membersRequestVersion)return
      const records=result?.records || []
      const user=this.data.user || {}
      const superAdmin=user.userRole==='super_admin' || user.username==='王师傅'
      const members=records.map(member=>{
        const guest=isGuest(member),builtin=member.username==='王师傅'||member.userRole==='super_admin'
        const canEdit=!guest && (!builtin || superAdmin) && (this.data.canManage || member.id===user.id) && (member.memberRole!=='owner' || member.id===user.id || user.memberRole==='owner' || superAdmin)
        return {...displayMember(member),isGuestAccount:guest,canEdit,canManageStatus:superAdmin && !builtin,canReset:user.memberRole==='owner' && !guest && !builtin && member.id!==user.id,canDelete:this.data.canManage && !guest && !builtin && member.memberRole!=='owner' && member.id!==user.id}
      })
      const self=members.find(member=>member.id===this.data.user?.id)
      const combined=reset?members:this.data.members.concat(members)
      this.setData({members:combined,membersPage:current,membersTotal:records.length?Number(result?.total || 0):combined.length,...(self ? {profile:displayMember({...this.data.user,...self})} : {})})
    } catch(error) { if(version===this.membersRequestVersion)this.setData({membersError:error.message || '家庭成员加载失败'}) }
    finally { if(version===this.membersRequestVersion)this.setData({membersLoading:false}) }
  },
  retryMembers() { this.loadMembers(this.data.membersPage===0) },
  async loadWechatStatus() {
    if(this.data.isGuest)return
    const userId=this.data.user?.id, version=(this.wechatStatusVersion||0)+1
    this.wechatStatusVersion=version
    this.setData({wechatStatusLoading:true,wechatStatusError:false,wechatBound:null})
    try {
      const status=await api.post('/user/wechat/status')
      if(version===this.wechatStatusVersion && this.data.user?.id===userId)this.setData({wechatBound:Boolean(status?.bound)})
    } catch(_) { if(version===this.wechatStatusVersion && this.data.user?.id===userId)this.setData({wechatStatusError:true}) }
    finally { if(version===this.wechatStatusVersion && this.data.user?.id===userId)this.setData({wechatStatusLoading:false}) }
  },
  openWechat() {
    if(this.data.isGuest || this.data.wechatStatusLoading)return
    if(this.data.wechatStatusError || this.data.wechatBound===null){this.loadWechatStatus();return}
    this.setData({wechatOpen:true,wechatPassword:'',wechatError:''})
    this.getTabBar()?.setData({hidden:true})
  },
  closeWechat() {
    if(!this.data.wechatOpen || this.data.wechatBusy)return
    this.setData({wechatOpen:false,wechatPassword:'',wechatError:''})
    this.getTabBar()?.setData({hidden:false})
  },
  onWechatPassword(e) { this.setData({wechatPassword:e.detail.value,wechatError:''}) },
  async submitWechat() {
    if(this.data.wechatBusy || this.data.isGuest || this.data.wechatBound===null)return
    const password=this.data.wechatPassword, bound=this.data.wechatBound
    if(!password){this.setData({wechatError:'请输入当前账号密码'});return}
    this.setData({wechatBusy:true,wechatError:''})
    try {
      if(bound) {
        await api.post('/user/wechat/unbind',{password})
      } else {
        const code=await new Promise((resolve,reject)=>wx.login({success:r=>r.code?resolve(r.code):reject(new Error('获取微信登录凭证失败')),fail:()=>reject(new Error('微信登录暂不可用，请稍后重试'))}))
        await api.post('/user/wechat/bind',{code,username:this.data.user.username,password})
      }
      this.wechatStatusVersion=(this.wechatStatusVersion||0)+1
      this.setData({wechatBound:!bound,wechatOpen:false,wechatPassword:'',wechatError:'',wechatStatusLoading:false,wechatStatusError:false})
      this.getTabBar()?.setData({hidden:false})
      wx.showToast({title:bound?'已解除绑定':'微信绑定成功',icon:'none'})
    } catch(error) { this.setData({wechatError:error.message || '操作失败，请稍后重试'}) }
    finally { this.setData({wechatBusy:false}) }
  },
  async checkAdminAccess() { const user=this.data.user;this.setData({canAccessAdmin:false});if(!user?.token || !(user.userRole==='super_admin'||user.username==='王师傅'))return;try{const allowed=await api.get('/platform-admin/access');if(this.data.user?.id===user.id)this.setData({canAccessAdmin:Boolean(allowed)})}catch(_){} },
  openAdmin() { if(this.data.canAccessAdmin)wx.navigateTo({url:'/pages/platform-admin/index'}) },
  addRecipe() { if(!this.data.isGuest)wx.navigateTo({url:'/pages/recipe-form/index'}) },
  openEmailSecurity() { if(!this.data.isGuest)wx.navigateTo({url:'/pages/email-security/index'}) },
  openHousehold() { if(!this.data.isGuest)wx.navigateTo({url:'/pages/household/index'}) },
  openCommute() { if(!this.data.isGuest)wx.navigateTo({url:'/pages/commute/index'}) },
  openExpense() { if(!this.data.isGuest)wx.navigateTo({url:'/pages/expense/index'}) },
  openLedger() { if(!this.data.isGuest)wx.navigateTo({url:'/pages/ledger/index'}) },
  openBaseManage() { if(!this.data.isGuest)wx.navigateTo({url:'/pages/base-manage/index'}) },
  addMember() { if(this.data.canManage)wx.navigateTo({url:'/pages/member-form/index'}) },
  editMember(e) {
    const member=this.data.members.find(item=>item.id===e.currentTarget.dataset.id)
    const user=this.data.user || {}
    if(!member || isGuest(member) || (member.userRole==='super_admin' && user.userRole!=='super_admin') || (!this.data.canManage && member.id!==user.id))return
    wx.navigateTo({url:`/pages/member-form/index?id=${encodeURIComponent(member.id)}`})
  },
  resetMemberPassword(e) {
    const member=this.data.members.find(item=>item.id===e.currentTarget.dataset.id)
    if(member?.canReset && this.data.user?.memberRole==='owner')wx.navigateTo({url:`/pages/member-reset/index?id=${encodeURIComponent(member.id)}`})
  },
  toggleGuestStatus(e) {
    const user=this.data.user || {}
    const member=this.data.members.find(item=>item.id===e.currentTarget.dataset.id)
    if(this.data.guestStatusBusy || !member || !isGuest(member) || !(user.userRole==='super_admin' || user.username==='王师傅'))return
    const disabled=member.status==='disabled'
    const status=disabled?'normal':'disabled'
    this.setData({guestStatusBusy:true})
    wx.showModal({
      title:disabled?'启用游客入口':'禁用游客入口',
      content:disabled?'确认恢复游客登录吗？这会影响整个系统的游客入口。':'确认禁止游客登录吗？这会影响整个系统的游客入口。',
      confirmText:disabled?'启用':'禁用',
      confirmColor:disabled?'#f47716':'#d64335',
      success:async ({confirm})=>{
        if(!confirm){this.setData({guestStatusBusy:false});return}
        try {
          await api.post('/user/setStatus',{id:member.id,status})
          const members=this.data.members.map(item=>item.id===member.id?{...item,status,statusLabel:disabled?'正常':'已禁用'}:item)
          this.setData({members})
          wx.showToast({title:disabled?'游客入口已启用':'游客入口已禁用',icon:'none'})
        } catch(error) { wx.showToast({title:error.message || '操作失败',icon:'none'}) }
        finally { this.setData({guestStatusBusy:false}) }
      },
      fail:()=>this.setData({guestStatusBusy:false})
    })
  },
  toggleMemberStatus(e) {
    const user=this.data.user || {}
    const member=this.data.members.find(item=>item.id===e.currentTarget.dataset.id)
    if(this.data.guestStatusBusy || !member || isGuest(member) || member.username==='王师傅' || member.userRole==='super_admin' || !(user.userRole==='super_admin' || user.username==='王师傅'))return
    const disabled=member.status==='disabled',status=disabled?'normal':'disabled'
    this.setData({guestStatusBusy:true})
    wx.showModal({title:disabled?'恢复登录':'禁止登录',content:disabled?`确认恢复「${member.nickname||member.username}」的登录权限吗？`:`确认禁止「${member.nickname||member.username}」登录吗？`,confirmText:disabled?'启用':'禁用',confirmColor:disabled?'#f47716':'#d64335',
      success:async ({confirm})=>{
        if(!confirm){this.setData({guestStatusBusy:false});return}
        try{await api.post('/user/setStatus',{id:member.id,status});this.setData({members:this.data.members.map(item=>item.id===member.id?{...item,status,statusLabel:disabled?'正常':'已禁用'}:item)});wx.showToast({title:disabled?'已启用':'已禁用',icon:'none'})}
        catch(error){wx.showToast({title:error.message||'操作失败',icon:'none'})}
        finally{this.setData({guestStatusBusy:false})}
      },fail:()=>this.setData({guestStatusBusy:false})})
  },
  removeMember(e) {
    const member=this.data.members.find(item=>item.id===e.currentTarget.dataset.id)
    if(!member || !member.canDelete || !this.data.canManage || this.data.memberDeleteBusy)return
    this.setData({memberDeleteBusy:true})
    wx.showModal({title:'删除成员',content:`确认删除「${member.nickname||member.username}」吗？`,confirmText:'删除',confirmColor:'#d64335',
      success:async ({confirm})=>{
        if(!confirm){this.setData({memberDeleteBusy:false});return}
        try{await api.post('/user/delete',{id:member.id});wx.showToast({title:'已删除',icon:'none'});await this.loadMembers(true)}
        catch(error){wx.showToast({title:error.message||'删除失败',icon:'none'})}
        finally{this.setData({memberDeleteBusy:false})}
      },fail:()=>this.setData({memberDeleteBusy:false})})
  },
  openPassword() {
    if(this.data.isGuest)return
    this.setData({passwordOpen:true,passwordError:'',oldPassword:'',newPassword:'',confirmPassword:''})
    this.getTabBar()?.setData({hidden:true})
  },
  closePassword() {
    if(!this.data.passwordOpen || this.data.passwordBusy)return
    this.setData({passwordOpen:false,passwordError:'',oldPassword:'',newPassword:'',confirmPassword:''})
    this.getTabBar()?.setData({hidden:false})
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
      wx.showToast({title:'密码已更新，请重新登录',icon:'none'})
      wx.reLaunch({url:'/pages/login/index'})
    } catch(error) { this.setData({passwordError:error.message || '密码修改失败'}) }
    finally { this.setData({passwordBusy:false}) }
  },
  swallow() {},
  logout() { wx.showModal({ title:'退出登录', content:'确定退出当前账号吗？', success:({confirm})=>{ if(confirm){session.clear();this.setData({user:null});wx.reLaunch({url:'/pages/login/index'})} } }) }
})
