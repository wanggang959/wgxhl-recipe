const api=require('../../services/api')
const session=require('../../services/session')
const {isGuest}=require('../../utils/profile')
const {presentRecipe}=require('../../utils/recipe')
const sorts=[{value:'latest',label:'最新'},{value:'likes_desc',label:'最赞'},{value:'likes_asc',label:'冷门'},{value:'random',label:'随机'}]
Page({
  data:{user:null,guest:false,scope:'family',sort:'latest',sorts,keyword:'',appliedKeyword:'',categoryId:'',categories:[],categoryError:'',recipes:[],page:0,total:0,loading:false,error:'',unread:0,noticeOpen:false},
  onShow(){const user=session.get();if(!user?.token){wx.reLaunch({url:'/pages/login/index'});return}this.getTabBar()?.setData({selected:'home',hidden:false});const guest=isGuest(user),identity=[user.id,user.householdId,guest].join(':');if(this.identity!==identity){this.identity=identity;this.setData({user,guest,scope:guest?'public':'family',categoryId:'',keyword:'',appliedKeyword:''});this.loadCategories();this.load(true)}else this.setData({user,guest});this.refreshUnread();this.stopTimer();this.unreadTimer=setInterval(()=>this.refreshUnread(),60000)},
  onHide(){this.stopTimer();this.closeNotifications()},onUnload(){this.stopTimer();this.requestVersion=(this.requestVersion||0)+1;this.categoryVersion=(this.categoryVersion||0)+1},
  stopTimer(){if(this.unreadTimer)clearInterval(this.unreadTimer);this.unreadTimer=null},
  async refreshUnread(){try{const value=await api.post('/notification/unreadCount');this.setData({unread:Math.max(0,Number(value)||0)})}catch(_){}},
  openNotifications(){this.setData({noticeOpen:true});this.getTabBar()?.setData({hidden:true});wx.hideTabBar({animation:false,fail:()=>{}})},
  closeNotifications(){if(!this.data.noticeOpen)return;this.setData({noticeOpen:false});this.getTabBar()?.setData({hidden:false});wx.showTabBar({animation:false,fail:()=>{}});this.refreshUnread()},
  add(){wx.navigateTo({url:'/pages/todo-form/index'})},
  async loadCategories(){const version=(this.categoryVersion||0)+1;this.categoryVersion=version;this.setData({categoryError:''});try{let records=[],page=1,total=0;do{const data=await api.post('/category/page',{current:page,size:50});if(version!==this.categoryVersion)return;const chunk=data?.records||[];records=records.concat(chunk);total=Number(data?.total||0);if(!chunk.length)break;page++}while(records.length<total&&page<=10000);this.setData({categories:records})}catch(error){if(version===this.categoryVersion)this.setData({categoryError:error.message||'分类加载失败'})}},
  async load(reset){if(!reset&&(this.data.loading||this.data.recipes.length>=this.data.total))return;const version=(this.requestVersion||0)+1;this.requestVersion=version;const current=reset?1:this.data.page+1,keyword=reset?this.data.keyword.trim():this.data.appliedKeyword;if(reset)this.pivot=this.data.sort==='random'?Math.floor(Math.random()*1000000000)+1:null;const query={current,size:8,status:'上架',scope:this.data.guest?'public':this.data.scope,sort:this.data.sort,randomPivot:this.pivot,recipeName:keyword,categoryId:this.data.categoryId};this.setData({loading:true,error:'',...(reset?{recipes:[],page:0,total:0,appliedKeyword:keyword}:{})});try{const data=await api.post('/recipe/page',query);if(version!==this.requestVersion)return;const records=(data?.records||[]).map(presentRecipe),recipes=reset?records:this.data.recipes.concat(records);this.setData({recipes,page:current,total:records.length?Number(data?.total||0):recipes.length})}catch(error){if(version===this.requestVersion)this.setData({error:error.message||'菜谱加载失败'})}finally{if(version===this.requestVersion)this.setData({loading:false})}},
  onReachBottom(){if(!this.data.error&&this.data.page&&this.data.recipes.length<this.data.total)this.load(false)},
  async onPullDownRefresh(){try{await Promise.all([this.loadCategories(),this.load(true)])}finally{wx.stopPullDownRefresh()}},
  chooseScope(e){const scope=e.currentTarget.dataset.value;if(this.data.guest||!['family','public'].includes(scope)||scope===this.data.scope)return;this.setData({scope,categoryId:''});this.load(true)},
  chooseSort(e){const sort=e.currentTarget.dataset.value;if(!sorts.some(item=>item.value===sort)||sort===this.data.sort)return;this.setData({sort});this.load(true)},
  chooseCategory(e){const categoryId=e.currentTarget.dataset.id||'';if(categoryId===this.data.categoryId)return;this.setData({categoryId});this.load(true)},
  inputKeyword(e){this.setData({keyword:e.detail.value})},search(){this.load(true)},retry(){this.load(this.data.page===0)},retryCategories(){this.loadCategories()},
  openRecipe(e){const id=e.currentTarget.dataset.id;if(id)wx.navigateTo({url:'/pages/recipe-detail/index?id='+encodeURIComponent(id)})}
})
