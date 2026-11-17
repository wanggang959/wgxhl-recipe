const api=require('../../services/api')
const session=require('../../services/session')
const {mergeFavorite}=require('../../utils/recipe')
const {isGuest}=require('../../utils/profile')
const refresh=require('../../utils/refresh')
Page({
  data:{user:null,guest:false,list:[],page:0,total:0,loading:false,loadingMore:false,error:'',removeBusyId:'',likeBusyId:'',unread:0,noticeOpen:false},
  onShow(){
    const user=session.get()
    if(!user?.token){wx.reLaunch({url:'/pages/login/index'});return}
    this.getTabBar()?.setData({selected:'favorites',hidden:false})
    this.user=user
    const guest=isGuest(user)
    const dirty=`${refresh.version('favorites')}:${refresh.version('recipes')}`
    this.setData({guest,user})
    this.refreshUnread()
    this.stopUnreadTimer()
    this.unreadTimer=setInterval(()=>this.refreshUnread(),60000)
    if(guest){
      this.setData({list:[],page:0,total:0,error:'',loading:false,loadingMore:false})
      return
    }
    if(!this.loadedAt||dirty!==this.seenDirty||Date.now()-this.loadedAt>60000)this.load(true)
  },
  onHide(){this.stopUnreadTimer();this.closeNotifications()},
  onUnload(){this.stopUnreadTimer()},
  stopUnreadTimer(){if(this.unreadTimer)clearInterval(this.unreadTimer);this.unreadTimer=null},
  async refreshUnread(){try{const value=await api.post('/notification/unreadCount');this.setData({unread:Math.max(0,Number(value)||0)})}catch(_){}},
  openNotifications(){this.setData({noticeOpen:true});this.getTabBar()?.setData({hidden:true})},
  closeNotifications(){if(!this.data.noticeOpen)return;this.setData({noticeOpen:false});this.getTabBar()?.setData({hidden:false});this.refreshUnread()},
  add(){wx.navigateTo({url:'/pages/recipe-form/index'})},
  onPullDownRefresh(){if(this.data.guest){wx.stopPullDownRefresh();return}this.load(true).finally(()=>wx.stopPullDownRefresh())},
  onReachBottom(){if(!this.data.error&&this.data.page&&this.data.list.length<this.data.total)this.load(false)},
  async load(reset){
    if(!reset&&(this.data.loading||this.data.loadingMore))return
    const version=(this.requestVersion||0)+1;this.requestVersion=version
    const current=reset?1:this.data.page+1
    this.setData({...(reset?{loading:true,error:'',list:[],page:0,total:0}:{loadingMore:true,error:''})})
    try{
      const data=await api.post('/favorite/page',{current,size:30,userId:this.user.id})
      if(version!==this.requestVersion)return
      const records=data?.records||[]
      const ids=[...new Set(records.map(item=>item.recipeId).filter(Boolean))]
      let recipes=[]
      if(ids.length){
        const batch=await api.post('/recipe/batch',{ids})
        if(version!==this.requestVersion)return
        recipes=Array.isArray(batch)?batch:[]
      }
      const byId=new Map(recipes.map(item=>[item.id,item]))
      const items=records.map(item=>mergeFavorite(item,byId.get(item.recipeId)))
      const list=reset?items:this.data.list.concat(items)
      this.seenDirty=`${refresh.version('favorites')}:${refresh.version('recipes')}`
      this.loadedAt=Date.now()
      this.setData({list,page:current,total:records.length?Number(data?.total||0):list.length})
    }catch(error){if(version===this.requestVersion)this.setData({error:error.message||'收藏加载失败'})}
    finally{if(version===this.requestVersion)this.setData({loading:false,loadingMore:false})}
  },
  retry(){this.load(this.data.page===0)},
  openRecipe(e){const id=e.currentTarget.dataset.id;if(id)wx.navigateTo({url:'/pages/recipe-detail/index?id='+encodeURIComponent(id)})},
  async removeFavorite(e){
    const favoriteId=e.currentTarget.dataset.id
    const item=this.data.list.find(entry=>entry.favoriteId===favoriteId)
    if(!item||this.data.removeBusyId)return
    this.setData({removeBusyId:favoriteId})
    try{
      await api.post('/favorite/delete',{id:favoriteId})
      this.setData({list:this.data.list.filter(entry=>entry.favoriteId!==favoriteId),total:Math.max(0,this.data.total-1)})
      refresh.markDirty('favorites');refresh.markDirty('recipes')
      wx.showToast({title:'已取消收藏',icon:'none'})
    }catch(error){wx.showToast({title:error.message||'操作失败',icon:'none'})}
    finally{this.setData({removeBusyId:''})}
  },
  async toggleLike(e){
    const id=e.currentTarget.dataset.id
    const item=this.data.list.find(entry=>entry.recipeId===id)
    if(!item||this.data.likeBusyId)return
    this.setData({likeBusyId:id})
    try{
      const result=await api.post('/recipe/toggleLike',{id})
      this.setData({list:this.data.list.map(entry=>entry.recipeId===id?{...entry,liked:Boolean(result?.liked),likeCount:Math.max(0,Number(result?.likeCount||0))}:entry)})
      refresh.markDirty('recipes')
    }catch(error){wx.showToast({title:error.message||'点赞失败',icon:'none'})}
    finally{this.setData({likeBusyId:''})}
  }
})
