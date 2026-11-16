const api=require('../../services/api')
const session=require('../../services/session')
const {presentDetail,recipeImageUrls}=require('../../utils/recipe')
const {isGuest}=require('../../utils/profile')
const refresh=require('../../utils/refresh')
const {dateText}=require('../../utils/date')
Page({
  data:{loading:true,error:'',recipe:null,ingredients:[],seasonings:[],steps:[],imageUrls:[],guest:false,isOwner:false,isSuperAdmin:false,isFavorite:false,isWanted:false,favoriteBusy:false,likeBusy:false,wantBusy:false,statusBusy:false},
  onLoad(options){
    const user=session.get()
    if(!user?.token){wx.reLaunch({url:'/pages/login/index'});return}
    this.user=user
    this.id=options.id||''
    this.setData({guest:isGuest(user),isSuperAdmin:user.userRole==='super_admin'||user.username==='王师傅'})
    this.load()
  },
  onShow(){
    if(this.loadedOnce)this.load(true)
  },
  async load(silent=false){
    if(!this.id){this.setData({loading:false,error:'菜谱不存在'});return}
    if(!silent)this.setData({loading:true,error:''})
    try{
      const detail=await api.post('/recipe/getById',{id:this.id})
      if(!detail?.recipe)throw new Error('菜谱不存在或无权查看')
      const presented=presentDetail(detail)
      this.detail=detail
      const user=this.user||session.get()||{}
      const guest=isGuest(user)
      let isFavorite=false,isWanted=false
      if(!guest){
        const [favorite,wanted]=await Promise.all([
          api.post('/favorite/check',{userId:user.id,recipeId:this.id}).catch(()=>false),
          api.post('/want/check',{userId:user.id,recipeId:this.id}).catch(()=>false)
        ])
        isFavorite=Boolean(favorite);isWanted=Boolean(wanted)
      }
      this.loadedOnce=true
      this.setData({...presented,imageUrls:recipeImageUrls(detail),isFavorite,isWanted,isOwner:Boolean(user.id&&presented.recipe.ownerUserId===user.id),error:''})
    }catch(error){if(!silent)this.setData({error:error.message||'菜谱加载失败'})}
    finally{if(!silent)this.setData({loading:false})}
  },
  retry(){this.load()},
  updateRecipe(patch){this.setData({['recipe']:{...this.data.recipe,...patch}})},
  async toggleFavorite(){
    if(this.data.guest||this.data.favoriteBusy)return
    const user=this.user||{}
    this.setData({favoriteBusy:true})
    try{
      if(this.data.isFavorite){
        const result=await api.post('/favorite/deleteByRecipeId',{userId:user.id,recipeId:this.id})
        const count=result?.favoriteCount
        this.updateRecipe({favoriteCount:count!=null?Math.max(0,Number(count)):Math.max(0,this.data.recipe.favoriteCount-1)})
        this.setData({isFavorite:false})
        wx.showToast({title:'已取消收藏',icon:'none'})
      }else{
        const result=await api.post('/favorite/create',{userId:user.id,recipeId:this.id})
        const count=result?.favoriteCount
        this.updateRecipe({favoriteCount:count!=null?Math.max(0,Number(count)):this.data.recipe.favoriteCount+1})
        this.setData({isFavorite:true})
        wx.showToast({title:'收藏成功',icon:'none'})
      }
      refresh.markDirty('favorites');refresh.markDirty('recipes')
    }catch(error){wx.showToast({title:error.message||'收藏操作失败',icon:'none'})}
    finally{this.setData({favoriteBusy:false})}
  },
  async toggleLike(){
    if(this.data.guest||this.data.likeBusy)return
    this.setData({likeBusy:true})
    try{
      const result=await api.post('/recipe/toggleLike',{id:this.id})
      this.updateRecipe({liked:Boolean(result?.liked),likeCount:Math.max(0,Number(result?.likeCount||0))})
      refresh.markDirty('recipes')
    }catch(error){wx.showToast({title:error.message||'点赞失败',icon:'none'})}
    finally{this.setData({likeBusy:false})}
  },
  async toggleWant(){
    if(this.data.guest||this.data.wantBusy)return
    const user=this.user||{}
    this.setData({wantBusy:true})
    try{
      if(this.data.isWanted){
        await api.post('/want/deleteByRecipeId',{userId:user.id,recipeId:this.id})
        this.setData({isWanted:false})
        wx.showToast({title:'已取消想吃',icon:'none'})
      }else{
        await api.post('/want/create',{userId:user.id,recipeId:this.id,plannedDate:dateText()})
        this.setData({isWanted:true})
        wx.showToast({title:'已添加到想吃',icon:'none'})
      }
      refresh.markDirty('want');refresh.markDirty('todo')
    }catch(error){wx.showToast({title:error.message||'想吃操作失败',icon:'none'})}
    finally{this.setData({wantBusy:false})}
  },
  edit(){wx.navigateTo({url:`/pages/recipe-form/index?id=${encodeURIComponent(this.id)}`})},
  remove(){
    if(!this.data.isOwner)return
    const name=this.data.recipe?.recipeName||'这道菜'
    wx.showModal({title:'删除菜谱',content:`确认删除「${name}」吗？删除后无法恢复。`,confirmText:'删除',confirmColor:'#d64335',success:async({confirm})=>{
      if(!confirm)return
      try{
        await api.post('/recipe/delete',{id:this.id})
        refresh.markDirty('recipes');refresh.markDirty('favorites');refresh.markDirty('want');refresh.markDirty('todo')
        wx.showToast({title:'菜谱已删除',icon:'none'})
        setTimeout(()=>wx.navigateBack({fail:()=>wx.switchTab({url:'/pages/home/index'})}),500)
      }catch(error){wx.showToast({title:error.message||'删除失败',icon:'none'})}
    }})
  },
  toggleStatus(){
    if(!this.data.isSuperAdmin)return
    const offline=this.data.recipe?.status==='下架',name=this.data.recipe?.recipeName||'这道菜'
    wx.showModal({title:offline?'恢复上架':'下架菜谱',content:offline?`确认把「${name}」恢复到家庭菜谱吗？`:`下架后家人将看不到「${name}」，只有作者和家庭管理员还能打开。`,confirmText:offline?'恢复':'下架',confirmColor:offline?'#f47716':'#d64335',success:async({confirm})=>{
      if(!confirm||this.data.statusBusy)return
      this.setData({statusBusy:true})
      try{
        await api.post('/recipe/setStatus',{id:this.id,status:offline?'上架':'下架'})
        refresh.markDirty('recipes')
        wx.showToast({title:offline?'已恢复上架':'已下架',icon:'none'})
        await this.load(true)
      }catch(error){wx.showToast({title:error.message||'操作失败',icon:'none'})}
      finally{this.setData({statusBusy:false})}
    }})
  },
  previewImage(e){
    const src=e.currentTarget.dataset.src
    if(!src)return
    const urls=this.data.imageUrls.length?this.data.imageUrls:[src]
    wx.previewImage({current:src,urls})
  }
})
