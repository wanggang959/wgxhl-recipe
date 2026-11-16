const api=require('../../services/api')
const session=require('../../services/session')
const {isGuest}=require('../../utils/profile')
const settingsDefault={noticeDay:0,arrivalTime:'09:00',noticeTime:'07:00',bufferMinutes:10,fallbackMinutes:45,workDays:'1,2,3,4,5',siteEnabled:true,emailEnabled:false,pushEnabled:false,originName:'',originLat:null,originLon:null,destinationName:'',destinationLat:null,destinationLon:null}
function today(){const d=new Date();return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`}
function timePart(value){return value?String(value).replace(' ','T').slice(11,16):''}
function blank(){return {id:'',commuteDate:today(),direction:'OUTBOUND',departureTime:'',arrivalTime:'',transport:'地铁',remark:'',includeStats:true,actualRain:'DRY'}}
function decorate(item){return {...item,dateLabel:String(item.commuteDate||'').slice(0,10),departureLabel:String(item.departureTime||'').replace('T',' ').slice(0,16),arrivalLabel:String(item.arrivalTime||'').replace('T',' ').slice(0,16),rainLabel:item.actualRain==='RAIN'?'下雨':item.actualRain==='DRY'?'不下雨':'不确定'}}
Page({
 data:{authorized:false,tab:'records',settings:{...settingsDefault},settingsLoaded:false,records:[],page:0,more:true,loading:false,busy:false,error:'',recommendation:null,statistics:null,editor:false,form:blank(),today:today(),transports:['地铁','公交','自驾','骑行','步行','其他'],rainLabels:['没下雨','下雨了','不确定'],rainValues:['DRY','RAIN','UNKNOWN'],workLabels:['周一至周五','周一至周六','每天'],workValues:['1,2,3,4,5','1,2,3,4,5,6','1,2,3,4,5,6,7'],locationPrefix:'origin',locationMode:'city',locationQuery:'',locations:[],locationBusy:false},
 onLoad(){const user=session.get();if(!user?.token){wx.reLaunch({url:'/pages/login/index'});return}if(isGuest(user)){this.setData({error:'游客不能查看通勤记录'});return}this.setData({authorized:true});this.initialize()},
 async initialize(){try{const settings=await api.post('/commute/settings');if(settings)this.setData({settings:{...settingsDefault,...settings,siteEnabled:!!settings.siteEnabled,emailEnabled:!!settings.emailEnabled,pushEnabled:!!settings.pushEnabled},settingsLoaded:true});else this.setData({settingsLoaded:true})}catch(error){await this.refresh();this.setData({error:error.message||'设置加载失败'});return}await this.refresh()},
 async refresh(){
  this.setData({error:''})
  const [recommendation,statistics]=await Promise.allSettled([api.post('/commute/recommendation'),api.post('/commute/statistics')])
  if(recommendation.status==='fulfilled'){
   const value=recommendation.value
   const label=x=>String(x||'').replace('T',' ').slice(0,16)
   this.setData({recommendation:value?{...value,dryLabel:label(value.dry?.departure),rainLabel:label(value.rain?.departure),departureLabel:label(value.departure)}:null})
  }
  else this.setData({error:recommendation.reason?.message||'出门建议加载失败'})
  if(statistics.status==='fulfilled')this.setData({statistics:statistics.value})
  await this.loadRecords(true)
 },
 async loadRecords(reset=false){
  if(this.data.loading||(!reset&&!this.data.more))return
  const page=reset?1:this.data.page+1
  this.setData({loading:true})
  try{const result=await api.post('/commute/records',{page});if(!Array.isArray(result))throw Error('记录数据异常');this.setData({records:reset?result.map(decorate):this.data.records.concat(result.map(decorate)),page,more:result.length===30})}
  catch(error){this.setData({error:error.message||'记录加载失败'})}
  finally{this.setData({loading:false})}
 },
 onReachBottom(){if(this.data.tab==='records'&&!this.data.editor)this.loadRecords(false)},
 tab(e){this.setData({tab:e.currentTarget.dataset.value,error:''})},
 input(e){const field=e.currentTarget.dataset.field;if(['commuteDate','departureTime','arrivalTime','remark'].includes(field))this.setData({[`form.${field}`]:e.detail.value})},
 settingsInput(e){const field=e.currentTarget.dataset.field;if(['arrivalTime','noticeTime','bufferMinutes','fallbackMinutes'].includes(field))this.setData({[`settings.${field}`]:e.detail.value})},
 date(e){this.setData({'form.commuteDate':e.detail.value})},
 time(e){this.setData({[`form.${e.currentTarget.dataset.field}`]:e.detail.value})},
 settingTime(e){this.setData({[`settings.${e.currentTarget.dataset.field}`]:e.detail.value})},
 direction(e){this.setData({'form.direction':e.detail.value==='0'?'OUTBOUND':'RETURN'})},
 transport(e){this.setData({'form.transport':this.data.transports[Number(e.detail.value)]})},
 rain(e){this.setData({'form.actualRain':this.data.rainValues[Number(e.detail.value)]})},
 include(e){this.setData({'form.includeStats':e.detail.value})},
 noticeDay(e){this.setData({'settings.noticeDay':Number(e.detail.value)})},
 workDays(e){this.setData({'settings.workDays':this.data.workValues[Number(e.detail.value)]})},
 channel(e){const field=e.currentTarget.dataset.field;if(['siteEnabled','emailEnabled','pushEnabled'].includes(field))this.setData({[`settings.${field}`]:e.detail.value})},
 edit(e){const item=this.data.records.find(entry=>entry.id===e.currentTarget.dataset.id);const form=item?{...blank(),...item,commuteDate:item.dateLabel,departureTime:timePart(item.departureTime),arrivalTime:timePart(item.arrivalTime),includeStats:Boolean(item.includeStats),actualRain:item.actualRain||'UNKNOWN'}:blank();this.setData({editor:true,form,error:''})},
 closeEditor(){if(!this.data.busy)this.setData({editor:false,error:''})},
 async saveRecord(){
  if(this.data.busy)return
  const form=this.data.form
  if(!form.commuteDate||!form.departureTime||!form.arrivalTime||form.arrivalTime<=form.departureTime){this.setData({error:'到达时间须晚于出发时间'});return}
  this.setData({busy:true,error:''})
  try{await api.post('/commute/records/save',{id:form.id||'',commuteDate:form.commuteDate,direction:form.direction,departureTime:`${form.commuteDate}T${form.departureTime}`,arrivalTime:`${form.commuteDate}T${form.arrivalTime}`,transport:form.transport,remark:form.remark,includeStats:form.includeStats,actualRain:form.actualRain});this.setData({editor:false});await this.refresh()}
  catch(error){this.setData({error:error.message||'保存失败'})}finally{this.setData({busy:false})}
 },
 remove(e){const item=this.data.records.find(entry=>entry.id===e.currentTarget.dataset.id);if(!item||this.data.busy)return;wx.showModal({title:'删除通勤记录',content:`确定删除 ${item.dateLabel} 的这条记录吗？删除后无法恢复。`,confirmText:'删除',confirmColor:'#d64335',success:async({confirm})=>{if(!confirm||this.data.busy)return;this.setData({busy:true});try{await api.post('/commute/records/delete',{id:item.id});await this.refresh()}catch(error){this.setData({error:error.message||'删除失败'})}finally{this.setData({busy:false})}}})},
 async pause(){if(this.data.busy)return;this.setData({busy:true});try{await api.post('/commute/pause-today');wx.showToast({title:'已暂停本次通知',icon:'none'});await this.refresh()}catch(error){this.setData({error:error.message||'操作失败'})}finally{this.setData({busy:false})}},
 async saveSettings(){
  if(this.data.busy||!this.data.settingsLoaded)return
  const s=this.data.settings,buffer=Number(s.bufferMinutes),fallback=Number(s.fallbackMinutes)
  if(!s.arrivalTime||!s.noticeTime||!Number.isInteger(buffer)||buffer<0||buffer>180||!Number.isInteger(fallback)||fallback<1||fallback>360){this.setData({error:'请检查时间和预估分钟数'});return}
  this.setData({busy:true,error:''})
  try{await api.post('/commute/settings/save',{...s,bufferMinutes:buffer,fallbackMinutes:fallback});this.setData({tab:'records'});await this.refresh()}catch(error){this.setData({error:error.message||'保存失败'})}finally{this.setData({busy:false})}
 },
 locationPrefix(e){this.setData({locationPrefix:e.currentTarget.dataset.value,locations:[],locationQuery:''})},
 locationMode(e){this.setData({locationMode:e.detail.value==='0'?'city':'share',locations:[]})},
 locationInput(e){this.setData({locationQuery:e.detail.value})},
 async search(){
  if(this.data.locationBusy)return
  const query=this.data.locationQuery.trim()
  if(!query){this.setData({error:'请输入区县或高德地点分享链接'});return}
  this.setData({locationBusy:true,error:'',locations:[]})
  try{
   if(this.data.locationMode==='share'){const url=query.match(/https:\/\/surl\.amap\.com\/[A-Za-z0-9]+/)?.[0];if(!url)throw Error('请粘贴高德地点分享链接');const location=await api.post('/commute/resolve-share',{url});this.chooseLocation(location)}
   else {const locations=await api.post('/commute/cities',{keyword:query});this.setData({locations:Array.isArray(locations)?locations:[],...(!locations?.length?{error:'没有找到地区'}:{})})}
  }catch(error){this.setData({error:error.message||'查询失败'})}
  finally{this.setData({locationBusy:false})}
 },
 selectLocation(e){const item=this.data.locations[Number(e.currentTarget.dataset.index)];if(item)this.chooseLocation(item)},
 chooseLocation(item){const prefix=this.data.locationPrefix,name=item.latitude!==undefined?item.name:`${item.adm1}·${item.adm2}·${item.name}`;this.setData({[`settings.${prefix}Name`]:name,[`settings.${prefix}Lat`]:Number(item.latitude??item.lat),[`settings.${prefix}Lon`]:Number(item.longitude??item.lon),locations:[],locationQuery:''})}
})
