const api=require('../../services/api')
const session=require('../../services/session')
const {isGuest}=require('../../utils/profile')
const {imageUrl}=require('../../utils/recipe')
const {dateText}=require('../../utils/date')
const refresh=require('../../utils/refresh')
const {rangeOptions,buildIngredientSummary,buildSeasoningSummary,summarySignature,isInRange,formatPurchaseLine,statusLabel}=require('../../utils/purchaseSummary')
const MARK_PREFIX='family_purchase_marks:'
const ACTION_WIDTH=174
Page({
  data:{guest:false,rangeOptions,activeRange:'today',today:'',loading:false,error:'',ingredients:[],seasonings:[],wantedCount:0,buyEditorOpen:false,buyTarget:null,buyQuantity:'',previewOpen:false,previewLines:[],previewBusy:false},
  onLoad(options){
    const user=session.get()
    if(!user?.token){wx.reLaunch({url:'/pages/login/index'});return}
    this.user=user
    const range=rangeOptions.some(item=>item.key===options.range)?options.range:'today'
    this.setData({guest:isGuest(user),activeRange:range,today:dateText()})
    if(isGuest(user))return
    this.load()
  },
  onShow(){
    if(this.data.guest||!this.loadedAt)return
    if(refresh.version('want')!==this.seenDirty)this.load()
  },
  onPullDownRefresh(){if(this.data.guest){wx.stopPullDownRefresh();return}this.load().finally(()=>wx.stopPullDownRefresh())},
  async load(){
    if(this.data.loading)return
    this.setData({loading:true,error:''})
    try{
      const wanted=await this.fetchWanted()
      const ids=[...new Set(wanted.map(item=>item.recipeId).filter(Boolean))]
      const details=await Promise.all(ids.map(id=>api.post('/recipe/getById',{id}).catch(()=>null)))
      const detailMap={}
      details.filter(Boolean).forEach(detail=>{if(detail?.recipe?.id)detailMap[detail.recipe.id]=detail})
      this.wanted=wanted
      this.detailMap=detailMap
      this.loadedAt=Date.now()
      this.seenDirty=refresh.version('want')
      this.rebuildSummary()
    }catch(error){this.setData({error:error.message||'采购汇总加载失败'})}
    finally{this.setData({loading:false})}
  },
  async fetchWanted(){
    let records=[],page=1,total=0
    do{
      const data=await api.post('/want/page',{current:page,size:50,userId:this.user.id})
      const chunk=data?.records||[]
      records=records.concat(chunk)
      total=Number(data?.total||0)
      if(!chunk.length||records.length>=total||page>=10)break
      page++
    }while(true)
    return records
  },
  rebuildSummary(){
    const today=this.data.today
    const filtered=(this.wanted||[]).filter(item=>isInRange(item.plannedDate,this.data.activeRange,today))
    const details=[]
    filtered.forEach(item=>{
      const detail=this.detailMap?.[item.recipeId]
      if(detail)details.push(detail)
    })
    const ingredients=buildIngredientSummary(details).map(item=>({...item,imageUrl:imageUrl(item.image)}))
    const seasonings=buildSeasoningSummary(details).map(item=>({...item,imageUrl:imageUrl(item.image)}))
    this.marks=this.loadMarks(ingredients,seasonings)
    this.setData({ingredients:this.decorate(ingredients),seasonings:this.decorate(seasonings),wantedCount:filtered.length})
  },
  decorate(items){
    return items.map(item=>{
      const mark=this.marks[item.key]||{}
      return {...item,status:mark.status||'',statusText:statusLabel(mark),statusClass:mark.status?`is-${mark.status}`:'',offset:0}
    })
  },
  markKey(){return `${MARK_PREFIX}${this.data.activeRange}`},
  loadMarks(ingredients,seasonings){
    const signature=summarySignature(ingredients,seasonings)
    this.signature=signature
    try{
      const cache=wx.getStorageSync(this.markKey())
      if(cache&&cache.signature===signature&&cache.marks)return cache.marks
    }catch(_){}
    return {}
  },
  saveMarks(){
    try{wx.setStorageSync(this.markKey(),{signature:this.signature,marks:this.marks})}catch(_){}
  },
  changeRange(e){
    const range=e.currentTarget.dataset.range
    if(!rangeOptions.some(item=>item.key===range)||range===this.data.activeRange)return
    this.setData({activeRange:range})
    this.rebuildSummary()
  },
  swipeStart(e){
    if(this.data.buyEditorOpen||this.data.previewOpen)return
    const type=e.currentTarget.dataset.type,index=Number(e.currentTarget.dataset.index)
    const list=this.data[type]
    if(!list||!list[index])return
    const touch=e.touches[0]
    this.swipeState={type,index,key:list[index].key,startX:touch.clientX,startY:touch.clientY,base:list[index].offset||0,horizontal:false}
  },
  swipeMove(e){
    const state=this.swipeState
    if(!state)return
    const touch=e.touches[0]
    const dx=touch.clientX-state.startX,dy=touch.clientY-state.startY
    if(!state.horizontal){
      if(Math.abs(dx)<8)return
      if(Math.abs(dx)<=Math.abs(dy)){this.swipeState=null;return}
      state.horizontal=true
    }
    const offset=Math.max(-ACTION_WIDTH,Math.min(0,state.base+dx))
    this.setData({[`${state.type}[${state.index}].offset`]:offset})
  },
  swipeEnd(){
    const state=this.swipeState
    if(!state)return
    this.swipeState=null
    const list=this.data[state.type]||[]
    const current=list[state.index]?.offset||0
    const open=current<-ACTION_WIDTH/2
    const updates={}
    ;(this.data.ingredients||[]).forEach((_,index)=>{updates[`ingredients[${index}].offset`]=0})
    ;(this.data.seasonings||[]).forEach((_,index)=>{updates[`seasonings[${index}].offset`]=0})
    updates[`${state.type}[${state.index}].offset`]=open?-ACTION_WIDTH:0
    this.setData(updates)
  },
  closeSwipes(){
    const updates={}
    ;(this.data.ingredients||[]).forEach((_,index)=>{updates[`ingredients[${index}].offset`]=0})
    ;(this.data.seasonings||[]).forEach((_,index)=>{updates[`seasonings[${index}].offset`]=0})
    this.setData(updates)
  },
  rowFromEvent(e){
    const type=e.currentTarget.dataset.type,index=Number(e.currentTarget.dataset.index)
    const item=(this.data[type]||[])[index]
    return item?{type,index,item}:null
  },
  markEnough(e){
    const row=this.rowFromEvent(e)
    if(!row)return
    this.applyMark(row.item.key,'enough')
  },
  markClear(e){
    const row=this.rowFromEvent(e)
    if(!row)return
    this.applyMark(row.item.key,'')
  },
  markBuy(e){
    const row=this.rowFromEvent(e)
    if(!row)return
    if(row.type==='ingredients'){this.openBuyEditor(row.item);return}
    this.applyMark(row.item.key,'buy')
  },
  applyMark(key,status,quantity=''){
    if(!status)delete this.marks[key]
    else this.marks={...this.marks,[key]:{status,quantity:status==='buy'?quantity:''}}
    this.saveMarks()
    this.setData({ingredients:this.decorate(this.data.ingredients),seasonings:this.decorate(this.data.seasonings)})
  },
  openBuyEditor(item){
    this.closeSwipes()
    this.setData({buyEditorOpen:true,buyTarget:item,buyQuantity:(this.marks[item.key]||{}).quantity||''})
  },
  closeBuyEditor(){this.setData({buyEditorOpen:false,buyTarget:null,buyQuantity:''})},
  inputBuyQuantity(e){this.setData({buyQuantity:e.detail.value})},
  submitBuyEditor(){
    const target=this.data.buyTarget
    if(!target)return
    const quantity=String(this.data.buyQuantity||'').trim()
    this.setData({buyEditorOpen:false,buyTarget:null,buyQuantity:''})
    this.applyMark(target.key,'buy',quantity)
  },
  purchaseLines(){
    const lines=[]
    this.data.ingredients.filter(item=>item.status==='buy').forEach(item=>lines.push(formatPurchaseLine(item,this.marks[item.key]||{})))
    this.data.seasonings.filter(item=>item.status==='buy').forEach(item=>lines.push(formatPurchaseLine(item,this.marks[item.key]||{})))
    return lines
  },
  openPurchasePreview(){
    if(this.data.guest||this.data.previewBusy)return
    const lines=this.purchaseLines()
    if(!lines.length){wx.showToast({title:'请先把要采购的食材或调料标记为待购',icon:'none'});return}
    this.closeSwipes()
    this.setData({previewLines:lines,previewOpen:true})
  },
  closePurchasePreview(){if(!this.data.previewBusy)this.setData({previewOpen:false})},
  async confirmPurchaseTodo(){
    if(this.data.previewBusy)return
    const lines=this.data.previewLines
    if(!lines.length)return
    this.setData({previewBusy:true})
    try{
      await api.post('/todo/create',{
        title:'采购任务',
        category:'GROCERY',
        description:lines.join('\n').slice(0,500),
        dueTime:`${this.data.today}T18:00:00`,
        repeatType:'NONE',
        notifySite:true,
        notifyEmail:false,
        noticeMinutes:[0]
      })
      refresh.markDirty('todo')
      this.setData({previewOpen:false,previewLines:[]})
      wx.showToast({title:'已加入待办',icon:'none'})
    }catch(error){wx.showToast({title:error.message||'加入待办失败',icon:'none'})}
    finally{this.setData({previewBusy:false})}
  },
  goBack(){wx.navigateBack({fail:()=>wx.switchTab({url:'/pages/todos/index'})})},
  goTodos(){wx.switchTab({url:'/pages/todos/index'})},
  swallow(){}
})
