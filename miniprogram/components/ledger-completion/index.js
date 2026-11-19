const api=require('../../services/api')
const session=require('../../services/session')
Component({
 properties:{todo:{type:Object,value:null,observer(value){if(value)this.open(value)}}},
 data:{loading:false,busy:false,members:[],payerId:'',payerName:'',amount:'',error:''},
 methods:{
  async open(todo){const version=(this.version||0)+1;this.version=version;this.setData({loading:true,error:'',members:[],payerId:'',payerName:'',amount:todo.ledgerSetting.estimatedAmount==null?'':String(todo.ledgerSetting.estimatedAmount)});try{const members=await api.post('/user/members')||[];if(version!==this.version)return;const member=members.find(m=>m.id===session.get()?.id);this.setData({members,payerId:member?.id||'',payerName:member?.nickname||member?.username||''})}catch(error){if(version===this.version)this.setData({error:error.message||'家庭成员加载失败'})}finally{if(version===this.version)this.setData({loading:false})}},
  close(){if(this.data.busy)return;this.version=(this.version||0)+1;this.triggerEvent('close')},swallow(){},
  payer(e){const member=this.data.members[Number(e.detail.value)];if(member)this.setData({payerId:member.id,payerName:member.nickname||member.username})},
  amount(e){this.setData({amount:e.detail.value})},
  async finish(e){const record=e.currentTarget.dataset.record==='yes',todo=this.properties.todo;if(this.data.busy||!todo)return;if(record&&(!this.data.payerId||!/^\d+(\.\d{1,2})?$/.test(this.data.amount)||Number(this.data.amount)<=0)){this.setData({error:'请选择付款人并填写有效金额'});return}this.setData({busy:true,error:''});try{await api.post(record?'/todo/complete-with-ledger':'/todo/complete',record?{id:todo.id,expectedDueTime:todo.dueTime,payerId:this.data.payerId,actualAmount:Number(this.data.amount)}:{id:todo.id});wx.showToast({title:record?'已完成并记账':'已完成',icon:'none'});this.triggerEvent('done')}catch(error){this.setData({error:error.message||'操作失败'})}finally{this.setData({busy:false})}}
 }
})
