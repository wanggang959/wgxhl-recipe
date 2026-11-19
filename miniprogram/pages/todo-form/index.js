const api=require('../../services/api')
const session=require('../../services/session')
const {categories,repeats,notices,defaultNotices,fromTodo,toPayload}=require('../../utils/todoForm')
Page({
  data:{loading:true,saving:false,error:'',ready:false,nextCycle:false,form:fromTodo(),categories,repeats,notices:notices.map(n=>({...n,selected:n.value===0})),members:[],categoryIndex:9,repeatIndex:0,repeatOpen:false,categoryOpen:false,calendarIndex:0,calendarLabels:['公历生日','农历生日'],ledgerCategories:['餐饮','买菜','居家','交通','购物','订阅','缴费','医疗','娱乐','其他']},
  async onLoad(options) {
    if(!session.get()?.token){wx.reLaunch({url:'/pages/login/index'});return}
    const id=options.id || '', nextCycle=options.nextCycle === '1'
    this.setData({nextCycle})
    wx.setNavigationBarTitle({title:nextCycle?'下一个周期':id?'编辑待办':'新增待办'})
    try {
      const [members,todo]=await Promise.all([api.post('/user/members'),id ? api.post('/todo/getById',{id}) : Promise.resolve({})])
      const form=fromTodo(todo || {},nextCycle)
      this.setData({form,members:(members || []).map(member=>({...member,selected:form.ownerIds.includes(member.id)})),notices:notices.map(n=>({...n,selected:form.noticeMinutes.includes(n.value)})),categoryIndex:Math.max(0,categories.findIndex(c=>c.value===form.category)),repeatIndex:Math.max(0,repeats.findIndex(r=>r.value===form.repeatType)),calendarIndex:form.dueCalendar==='LUNAR'?1:0,ready:true})
    } catch(error){this.setData({error:error.message || '加载失败'})}
    finally{this.setData({loading:false})}
  },
  field(e){this.setData({[`form.${e.currentTarget.dataset.field}`]:e.detail.value})},
  toggle(e){this.setData({[`form.${e.currentTarget.dataset.field}`]:e.detail.value})},
  ledgerCategoryChange(e){const value=this.data.ledgerCategories[Number(e.detail.value)];if(value)this.setData({'form.ledgerCategory':value})},
  owners(e){const ids=e.detail.value;this.setData({'form.ownerIds':ids,members:this.data.members.map(m=>({...m,selected:ids.includes(m.id)}))})},
  noticeChange(e){const values=e.detail.value.map(Number).sort((a,b)=>b-a);this.setData({'form.noticeMinutes':values,notices:notices.map(n=>({...n,selected:values.includes(n.value)}))})},
  categoryChange(e){const index=Number(e.detail.value), next=categories[index].value, previous=this.data.form.category, selected=defaultNotices(next);this.setData({categoryIndex:index,'form.category':next,'form.repeatType':next==='BIRTHDAY'?'YEARLY':previous==='BIRTHDAY'?'NONE':this.data.form.repeatType,repeatIndex:next==='BIRTHDAY'?6:previous==='BIRTHDAY'?0:this.data.repeatIndex,'form.dueCalendar':next==='BIRTHDAY'?this.data.form.dueCalendar:'SOLAR',calendarIndex:next==='BIRTHDAY'?this.data.calendarIndex:0,'form.noticeMinutes':selected,notices:notices.map(n=>({...n,selected:selected.includes(n.value)}))})},
  toggleCategory(){this.setData({categoryOpen:!this.data.categoryOpen,repeatOpen:false})},
  toggleRepeat(){this.setData({repeatOpen:!this.data.repeatOpen,categoryOpen:false})},
  closeDropdown(){if(this.data.repeatOpen || this.data.categoryOpen)this.setData({repeatOpen:false,categoryOpen:false})},
  selectCategory(e){const index=Number(e.currentTarget.dataset.index);if(!Number.isInteger(index)||!categories[index])return;this.categoryChange({detail:{value:index}});this.setData({categoryOpen:false})},
  selectRepeat(e){const index=Number(e.currentTarget.dataset.index);if(!Number.isInteger(index)||!repeats[index])return;this.setData({repeatIndex:index,'form.repeatType':repeats[index].value,repeatOpen:false})},
  calendarChange(e){const index=Number(e.detail.value);this.setData({calendarIndex:index,'form.dueCalendar':index?'LUNAR':'SOLAR'})},
  async save(){
    if(this.data.saving || !this.data.ready)return
    let payload;try{payload=toPayload(this.data.form,this.data.nextCycle)}catch(error){wx.showToast({title:error.message,icon:'none'});return}
    this.setData({saving:true})
    try{await api.post(payload.id?'/todo/update':'/todo/create',payload);wx.showToast({title:this.data.nextCycle?'已进入下一周期':'已保存'});wx.navigateBack()}
    catch(error){wx.showToast({title:error.message || '保存失败',icon:'none'})}
    finally{this.setData({saving:false})}
  }
})
