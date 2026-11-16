const api=require('../../services/api')
const session=require('../../services/session')
const {isGuest}=require('../../utils/profile')
const projectTypes=[{value:'WEDDING',label:'婚礼'},{value:'DECORATION',label:'装修'},{value:'MOVE',label:'搬家'},{value:'TRAVEL',label:'旅行'},{value:'BABY',label:'备产'},{value:'OTHER',label:'其他'}]
const categories=[{value:'',label:'全部'},{value:'FOOD',label:'食品'},{value:'CLOTHES',label:'服饰'},{value:'JEWELRY',label:'首饰'},{value:'HOME_TEXTILE',label:'家纺'},{value:'APPLIANCE',label:'家电'},{value:'BANQUET',label:'酒席'},{value:'HOME',label:'婚房'},{value:'DOCUMENT',label:'证件'},{value:'SUPPLY',label:'用品'},{value:'OTHER',label:'其他'}]
const statuses=[{value:'',label:'全部'},{value:'CONFIRM',label:'待确认'},{value:'TODO',label:'待购买'},{value:'ORDERED',label:'已下单'},{value:'RECEIVED',label:'已到货'},{value:'DONE',label:'已完成'},{value:'CANCELLED',label:'已取消'}]
function number(value){const text=String(value??'').trim();if(!text)return '0';if(!/^\d+(\.\d{1,2})?$/.test(text))throw Error('金额须为非负数字，最多两位小数');return text}
function projectBlank(){return {id:'',projectName:'',projectType:'WEDDING',totalBudget:'',startDate:'',endDate:'',remark:''}}
function itemBlank(projectId,userId){return {id:'',projectId,itemName:'',category:'OTHER',budgetAmount:'',actualAmount:'',quantity:'',status:'TODO',payerId:userId,ownerId:userId,purchaseDate:'',plannedTime:'',purchaseChannel:'',purchaseLink:'',remark:'',imageUrl:''}}
function optionsForItem(options,item){
 const result=options.slice()
 for(const [id,name] of [[item?.ownerId,item?.ownerName],[item?.payerId,item?.payerName]])if(id&&!result.some(option=>option.id===id))result.push({id,name:name||'原成员（不可用）'})
 return result
}
Page({
 data:{user:null,canWrite:false,view:'list',projects:[],project:null,selectedId:'',members:[],membersLoaded:false,items:[],summary:null,page:0,total:0,loading:false,itemsLoading:false,busy:false,error:'',showArchived:false,category:'',status:'',projectTypes,categories,statuses,itemCategories:categories.slice(1),itemStatuses:statuses.slice(1),projectForm:projectBlank(),itemForm:itemBlank('',''),plannedDate:'',plannedClock:'',typesIndex:0,categoryIndex:0,statusIndex:0,payerIndex:0,ownerIndex:0,memberOptions:[]},
 onLoad(){const user=session.get();if(!user?.token){wx.reLaunch({url:'/pages/login/index'});return}this.setData({user,canWrite:!isGuest(user)});this.initialize()},
 async initialize(){try{const members=await api.post('/user/members');if(!Array.isArray(members))throw Error('家庭成员数据异常');this.setData({members,membersLoaded:true,memberOptions:[{id:'',name:'不指定'},...members.map(m=>({id:m.id,name:m.nickname||m.username||'家人'}))]})}catch(_){this.setData({membersLoaded:false});wx.showToast({title:'家庭成员加载失败，请重新进入',icon:'none'})}await this.loadProjects()},
 async loadProjects(){
  if(this.data.loading)return
  this.setData({loading:true,error:''})
  try{
   let current=1,records=[],total=0
   do{const result=await api.post('/expense/project/page',{current,size:50,archived:this.data.showArchived?null:false});const batch=result?.records||[];records=records.concat(batch);total=Number(result?.total||0);current++;if(!batch.length)break}while(records.length<total)
   const user=this.data.user,manager=['owner','admin'].includes(user.memberRole),projects=records.map(p=>({...p,typeLabel:projectTypes.find(t=>t.value===p.projectType)?.label||'其他',canManage:!isGuest(user)&&(manager||p.createUserId===user.id)}))
   const selected=projects.find(p=>p.id===this.data.selectedId)||projects[0]||null
   this.setData({projects,project:selected,selectedId:selected?.id||''})
   await this.loadItems(true)
  }catch(error){this.setData({error:error.message||'专项开支加载失败'})}finally{this.setData({loading:false})}
 },
 async loadItems(reset=false){
  if(!this.data.selectedId){this.setData({items:[],page:0,total:0,summary:null});return}
  if(this.data.itemsLoading&&!reset)return
  const version=(this.itemsVersion||0)+1;this.itemsVersion=version
  const current=reset?1:this.data.page+1,projectId=this.data.selectedId
  this.setData({itemsLoading:true,error:''})
  try{
   const params={projectId,category:this.data.category,status:this.data.status}
   const [result,summary]=await Promise.all([api.post('/expense/item/page',{...params,current,size:20}),api.post('/expense/project/summary',{id:projectId,category:this.data.category,status:this.data.status})])
   if(version!==this.itemsVersion)return
   const user=this.data.user,manager=['owner','admin'].includes(user.memberRole),project=this.data.project
   const records=(result?.records||[]).map(item=>({...item,categoryLabel:categories.find(c=>c.value===item.category)?.label||'其他',statusLabel:statuses.find(s=>s.value===item.status)?.label||item.status,canManage:!isGuest(user)&&(manager||item.ownerId===user.id||project?.createUserId===user.id)}))
   this.setData({items:reset?records:this.data.items.concat(records),page:current,total:records.length?Number(result?.total||0):(reset?0:this.data.items.length),summary})
  }catch(error){if(version===this.itemsVersion)this.setData({error:error.message||'开支清单加载失败'})}
  finally{if(version===this.itemsVersion)this.setData({itemsLoading:false})}
 },
 onReachBottom(){if(this.data.view==='list'&&!this.data.itemsLoading&&this.data.items.length<this.data.total)this.loadItems(false)},
 chooseProject(e){const project=this.data.projects.find(p=>p.id===e.currentTarget.dataset.id);if(!project||project.id===this.data.selectedId)return;this.setData({project,selectedId:project.id,items:[],page:0,total:0,summary:null,category:'',status:''});this.loadItems(true)},
 toggleArchived(){this.setData({showArchived:!this.data.showArchived});this.loadProjects()},
 filter(e){const kind=e.currentTarget.dataset.kind,value=e.currentTarget.dataset.value;if(kind==='category'||kind==='status'){this.setData({[kind]:value,items:[],page:0,total:0,summary:null});this.loadItems(true)}},
 projectEdit(e){if(isGuest(this.data.user))return;const project=e.currentTarget.dataset.id?this.data.project:null;if(project&&!project.canManage)return;const form=project?{...project,totalBudget:String(project.totalBudget??''),startDate:project.startDate||'',endDate:project.endDate||'',remark:project.remark||''}:projectBlank();this.setData({view:'project',projectForm:form,typesIndex:Math.max(0,projectTypes.findIndex(t=>t.value===form.projectType)),error:''})},
 itemEdit(e){if(isGuest(this.data.user)||!this.data.project)return;if(!this.data.membersLoaded){wx.showToast({title:'成员加载失败，请重新进入',icon:'none'});return}const item=this.data.items.find(i=>i.id===e.currentTarget.dataset.id);if(item&&!item.canManage)return;const options=optionsForItem(this.data.memberOptions,item);const form=item?{...item,budgetAmount:String(item.budgetAmount??''),actualAmount:String(item.actualAmount??''),quantity:item.quantity||'',purchaseDate:item.purchaseDate||'',plannedTime:item.plannedTime||'',purchaseChannel:item.purchaseChannel||'',purchaseLink:item.purchaseLink||'',remark:item.remark||'',imageUrl:item.imageUrl||''}:itemBlank(this.data.selectedId,this.data.user.id);const planned=String(form.plannedTime).replace(' ','T');this.setData({view:'item',itemForm:form,memberOptions:options,plannedDate:planned.slice(0,10),plannedClock:planned.slice(11,16),categoryIndex:Math.max(0,categories.slice(1).findIndex(c=>c.value===form.category)),statusIndex:Math.max(0,statuses.slice(1).findIndex(s=>s.value===form.status)),payerIndex:Math.max(0,options.findIndex(m=>m.id===form.payerId)),ownerIndex:Math.max(0,options.findIndex(m=>m.id===form.ownerId)),error:''})},
 field(e){const group=this.data.view==='project'?'projectForm':'itemForm',field=e.currentTarget.dataset.field;const allowed=group==='projectForm'?['projectName','totalBudget','startDate','endDate','remark']:['itemName','budgetAmount','actualAmount','quantity','purchaseDate','plannedTime','purchaseChannel','purchaseLink','remark','imageUrl'];if(allowed.includes(field))this.setData({[`${group}.${field}`]:e.detail.value})},
 pick(e){const kind=e.currentTarget.dataset.kind,index=Number(e.detail.value);if(kind==='type'&&projectTypes[index])this.setData({typesIndex:index,'projectForm.projectType':projectTypes[index].value});if(kind==='category'&&this.data.itemCategories[index])this.setData({categoryIndex:index,'itemForm.category':this.data.itemCategories[index].value});if(kind==='status'&&this.data.itemStatuses[index])this.setData({statusIndex:index,'itemForm.status':this.data.itemStatuses[index].value});if(kind==='owner'&&this.data.memberOptions[index])this.setData({ownerIndex:index,'itemForm.ownerId':this.data.memberOptions[index].id});if(kind==='payer'&&this.data.memberOptions[index])this.setData({payerIndex:index,'itemForm.payerId':this.data.memberOptions[index].id})},
 planned(e){const field=e.currentTarget.dataset.field;if(field==='plannedDate'||field==='plannedClock')this.setData({[field]:e.detail.value})},
 clearPlanned(){this.setData({plannedDate:'',plannedClock:''})},
 async save(){
  if(this.data.busy||isGuest(this.data.user))return
  const project=this.data.view==='project',f=project?this.data.projectForm:this.data.itemForm
  let payload
  try{
   if(project){if(!f.projectName.trim())throw Error('请输入专项名称');payload={id:f.id||undefined,projectName:f.projectName.trim(),projectType:f.projectType,totalBudget:number(f.totalBudget),startDate:f.startDate||null,endDate:f.endDate||null,remark:f.remark.trim()}}
   else{if(!f.itemName.trim())throw Error('请输入开支名称');if(Boolean(this.data.plannedDate)!==Boolean(this.data.plannedClock))throw Error('请同时选择提醒日期和时间');if(f.id && f.plannedTime && !this.data.plannedDate)throw Error('暂不支持清除已有提醒时间');payload={...f,id:f.id||undefined,projectId:this.data.selectedId,itemName:f.itemName.trim(),budgetAmount:number(f.budgetAmount),actualAmount:number(f.actualAmount),payerId:f.payerId||null,ownerId:f.ownerId||null,purchaseDate:f.purchaseDate||null,plannedTime:this.data.plannedDate?`${this.data.plannedDate}T${this.data.plannedClock}:00`:null};delete payload.canManage;delete payload.categoryLabel;delete payload.statusLabel}
  }catch(error){this.setData({error:error.message});return}
  this.setData({busy:true,error:''})
  try{const result=await api.post(project?(f.id?'/expense/project/update':'/expense/project/create'):(f.id?'/expense/item/update':'/expense/item/create'),payload);if(project&&!f.id&&result?.id)this.setData({selectedId:result.id});this.setData({view:'list'});await this.loadProjects();wx.showToast({title:'已保存',icon:'success'})}
  catch(error){this.setData({error:error.message||'保存失败'})}
  finally{this.setData({busy:false})}
 },
 cancel(){if(!this.data.busy)this.setData({view:'list',error:''})},
 archive(){const project=this.data.project;if(!project?.canManage||this.data.busy)return;wx.showModal({title:project.archived?'恢复专项':'归档专项',content:`确认${project.archived?'恢复':'归档'}「${project.projectName}」吗？`,success:async({confirm})=>{if(!confirm||this.data.busy)return;this.setData({busy:true});try{await api.post(project.archived?'/expense/project/restore':'/expense/project/archive',{id:project.id});await this.loadProjects()}catch(error){this.setData({error:error.message||'操作失败'})}finally{this.setData({busy:false})}}})},
 remove(e){const item=this.data.items.find(i=>i.id===e.currentTarget.dataset.id);if(!item?.canManage||this.data.busy)return;wx.showModal({title:'删除开支项',content:`确认删除「${item.itemName}」吗？`,confirmText:'删除',confirmColor:'#d64335',success:async({confirm})=>{if(!confirm||this.data.busy)return;this.setData({busy:true});try{await api.post('/expense/item/delete',{id:item.id});await this.loadItems(true)}catch(error){this.setData({error:error.message||'删除失败'})}finally{this.setData({busy:false})}}})}
})
module.exports={number,optionsForItem}
