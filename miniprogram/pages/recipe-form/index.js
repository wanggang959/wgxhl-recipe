const api=require('../../services/api')
const session=require('../../services/session')
const {isGuest}=require('../../utils/profile')
const {imageUrl}=require('../../utils/recipe')
const {chooseImage,cropImage,uploadImage}=require('../../utils/imageUpload')
const refresh=require('../../utils/refresh')
const {
  difficultyOptions,servingOptions,ingredientUnits,seasoningUnits,
  emptyForm,emptyStep,emptyIngredient,emptySeasoning,validate,buildPayload,normalizeStepNo,fromDetail,
}=require('../../utils/recipeForm')
const visibilityOptions=[{value:'family',label:'家庭成员可见'},{value:'public',label:'所有人可见（公共）'},{value:'private',label:'仅自己可见'}]
const servingPickerOptions=['未填写'].concat(servingOptions)
Page({
  data:{
    loading:true,error:'',saving:false,isEdit:false,guest:false,
    form:emptyForm(),coverPreview:'',coverUploading:false,
    difficultyOptions,difficultyIndex:2,servingPickerOptions,servingIndex:0,
    visibilityOptions,visibilityIndex:0,
    categoryOptions:[{id:'',categoryName:'未分类'}],categoryIndex:0,categoryError:'',
    ingredientUnits,seasoningUnits,
    steps:[],ingredients:[],seasonings:[],
    uploadingKey:'',uploadingStepIndex:-1,
    baseOpen:false,baseType:'ingredient',baseTargetIndex:-1,baseKeyword:'',baseList:[],basePage:0,baseTotal:0,baseLoading:false,baseError:'',
    quickOpen:false,quickType:'ingredient',quickTargetIndex:-1,quickName:'',quickDesc:'',quickImage:'',quickPreview:'',quickSaving:false,quickUploading:false,quickError:''
  },
  onLoad(options){
    const user=session.get()
    if(!user?.token){wx.reLaunch({url:'/pages/login/index'});return}
    if(isGuest(user)){wx.showToast({title:'游客不能添加菜谱',icon:'none'});setTimeout(()=>wx.navigateBack({fail:()=>wx.switchTab({url:'/pages/home/index'})}),600);return}
    this.user=user
    const id=options.id||''
    this.setData({isEdit:Boolean(id)})
    wx.setNavigationBarTitle({title:id?'编辑菜谱':'添加菜谱'})
    this.bootstrap(id)
  },
  async bootstrap(id){
    this.setData({loading:true,error:''})
    try{
      const [categories,ingredientPage,seasoningPage,detail]=await Promise.all([
        this.fetchCategories(),
        api.post('/ingredient/page',{current:1,size:50}),
        api.post('/seasoning/page',{current:1,size:50}),
        id?api.post('/recipe/getById',{id}):Promise.resolve(null)
      ])
      this.ingredientOptions=this.mapBases(ingredientPage?.records,'ingredient')
      this.seasoningOptions=this.mapBases(seasoningPage?.records,'seasoning')
      this.setData({categoryOptions:[{id:'',categoryName:'未分类'}].concat(categories)})
      if(detail){
        const parsed=fromDetail(detail)
        this.applyForm(parsed.form,parsed.steps,parsed.ingredients,parsed.seasonings)
      }else{
        this.applyForm(emptyForm(),[emptyStep(0)],[emptyIngredient(0)],[emptySeasoning(0)])
      }
    }catch(error){this.setData({error:error.message||'加载失败'})}
    finally{this.setData({loading:false})}
  },
  async fetchCategories(){
    let records=[],page=1,total=0
    do{
      const data=await api.post('/category/page',{current:page,size:50})
      const chunk=data?.records||[]
      records=records.concat(chunk)
      total=Number(data?.total||0)
      if(!chunk.length)break
      page++
    }while(records.length<total&&page<=10000)
    return records
  },
  mapBases(records,type){
    return (records||[]).map(item=>type==='ingredient'
      ?{id:item.id,name:item.ingredientName,desc:item.ingredientDesc||'',image:item.ingredientImage||'',imageUrl:imageUrl(item.ingredientImage)}
      :{id:item.id,name:item.seasoningName,desc:item.seasoningDesc||'',image:item.seasoningImage||'',imageUrl:imageUrl(item.seasoningImage)})
  },
  applyForm(form,steps,ingredients,seasonings){
    const categoryIndex=Math.max(0,this.data.categoryOptions.findIndex(item=>item.id===(form.categoryId||'')))
    const difficultyIndex=Math.max(0,difficultyOptions.indexOf(form.difficulty||'普通'))
    const servingIndex=Math.max(0,servingPickerOptions.indexOf(form.servingCount||''))
    const visibilityIndex=Math.max(0,visibilityOptions.findIndex(item=>item.value===(form.visibility||'family')))
    this.setData({
      form,
      coverPreview:imageUrl(form.coverImage),
      categoryIndex,difficultyIndex,servingIndex,visibilityIndex,
      steps:steps.map((item,index)=>({...item,imageUrl:imageUrl(item.stepImage)})),
      ingredients:ingredients.map((item,index)=>({...item,imageUrl:imageUrl(item.ingredientImage),unitIndex:Math.max(0,ingredientUnits.indexOf(item.unit||'克'))})),
      seasonings:seasonings.map((item,index)=>({...item,imageUrl:imageUrl(item.seasoningImage),unitIndex:Math.max(0,seasoningUnits.indexOf(item.unit||'适量'))})),
    })
  },
  field(e){const field=e.currentTarget.dataset.field;if(field)this.setData({[field]:e.detail.value})},
  changeCategory(e){
    const index=Number(e.detail.value),option=this.data.categoryOptions[index]||{id:'',categoryName:''}
    this.setData({'form.categoryId':option.id||'','form.categoryName':option.categoryName||'',categoryIndex:index})
  },
  changeDifficulty(e){
    const index=Number(e.detail.value)
    this.setData({difficultyIndex:index,'form.difficulty':difficultyOptions[index]||'普通'})
  },
  changeServing(e){
    const index=Number(e.detail.value)
    this.setData({servingIndex:index,'form.servingCount':index>0?(servingPickerOptions[index]||''):''})
  },
  changeVisibility(e){
    const index=Number(e.detail.value)
    this.setData({visibilityIndex:index,'form.visibility':(visibilityOptions[index]||visibilityOptions[0]).value})
  },
  async uploadCover(){
    if(this.data.coverUploading)return
    const files=await chooseImage(1).catch(error=>{wx.showToast({title:error.message,icon:'none'});return[]})
    if(!files.length)return
    this.setData({coverUploading:true})
    try{
      const cropped=await cropImage(files[0],'cover');if(!cropped)return
      const path=await uploadImage(cropped,'cover')
      this.setData({'form.coverImage':path,coverPreview:imageUrl(path)})
      wx.showToast({title:'封面上传成功',icon:'none'})
    }catch(error){wx.showToast({title:error.message||'封面上传失败',icon:'none'})}
    finally{this.setData({coverUploading:false})}
  },
  removeCover(){this.setData({'form.coverImage':'',coverPreview:''})},
  addIngredient(){
    const list=this.data.ingredients.concat(emptyIngredient(this.data.ingredients.length))
    this.setData({ingredients:list})
  },
  removeIngredient(e){
    const index=Number(e.currentTarget.dataset.index)
    if(!Number.isInteger(index))return
    this.setData({ingredients:this.data.ingredients.filter((_,i)=>i!==index)})
  },
  ingredientField(e){
    const index=Number(e.currentTarget.dataset.index),field=e.currentTarget.dataset.field
    if(!Number.isInteger(index)||!field)return
    this.setData({[`ingredients[${index}].${field}`]:e.detail.value})
  },
  changeIngredientUnit(e){
    const index=Number(e.currentTarget.dataset.index),unitIndex=Number(e.detail.value)
    if(!Number.isInteger(index))return
    this.setData({[`ingredients[${index}].unit`]:ingredientUnits[unitIndex]||'克',[`ingredients[${index}].unitIndex`]:unitIndex})
  },
  addSeasoning(){
    const list=this.data.seasonings.concat(emptySeasoning(this.data.seasonings.length))
    this.setData({seasonings:list})
  },
  removeSeasoning(e){
    const index=Number(e.currentTarget.dataset.index)
    if(!Number.isInteger(index))return
    this.setData({seasonings:this.data.seasonings.filter((_,i)=>i!==index)})
  },
  seasoningField(e){
    const index=Number(e.currentTarget.dataset.index),field=e.currentTarget.dataset.field
    if(!Number.isInteger(index)||!field)return
    this.setData({[`seasonings[${index}].${field}`]:e.detail.value})
  },
  changeSeasoningUnit(e){
    const index=Number(e.currentTarget.dataset.index),unitIndex=Number(e.detail.value)
    if(!Number.isInteger(index))return
    this.setData({[`seasonings[${index}].unit`]:seasoningUnits[unitIndex]||'适量',[`seasonings[${index}].unitIndex`]:unitIndex})
  },
  addStep(){
    const list=this.data.steps.concat(emptyStep(this.data.steps.length))
    this.setData({steps:normalizeStepNo(list)})
  },
  removeStep(e){
    const index=Number(e.currentTarget.dataset.index)
    if(!Number.isInteger(index))return
    this.setData({steps:normalizeStepNo(this.data.steps.filter((_,i)=>i!==index))})
  },
  moveStep(e){
    const index=Number(e.currentTarget.dataset.index),direction=Number(e.currentTarget.dataset.direction)
    const target=index+direction
    if(!Number.isInteger(index)||target<0||target>=this.data.steps.length)return
    const list=this.data.steps.slice()
    const [item]=list.splice(index,1)
    list.splice(target,0,item)
    this.setData({steps:normalizeStepNo(list)})
  },
  stepField(e){
    const index=Number(e.currentTarget.dataset.index),field=e.currentTarget.dataset.field
    if(!Number.isInteger(index)||!field)return
    this.setData({[`steps[${index}].${field}`]:e.detail.value})
  },
  async uploadStepImage(e){
    const index=Number(e.currentTarget.dataset.index)
    if(!Number.isInteger(index)||this.data.uploadingKey)return
    const files=await chooseImage(1).catch(error=>{wx.showToast({title:error.message,icon:'none'});return[]})
    if(!files.length)return
    this.setData({uploadingKey:`step-${index}`,uploadingStepIndex:index})
    try{
      const cropped=await cropImage(files[0],'step');if(!cropped)return
      const path=await uploadImage(cropped,'step')
      this.setData({[`steps[${index}].stepImage`]:path,[`steps[${index}].imageUrl`]:imageUrl(path)})
    }catch(error){wx.showToast({title:error.message||'步骤图上传失败',icon:'none'})}
    finally{this.setData({uploadingKey:'',uploadingStepIndex:-1})}
  },
  removeStepImage(e){
    const index=Number(e.currentTarget.dataset.index)
    if(!Number.isInteger(index))return
    this.setData({[`steps[${index}].stepImage`]:'',[`steps[${index}].imageUrl`]:''})
  },
  baseListFor(type){return type==='ingredient'?(this.ingredientOptions||[]):(this.seasoningOptions||[])},
  currentRow(type,index){return (type==='ingredient'?this.data.ingredients:this.data.seasonings)[index]},
  openBasePicker(e){
    const type=e.currentTarget.dataset.type==='seasoning'?'seasoning':'ingredient',index=Number(e.currentTarget.dataset.index)
    if(!Number.isInteger(index))return
    this.hideTabBar()
    this.setData({baseOpen:true,baseType:type,baseTargetIndex:index,baseKeyword:'',baseList:[],basePage:0,baseTotal:0,baseError:''})
    this.loadBase(false)
  },
  closeBasePicker(){this.setData({baseOpen:false});this.showTabBar()},
  inputBaseKeyword(e){this.setData({baseKeyword:e.detail.value})},
  searchBase(){this.loadBase(false)},
  async loadBase(append){
    if(this.data.baseLoading)return
    const version=(this.baseVersion||0)+1
    this.baseVersion=version
    const current=append?this.data.basePage+1:1
    this.setData({baseLoading:true,baseError:''})
    try{
      const path=this.data.baseType==='ingredient'?'/ingredient/page':'/seasoning/page'
      const data=await api.post(path,{current,size:30,keyword:this.data.baseKeyword.trim()})
      if(version!==this.baseVersion)return
      const records=this.mapBases(data?.records,this.data.baseType)
      const row=this.currentRow(this.data.baseType,this.data.baseTargetIndex)
      const selectedId=row?(this.data.baseType==='ingredient'?row.ingredientId:row.seasoningId):''
      const list=records.map(item=>({...item,selected:item.id===selectedId}))
      this.setData({baseList:append?this.data.baseList.concat(list):list,basePage:current,baseTotal:records.length?Number(data?.total||0):0})
    }catch(error){if(version===this.baseVersion)this.setData({baseError:error.message||'加载失败'})}
    finally{if(version===this.baseVersion)this.setData({baseLoading:false})}
  },
  loadMoreBase(){if(this.data.baseList.length<this.data.baseTotal)this.loadBase(true)},
  selectBase(e){
    const id=e.currentTarget.dataset.id
    const option=(this.data.baseList||[]).find(item=>item.id===id)
    if(!option)return
    const type=this.data.baseType,index=this.data.baseTargetIndex
    if(type==='ingredient'){
      this.setData({[`ingredients[${index}].ingredientId`]:option.id,[`ingredients[${index}].ingredientName`]:option.name,[`ingredients[${index}].ingredientImage`]:option.image,[`ingredients[${index}].imageUrl`]:option.imageUrl})
    }else{
      this.setData({[`seasonings[${index}].seasoningId`]:option.id,[`seasonings[${index}].seasoningName`]:option.name,[`seasonings[${index}].seasoningImage`]:option.image,[`seasonings[${index}].imageUrl`]:option.imageUrl})
    }
    this.closeBasePicker()
  },
  openQuickFromBase(){
    const type=this.data.baseType,index=this.data.baseTargetIndex
    this.setData({baseOpen:false})
    this.openQuick({currentTarget:{dataset:{type,index}}})
  },
  openQuick(e){
    const type=e.currentTarget.dataset.type==='seasoning'?'seasoning':'ingredient'
    let index=Number(e.currentTarget.dataset.index)
    if(!Number.isInteger(index)){
      const list=type==='ingredient'?this.data.ingredients:this.data.seasonings
      index=list.findIndex(item=>!(type==='ingredient'?item.ingredientId:item.seasoningId))
      if(index<0){
        index=list.length
        this.setData(type==='ingredient'?{ingredients:list.concat(emptyIngredient(index))}:{seasonings:list.concat(emptySeasoning(index))})
      }
    }
    this.hideTabBar()
    this.setData({quickOpen:true,quickType:type,quickTargetIndex:index,quickName:'',quickDesc:'',quickImage:'',quickPreview:'',quickError:''})
  },
  closeQuick(){if(!this.data.quickSaving)this.setData({quickOpen:false});this.showTabBar()},
  quickField(e){const field=e.currentTarget.dataset.field;if(field)this.setData({[field]:e.detail.value,quickError:''})},
  async uploadQuickImage(){
    if(this.data.quickUploading)return
    const files=await chooseImage(1).catch(error=>{wx.showToast({title:error.message,icon:'none'});return[]})
    if(!files.length)return
    this.setData({quickUploading:true})
    try{
      const cropped=await cropImage(files[0],this.data.quickType);if(!cropped)return
      const path=await uploadImage(cropped,this.data.quickType)
      this.setData({quickImage:path,quickPreview:imageUrl(path)})
    }catch(error){this.setData({quickError:error.message||'图片上传失败'})}
    finally{this.setData({quickUploading:false})}
  },
  async submitQuick(){
    if(this.data.quickSaving)return
    const name=this.data.quickName.trim(),type=this.data.quickType
    if(!name){this.setData({quickError:'请输入名称'});return}
    this.setData({quickSaving:true,quickError:''})
    try{
      const payload=type==='ingredient'
        ?{ingredientName:name,ingredientDesc:this.data.quickDesc.trim(),ingredientImage:this.data.quickImage}
        :{seasoningName:name,seasoningDesc:this.data.quickDesc.trim(),seasoningImage:this.data.quickImage}
      const created=await api.post(type==='ingredient'?'/ingredient/create':'/seasoning/create',payload)
      const option=this.mapBases([created],type)[0]
      if(!option){throw new Error('创建结果异常，请重试')}
      if(type==='ingredient')this.ingredientOptions=(this.ingredientOptions||[]).concat(option)
      else this.seasoningOptions=(this.seasoningOptions||[]).concat(option)
      const index=this.data.quickTargetIndex
      if(type==='ingredient'){
        this.setData({[`ingredients[${index}].ingredientId`]:option.id,[`ingredients[${index}].ingredientName`]:option.name,[`ingredients[${index}].ingredientImage`]:option.image,[`ingredients[${index}].imageUrl`]:option.imageUrl})
      }else{
        this.setData({[`seasonings[${index}].seasoningId`]:option.id,[`seasonings[${index}].seasoningName`]:option.name,[`seasonings[${index}].seasoningImage`]:option.image,[`seasonings[${index}].imageUrl`]:option.imageUrl})
      }
      wx.showToast({title:'已创建并选用',icon:'none'})
      this.setData({quickOpen:false})
      this.showTabBar()
    }catch(error){this.setData({quickError:error.message||'创建失败'})}
    finally{this.setData({quickSaving:false})}
  },
  async submit(){
    if(this.data.saving)return
    if(this.data.coverUploading || this.data.uploadingKey){wx.showToast({title:'图片上传中，请稍候',icon:'none'});return}
    const message=validate(this.data.form)
    if(message){wx.showToast({title:message,icon:'none'});return}
    this.setData({saving:true})
    try{
      const payload=buildPayload(this.data.form,this.data.steps,this.data.ingredients,this.data.seasonings)
      const result=await api.post(this.data.isEdit?'/recipe/update':'/recipe/create',payload)
      refresh.markDirty('recipes');refresh.markDirty('favorites');refresh.markDirty('want');refresh.markDirty('todo')
      wx.showToast({title:this.data.isEdit?'已保存':'已添加',icon:'none'})
      const newId=result?.recipe?.id||result?.id
      if(!this.data.isEdit&&newId){
        setTimeout(()=>wx.redirectTo({url:'/pages/recipe-detail/index?id='+encodeURIComponent(newId)}),400)
      }else{
        setTimeout(()=>wx.navigateBack({fail:()=>wx.switchTab({url:'/pages/home/index'})}),400)
      }
    }catch(error){wx.showToast({title:error.message||'保存失败',icon:'none'})}
    finally{this.setData({saving:false})}
  },
  hideTabBar(){this.getTabBar?.()?.setData({hidden:true});wx.hideTabBar({animation:false,fail:()=>{}})},
  showTabBar(){if(this.data.baseOpen||this.data.quickOpen)return;this.getTabBar?.()?.setData({hidden:false});wx.showTabBar({animation:false,fail:()=>{}})},
  swallow(){}
})
