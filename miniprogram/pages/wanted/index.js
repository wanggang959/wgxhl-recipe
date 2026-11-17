const api=require('../../services/api')
const session=require('../../services/session')
const {isGuest}=require('../../utils/profile')
const {imageUrl}=require('../../utils/recipe')
const {dateText,addDays,formatDateLabel,isPastDate,dayLabel}=require('../../utils/date')
const refresh=require('../../utils/refresh')
const DEFAULT_NOTIFY_USER_ID='admin-wangshifu'
Page({
  data:{user:null,guest:false,today:'',dateTabs:[],selectedDate:'',list:[],page:0,total:0,hasWanted:false,loading:false,loadingMore:false,error:'',editOpen:false,editTarget:null,editDate:'',busy:false,notifyOpen:false,notifyPreview:'',notifyMembers:[],selectedNotifyIds:[],notifyBusy:false,unread:0,noticeOpen:false},
  onLoad(){
    const user=session.get()
    if(!user?.token){wx.reLaunch({url:'/pages/login/index'});return}
    this.user=user
    const today=dateText()
    this.setData({guest:isGuest(user),today,selectedDate:today,user})
  },
  onShow(){
    this.getTabBar()?.setData({selected:'wanted',hidden:false})
    this.refreshUnread()
    this.stopUnreadTimer()
    this.unreadTimer=setInterval(()=>this.refreshUnread(),60000)
    if(this.data.guest)return
    const dirty=refresh.version('want')
    if(!this.loadedAt||dirty!==this.seenDirty||Date.now()-this.loadedAt>60000)this.refresh()
  },
  onPullDownRefresh(){if(this.data.guest){wx.stopPullDownRefresh();return}this.refresh().finally(()=>wx.stopPullDownRefresh())},
  onHide(){
    this.stopUnreadTimer()
    this.closeNotifications()
    if(this.data.editOpen||this.data.notifyOpen){
      this.setData({editOpen:false,notifyOpen:false,editTarget:null})
      this.getTabBar()?.setData({hidden:false})
    }
  },
  onUnload(){this.stopUnreadTimer()},
  stopUnreadTimer(){if(this.unreadTimer)clearInterval(this.unreadTimer);this.unreadTimer=null},
  async refreshUnread(){try{const value=await api.post('/notification/unreadCount');this.setData({unread:Math.max(0,Number(value)||0)})}catch(_){}},
  openNotifications(){this.setData({noticeOpen:true});this.getTabBar()?.setData({hidden:true})},
  closeNotifications(){if(!this.data.noticeOpen)return;this.setData({noticeOpen:false});this.getTabBar()?.setData({hidden:false});this.refreshUnread()},
  add(){wx.navigateTo({url:'/pages/recipe-form/index'})},
  onReachBottom(){if(!this.data.guest&&!this.data.error&&this.data.page&&this.data.list.length<this.data.total)this.loadList(false)},
  async refresh(){
    if(this.data.guest)return
    await this.loadDates()
    await this.loadList(true)
  },
  async loadDates(){
    try{
      const dates=await api.post('/want/dateList',{userId:this.user.id})
      const today=this.data.today,tomorrow=addDays(1,new Date(`${today}T00:00:00`))
      const wantedDates=[...new Set((Array.isArray(dates)?dates:[])
        .map(item=>String(item).slice(0,10))
        .filter(item=>item&&!isPastDate(item,today)))]
      const extra=wantedDates.filter(item=>item!==today&&item!==tomorrow).sort()
      const tabs=[{value:today,label:'今日'},{value:tomorrow,label:'明日'}].concat(extra.map(item=>({value:item,label:formatDateLabel(item)})))
      let selectedDate=this.data.selectedDate
      if(!tabs.some(item=>item.value===selectedDate))selectedDate=today
      this.wantedDates=wantedDates
      this.setData({dateTabs:tabs,selectedDate,hasWanted:wantedDates.length>0})
    }catch(error){
      this.setData({error:error.message||'想吃日期加载失败'})
    }
  },
  async loadList(reset){
    if(!reset&&(this.data.loading||this.data.loadingMore))return
    const version=(this.requestVersion||0)+1
    this.requestVersion=version
    const current=reset?1:this.data.page+1
    this.setData({...(reset?{loading:true,error:'',list:[],page:0,total:0}:{loadingMore:true,error:''})})
    try{
      const data=await api.post('/want/page',{current,size:20,userId:this.user.id,plannedDate:this.data.selectedDate})
      if(version!==this.requestVersion)return
      const records=(data?.records||[]).map(item=>({...item,coverUrl:imageUrl(item.coverImage),plannedLabel:formatDateLabel(item.plannedDate)}))
      const list=reset?records:this.data.list.concat(records)
      this.seenDirty=refresh.version('want')
      this.loadedAt=Date.now()
      this.setData({list,page:current,total:records.length?Number(data?.total||0):list.length,hasWanted:this.data.hasWanted||records.length>0})
    }catch(error){if(version===this.requestVersion)this.setData({error:error.message||'想吃列表加载失败'})}
    finally{if(version===this.requestVersion)this.setData({loading:false,loadingMore:false})}
  },
  retry(){this.refresh()},
  pickDate(e){
    const value=e.currentTarget.dataset.value
    if(!value||value===this.data.selectedDate)return
    this.setData({selectedDate:value,list:[],page:0,total:0})
    return this.loadList(true)
  },
  openRecipe(e){const id=e.currentTarget.dataset.id;if(id)wx.navigateTo({url:'/pages/recipe-detail/index?id='+encodeURIComponent(id)})},
  openEdit(e){
    const item=this.data.list.find(entry=>entry.id===e.currentTarget.dataset.id)
    if(!item)return
    this.hideTabBar()
    this.setData({editOpen:true,editTarget:item,editDate:item.plannedDate||this.data.today})
  },
  closeEdit(){if(!this.data.busy){this.setData({editOpen:false,editTarget:null});this.showTabBar()}},
  hideTabBar(){this.getTabBar()?.setData({hidden:true})},
  showTabBar(){if(this.data.notifyOpen||this.data.editOpen)return;this.getTabBar()?.setData({hidden:false})},
  changeEditDate(e){this.setData({editDate:e.detail.value})},
  async submitEdit(){
    const target=this.data.editTarget
    if(!target||this.data.busy)return
    if(!this.data.editDate||isPastDate(this.data.editDate,this.data.today)){
      wx.showToast({title:'请选择今天或之后的日期',icon:'none'});return
    }
    this.setData({busy:true})
    try{
      await api.post('/want/updateDate',{id:target.id,plannedDate:this.data.editDate})
      refresh.markDirty('want');refresh.markDirty('todo')
      wx.showToast({title:'已修改想吃日期',icon:'none'})
      this.setData({editOpen:false,editTarget:null})
      this.showTabBar()
      this.loadedAt=0
      await this.refresh()
    }catch(error){wx.showToast({title:error.message||'修改失败',icon:'none'})}
    finally{this.setData({busy:false})}
  },
  remove(e){
    const item=this.data.list.find(entry=>entry.id===e.currentTarget.dataset.id)
    if(!item||this.data.busy)return
    wx.showModal({title:'移除想吃',content:`确认把「${item.recipeName||'这道菜'}」从想吃清单移除吗？`,confirmText:'移除',confirmColor:'#d64335',success:async({confirm})=>{
      if(!confirm||this.data.busy)return
      this.setData({busy:true})
      try{
        await api.post('/want/delete',{id:item.id})
        const list=this.data.list.filter(entry=>entry.id!==item.id)
        const wantedDates=(this.wantedDates||[]).filter(date=>date!==this.data.selectedDate||list.length>0)
        this.wantedDates=wantedDates
        this.setData({list,total:Math.max(0,this.data.total-1),hasWanted:wantedDates.length>0||list.length>0})
        refresh.markDirty('want');refresh.markDirty('todo')
        wx.showToast({title:'已从想吃移除',icon:'none'})
      }catch(error){wx.showToast({title:error.message||'移除失败',icon:'none'})}
      finally{this.setData({busy:false})}
    }})
  },
  async openNotify(){
    if(this.data.notifyBusy)return
    if(!this.data.hasWanted){wx.showToast({title:'今天及之后还没有想吃的菜',icon:'none'});return}
    let preview=''
    try{
      const data=await api.post('/want/notifyPreview')
      preview=data?.previewBody||''
    }catch(_){}
    if(!preview){
      const names=this.data.list.map(item=>item.recipeName).filter(Boolean)
      preview=names.length?`${this.notifyActor()}${dayLabel(this.data.selectedDate,this.data.today)}想吃${names.slice(0,3).join('、')}，点击前往备菜`:''
    }
    if(!preview){wx.showToast({title:'今天及之后还没有想吃的菜',icon:'none'});return}
    try{
      const members=await api.post('/user/members')
      const rows=(Array.isArray(members)?members:[]).filter(item=>!isGuest(item))
      if(!rows.length){wx.showToast({title:'暂无家庭成员',icon:'none'});return}
      const selectedNotifyIds=rows.some(item=>item.id===DEFAULT_NOTIFY_USER_ID)?[DEFAULT_NOTIFY_USER_ID]:[rows[0].id]
      this.hideTabBar()
      this.setData({notifyOpen:true,notifyPreview:preview,notifyMembers:rows.map(item=>({...item,label:item.nickname||item.username||'家人',selected:selectedNotifyIds.includes(item.id)})),selectedNotifyIds})
    }catch(error){wx.showToast({title:error.message||'家人列表加载失败',icon:'none'})}
  },
  notifyActor(){const user=this.user||{};return user.nickname||user.username||'家人'},
  closeNotify(){if(!this.data.notifyBusy){this.setData({notifyOpen:false});this.showTabBar()}},
  toggleNotifyMember(e){
    const id=e.currentTarget.dataset.id
    if(!id||this.data.notifyBusy)return
    const selected=this.data.selectedNotifyIds.includes(id)
    const selectedNotifyIds=selected?this.data.selectedNotifyIds.filter(item=>item!==id):this.data.selectedNotifyIds.concat(id)
    this.setData({selectedNotifyIds,notifyMembers:this.data.notifyMembers.map(item=>({...item,selected:selectedNotifyIds.includes(item.id)}))})
  },
  async submitNotify(){
    if(this.data.notifyBusy)return
    if(!this.data.selectedNotifyIds.length){wx.showToast({title:'请至少选择一位家人',icon:'none'});return}
    this.setData({notifyBusy:true})
    try{
      await api.post('/want/notify',{targetUserIds:this.data.selectedNotifyIds})
      this.setData({notifyOpen:false})
      this.showTabBar()
      wx.showToast({title:'已发送通知',icon:'none'})
    }catch(error){wx.showToast({title:error.message||'通知发送失败',icon:'none'})}
    finally{this.setData({notifyBusy:false})}
  },
  goHome(){wx.switchTab({url:'/pages/home/index'})},
  swallow(){}
})
