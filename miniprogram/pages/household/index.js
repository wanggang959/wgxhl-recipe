const api=require('../../services/api')
const session=require('../../services/session')
const {isGuest}=require('../../utils/profile')
const roleLabels={owner:'家庭所有者',admin:'家庭管理员',member:'家人',guest:'游客'}
Page({
 data:{user:null,households:[],invites:[],members:[],loading:false,busy:false,error:'',name:'',joinCode:'',inviteRole:'member',transferIndex:0,transferOptions:[],canManage:false,isOwner:false},
 onLoad(){const user=session.get();if(!user?.token){wx.reLaunch({url:'/pages/login/index'});return}if(isGuest(user)){this.setData({error:'游客不能管理家庭'});return}this.setData({user,canManage:['owner','admin'].includes(user.memberRole),isOwner:user.memberRole==='owner'});this.load()},
 async load(){
  if(this.data.loading)return
  this.setData({loading:true,error:''})
  try{
   const calls=[api.post('/household/list'),api.post('/user/members')]
   if(this.data.canManage)calls.push(api.post('/household/invite/list'))
   const [households,members,invites]=await Promise.all(calls)
   if(!Array.isArray(households)||!Array.isArray(members))throw Error('家庭数据格式异常')
   const transferOptions=members.filter(m=>m.id!==this.data.user.id&&!isGuest(m))
   this.setData({households:households.map(item=>({...item,roleLabel:roleLabels[item.memberRole]||'家人'})),members,transferOptions,invites:(invites||[]).map(item=>({...item,expiresLabel:String(item.expiresAt||'').replace('T',' ').slice(0,16)||'长期有效',roleLabel:roleLabels[item.memberRole]||'家人'}))})
  }catch(error){this.setData({error:error.message||'家庭信息加载失败'})}
  finally{this.setData({loading:false})}
 },
 input(e){const field=e.currentTarget.dataset.field;if(['name','joinCode'].includes(field))this.setData({[field]:e.detail.value,error:''})},
 role(e){const role=e.currentTarget.dataset.role;if(role==='member'||(role==='admin'&&this.data.isOwner))this.setData({inviteRole:role})},
 chooseTransfer(e){this.setData({transferIndex:Number(e.detail.value)})},
 async doChange(path,payload){
  if(this.data.busy)return
  this.setData({busy:true,error:''})
  try{const user=await api.post(path,payload);if(!user?.id||!user?.token)throw Error('操作已完成，请重新登录以刷新家庭状态');session.set(user);wx.switchTab({url:'/pages/profile/index'})}
  catch(error){this.setData({error:error.message||'操作失败'})}
  finally{this.setData({busy:false})}
 },
 switchHousehold(e){
  const item=this.data.households.find(h=>h.id===e.currentTarget.dataset.id)
  if(!item||this.data.busy)return
  if(item.active){wx.navigateBack();return}
  this.doChange('/household/switch',{id:item.id})
 },
 create(){
  const name=this.data.name.trim()
  if(!name || name.length>64){this.setData({error:'请输入不超过64字的家庭名称'});return}
  this.doChange('/household/create',{householdName:name})
 },
 async invite(){
  if(this.data.busy||!this.data.canManage)return
  this.setData({busy:true,error:''})
  try{await api.post('/household/invite/create',{memberRole:this.data.inviteRole,maxUses:1,expiresInHours:72});await this.load()}
  catch(error){this.setData({error:error.message||'生成邀请码失败'})}
  finally{this.setData({busy:false})}
 },
 revoke(e){
  const invite=this.data.invites.find(item=>item.id===e.currentTarget.dataset.id)
  if(!invite||this.data.busy||!this.data.canManage)return
  wx.showModal({title:'撤销邀请',content:'确认撤销这个邀请码吗？',success:async({confirm})=>{
   if(!confirm||this.data.busy)return
   this.setData({busy:true,error:''})
   try{await api.post('/household/invite/revoke',{id:invite.id});await this.load()}
   catch(error){this.setData({error:error.message||'撤销失败'})}
   finally{this.setData({busy:false})}
  }})
 },
 copy(e){const code=e.currentTarget.dataset.code;if(code)wx.setClipboardData({data:code})},
 async join(){
  if(this.data.busy)return
  const inviteCode=this.data.joinCode.trim().toUpperCase()
  if(!inviteCode){this.setData({error:'请输入邀请码'});return}
  this.setData({busy:true,error:''})
  try{
   const preview=await api.post('/household/invite/preview',{inviteCode})
   this.setData({busy:false})
   wx.showModal({title:'加入家庭',content:`确认加入「${preview?.householdName||'该家庭'}」吗？`,success:({confirm})=>{if(confirm)this.doChange('/household/join',{inviteCode})}})
  }catch(error){this.setData({busy:false,error:error.message||'邀请码无效'})}
 },
 transfer(){
  const target=this.data.transferOptions[this.data.transferIndex]
  if(!this.data.isOwner||!target||this.data.busy)return
  wx.showModal({title:'转让所有权',content:`确认将家庭所有权转让给「${target.nickname||target.username}」吗？转让后你将成为管理员。`,confirmText:'转让',success:({confirm})=>{if(confirm)this.doChange('/household/transfer',{targetUserId:target.id})}})
 },
 leave(){
  if(this.data.isOwner||this.data.busy)return
  wx.showModal({title:'退出家庭',content:'退出后，你在该家庭的待办、收藏和想吃等数据会被清理，个人菜谱会保留。确认退出吗？',confirmText:'退出',confirmColor:'#d64335',success:({confirm})=>{if(confirm)this.doChange('/household/leave',{})}})
 },
 dissolve(){
  if(!this.data.isOwner||this.data.busy)return
  wx.showModal({title:'解散家庭',content:'解散会删除该家庭的待办、专项开支、收藏等数据，且无法恢复。仅当家庭只剩你一人时可解散，确认继续吗？',confirmText:'解散',confirmColor:'#d64335',success:({confirm})=>{if(confirm)this.doChange('/household/dissolve',{})}})
 }
})
