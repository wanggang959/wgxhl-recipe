const api=require('../../services/api')
const session=require('../../services/session')
Page({
 data:{userId:'',loading:false,busy:false,mode:'',memberName:'',error:'',done:false,currentPassword:'',password:'',confirmation:''},
 onLoad(options){const user=session.get();if(!user?.token){wx.reLaunch({url:'/pages/login/index'});return}if(user.memberRole!=='owner'||!options.id||options.id===user.id){this.setData({error:'无权重置此成员密码'});return}this.setData({userId:options.id});this.load()},
 async load(){
  if(this.data.loading||!this.data.userId)return
  this.setData({loading:true,error:''})
  try{const result=await api.post('/user/security/member/options',{userId:this.data.userId});this.setData({mode:result?.mode||'',memberName:result?.memberName||''})}
  catch(error){this.setData({error:error.message||'无法读取重置权限'})}
  finally{this.setData({loading:false})}
 },
 input(e){const field=e.currentTarget.dataset.field;if(['currentPassword','password','confirmation'].includes(field))this.setData({[field]:e.detail.value,error:''})},
 submit(){
  if(this.data.busy||!['DIRECT','EMAIL'].includes(this.data.mode))return
  const {mode,currentPassword,password,confirmation,userId}=this.data
  if(mode==='DIRECT' && !currentPassword){this.setData({error:'请输入你的当前密码'});return}
  if(mode==='DIRECT' && (password.length<8||password.length>64)){this.setData({error:'密码长度须为 8 至 64 位'});return}
  if(mode==='DIRECT' && password!==confirmation){this.setData({error:'两次输入的密码不一致'});return}
  wx.showModal({title:'确认重置密码',content:mode==='DIRECT'?'重置后，成员所有设备上的旧登录都会失效。确认继续吗？':'将向成员本人已验证的邮箱发送重置链接，由成员自行设置新密码。确认发送吗？',success:async ({confirm})=>{
   if(!confirm||this.data.busy)return
   this.setData({busy:true,error:''})
   try{
    if(mode==='DIRECT')await api.post('/user/security/member/resetManaged',{userId,currentPassword,newPassword:password})
    else await api.post('/user/security/member/requestReset',{userId})
    this.setData({currentPassword:'',password:'',confirmation:'',done:true})
   }catch(error){this.setData({error:error.message||'重置失败'})}
   finally{this.setData({busy:false})}
  }})
 },
 back(){wx.navigateBack()},
 onUnload(){this.setData({currentPassword:'',password:'',confirmation:''})}
})
