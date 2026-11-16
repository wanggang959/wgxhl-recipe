const api=require('../../services/api')
const session=require('../../services/session')
const {isGuest}=require('../../utils/profile')
Page({
  data:{loading:false,busy:false,error:'',status:null,mode:'verify',request:null,done:false,newEmail:'',currentPassword:'',oldCode:'',newCode:''},
  onLoad(){const user=session.get();if(!user?.token){wx.reLaunch({url:'/pages/login/index'});return}if(isGuest(user)){this.setData({error:'游客不能管理邮箱'});return}this.load()},
  async load(){
    if(this.data.loading)return
    this.setData({loading:true,error:''})
    try{const status=await api.post('/user/security/status');if(!status)throw Error('无法读取账号信息');this.setData({status});this.setMode(status.verified?'overview':status.email?'verify':'bind')}
    catch(error){this.setData({error:error.message||'无法读取账号信息'})}
    finally{this.setData({loading:false})}
  },
  setMode(mode){this.setData({mode,request:null,error:'',newEmail:mode==='verify'?(this.data.status?.email||''):'',currentPassword:'',oldCode:'',newCode:''})},
  changeMode(e){if(!this.data.busy)this.setMode(e.currentTarget.dataset.mode)},
  onInput(e){const field=e.currentTarget.dataset.field;if(['newEmail','currentPassword','oldCode','newCode'].includes(field))this.setData({[field]:e.detail.value,error:''})},
  async send(){
    if(this.data.busy)return
    const newEmail=this.data.newEmail.trim()
    if(!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(newEmail)){this.setData({error:'请输入有效的邮箱地址'});return}
    if(!this.data.currentPassword){this.setData({error:'请输入生活助手登录密码'});return}
    this.setData({busy:true,error:''})
    try{const request=await api.post('/user/security/email/send',{newEmail,currentPassword:this.data.currentPassword});if(!request?.requestId)throw Error('验证码请求失败');this.setData({request,currentPassword:'',oldCode:'',newCode:''})}
    catch(error){this.setData({error:error.message||'邮件发送失败'})}
    finally{this.setData({busy:false})}
  },
  async confirm(){
    if(this.data.busy || !this.data.request)return
    if(!/^\d{6}$/.test(this.data.newCode) || (this.data.request.needsOldCode && !/^\d{6}$/.test(this.data.oldCode))){this.setData({error:'请输入 6 位验证码'});return}
    this.setData({busy:true,error:''})
    try{await api.post('/user/security/email/confirm',{requestId:this.data.request.requestId,oldCode:this.data.oldCode,newCode:this.data.newCode});session.clear();this.setData({done:true,request:null,oldCode:'',newCode:'',currentPassword:''})}
    catch(error){this.setData({error:error.message||'邮箱验证失败'})}
    finally{this.setData({busy:false})}
  },
  revise(){if(!this.data.busy)this.setData({request:null,error:'',oldCode:'',newCode:''})},
  login(){wx.reLaunch({url:'/pages/login/index'})},
  onUnload(){this.setData({currentPassword:'',oldCode:'',newCode:''})}
})
