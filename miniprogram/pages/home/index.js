const api=require('../../services/api')
const session=require('../../services/session')
const {isGuest}=require('../../utils/profile')
const {presentRecipe}=require('../../utils/recipe')
const refresh=require('../../utils/refresh')
const {dateText,addDays}=require('../../utils/date')
const sorts=[{value:'latest',label:'最新'},{value:'likes_desc',label:'最赞'},{value:'likes_asc',label:'冷门'},{value:'random',label:'随机'}]
Page({
  data:{user:null,guest:false,isSuperAdmin:false,showOffline:false,scope:'family',sort:'latest',sorts,keyword:'',appliedKeyword:'',categoryId:'',categories:[],categoryError:'',recipes:[],page:0,total:0,loading:false,error:'',unread:0,noticeOpen:false,favoriteBusyId:'',likeBusyId:''},
  onShow(){
    const user=session.get()
    if(!user?.token){wx.reLaunch({url:'/pages/login/index'});return}
    this.getTabBar()?.setData({selected:'home',hidden:false})
    const guest=isGuest(user),identity=[user.id,user.householdId,guest].join(':'),dirty=refresh.version('recipes')
    if(this.identity!==identity){
      this.identity=identity
      this.recipeDirty=dirty
      this.setData({user,guest,isSuperAdmin:!guest&&(user.userRole==='super_admin'||user.username==='王师傅'),showOffline:false,scope:guest?'public':'family',categoryId:'',keyword:'',appliedKeyword:''})
      this.loadCategories()
      this.load(true)
    }else{
      this.setData({user,guest,isSuperAdmin:!guest&&(user.userRole==='super_admin'||user.username==='王师傅')})
      if(dirty!==this.recipeDirty){this.recipeDirty=dirty;this.load(true)}
    }
    this.refreshUnread()
    this.stopTimer()
    this.unreadTimer=setInterval(()=>this.refreshUnread(),60000)
  },
  onHide(){this.stopTimer();this.closeNotifications();this.closeWant()},
  onUnload(){this.stopTimer();this.requestVersion=(this.requestVersion||0)+1;this.categoryVersion=(this.categoryVersion||0)+1},
  stopTimer(){if(this.unreadTimer)clearInterval(this.unreadTimer);this.unreadTimer=null},
  async refreshUnread(){try{const value=await api.post('/notification/unreadCount');this.setData({unread:Math.max(0,Number(value)||0)})}catch(_){}},
  openNotifications(){this.setData({noticeOpen:true});this.getTabBar()?.setData({hidden:true});wx.hideTabBar({animation:false,fail:()=>{}})},
  closeNotifications(){if(!this.data.noticeOpen)return;this.setData({noticeOpen:false});this.getTabBar()?.setData({hidden:false});wx.showTabBar({animation:false,fail:()=>{}});this.refreshUnread()},
  add(){wx.navigateTo({url:'/pages/recipe-form/index'})},
  async loadCategories(){const version=(this.categoryVersion||0)+1;this.categoryVersion=version;this.setData({categoryError:''});try{let records=[],page=1,total=0;do{const data=await api.post('/category/page',{current:page,size:50});if(version!==this.categoryVersion)return;const chunk=data?.records||[];records=records.concat(chunk);total=Number(data?.total||0);if(!chunk.length)break;page++}while(records.length<total&&page<=10000);this.setData({categories:records})}catch(error){if(version===this.categoryVersion)this.setData({categoryError:error.message||'分类加载失败'})}},
  async load(reset){
    if(!reset&&(this.data.loading||this.data.recipes.length>=this.data.total))return
    const version=(this.requestVersion||0)+1;this.requestVersion=version
    const current=reset?1:this.data.page+1,keyword=reset?this.data.keyword.trim():this.data.appliedKeyword
    if(reset)this.pivot=this.data.sort==='random'?Math.floor(Math.random()*1000000000)+1:null
    const query={current,size:8,status:this.data.isSuperAdmin&&this.data.showOffline?'下架':'上架',scope:this.data.guest?'public':this.data.scope,sort:this.data.sort,randomPivot:this.pivot,recipeName:keyword,categoryId:this.data.categoryId}
    this.setData({loading:true,error:'',...(reset?{recipes:[],page:0,total:0,appliedKeyword:keyword}:{})})
    try{
      const data=await api.post('/recipe/page',query)
      if(version!==this.requestVersion)return
      const records=(data?.records||[]).map(presentRecipe),recipes=reset?records:this.data.recipes.concat(records)
      this.recipeDirty=refresh.version('recipes')
      this.setData({recipes,page:current,total:records.length?Number(data?.total||0):recipes.length})
    }catch(error){if(version===this.requestVersion)this.setData({error:error.message||'菜谱加载失败'})}
    finally{if(version===this.requestVersion)this.setData({loading:false})}
  },
  onReachBottom(){if(!this.data.error&&this.data.page&&this.data.recipes.length<this.data.total)this.load(false)},
  async onPullDownRefresh(){try{await Promise.all([this.loadCategories(),this.load(true)])}finally{wx.stopPullDownRefresh()}},
  chooseScope(e){const scope=e.currentTarget.dataset.value;if(this.data.guest||!['family','public'].includes(scope)||scope===this.data.scope)return;this.setData({scope,categoryId:'',showOffline:false});this.load(true)},
  toggleOffline(){if(!this.data.isSuperAdmin||this.data.guest||this.data.scope==='public')return;this.setData({showOffline:!this.data.showOffline});this.load(true)},
  chooseSort(e){const sort=e.currentTarget.dataset.value;if(!sorts.some(item=>item.value===sort)||sort===this.data.sort)return;this.setData({sort});this.load(true)},
  chooseCategory(e){const categoryId=e.currentTarget.dataset.id||'';if(categoryId===this.data.categoryId)return;this.setData({categoryId});this.load(true)},
  inputKeyword(e){this.setData({keyword:e.detail.value})},search(){this.load(true)},retry(){this.load(this.data.page===0)},retryCategories(){this.loadCategories()},
  openRecipe(e){const id=e.currentTarget.dataset.id;if(id)wx.navigateTo({url:'/pages/recipe-detail/index?id='+encodeURIComponent(id)})},
  findRecipe(id){return this.data.recipes.find(item=>item.id===id)},
  updateRecipe(id, patch){this.setData({recipes:this.data.recipes.map(item=>item.id===id?{...item,...patch}:item)})},
  async toggleFavorite(e){
    const id=e.currentTarget.dataset.id,item=this.findRecipe(id)
    if(!item||this.data.guest||this.data.favoriteBusyId)return
    const user=this.data.user||{}
    this.setData({favoriteBusyId:id})
    try{
      if(item.favorite){
        const result=await api.post('/favorite/deleteByRecipeId',{userId:user.id,recipeId:id})
        const count=result?.favoriteCount
        this.updateRecipe(id,{favorite:false,favoriteCount:count!=null?Math.max(0,Number(count)):Math.max(0,item.favoriteCount-1)})
      }else{
        const result=await api.post('/favorite/create',{userId:user.id,recipeId:id})
        const count=result?.favoriteCount
        this.updateRecipe(id,{favorite:true,favoriteCount:count!=null?Math.max(0,Number(count)):item.favoriteCount+1})
      }
      refresh.markDirty('favorites');refresh.markDirty('recipes')
      wx.showToast({title:item.favorite?'已取消收藏':'收藏成功',icon:'none'})
    }catch(error){wx.showToast({title:error.message||'收藏操作失败',icon:'none'})}
    finally{this.setData({favoriteBusyId:''})}
  },
  async toggleLike(e){
    const id=e.currentTarget.dataset.id,item=this.findRecipe(id)
    if(!item||this.data.guest||this.data.likeBusyId)return
    this.setData({likeBusyId:id})
    try{
      const result=await api.post('/recipe/toggleLike',{id})
      this.updateRecipe(id,{liked:Boolean(result?.liked),likeCount:Math.max(0,Number(result?.likeCount||0))})
      refresh.markDirty('recipes')
    }catch(error){wx.showToast({title:error.message||'点赞失败',icon:'none'})}
    finally{this.setData({likeBusyId:''})}
  },
  openWant(e){const item=this.findRecipe(e.currentTarget.dataset.id);if(!item||this.data.guest||this.data.wantBusy)return;const today=dateText();this.setData({wantOpen:true,wantTarget:item,wantDate:today,wantMinDate:today});this.getTabBar()?.setData({hidden:true});wx.hideTabBar({animation:false,fail:()=>{}})},
  closeWant(){if(!this.data.wantOpen||this.data.wantBusy)return;this.setData({wantOpen:false,wantTarget:null});this.getTabBar()?.setData({hidden:false});wx.showTabBar({animation:false,fail:()=>{}})},
  wantDateChange(e){this.setData({wantDate:e.detail.value})},
  wantToday(){this.submitWant(dateText())},
  wantTomorrow(){this.submitWant(addDays(1))},
  wantCustom(){this.submitWant(this.data.wantDate)},
  async submitWant(plannedDate){
    const item=this.data.wantTarget,user=this.data.user
    if(!item||this.data.guest||this.data.wantBusy||!user?.id)return
    if(!plannedDate||plannedDate<dateText()){wx.showToast({title:'请选择今天或之后的日期',icon:'none'});return}
    this.setData({wantBusy:true})
    try{await api.post('/want/create',{userId:user.id,recipeId:item.id,plannedDate});this.updateRecipe(item.id,{wanted:true});refresh.markDirty('want');refresh.markDirty('todo');refresh.markDirty('recipes');this.setData({wantBusy:false});this.closeWant();wx.showToast({title:'已添加到想吃',icon:'none'})}
    catch(error){wx.showToast({title:error.message||'添加想吃失败',icon:'none'})}
    finally{this.setData({wantBusy:false})}
  },
  swallow(){},
})
