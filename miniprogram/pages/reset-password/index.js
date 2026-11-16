const api = require('../../services/api')
const session = require('../../services/session')
function extractToken(value) {
  const input=String(value||'').trim()
  if (/^[A-Za-z0-9_-]{43}$/.test(input)) return input
  const match=input.match(/[?&]token=([A-Za-z0-9_-]{43})(?:[&#\s]|$)/)
  return match ? match[1] : ''
}
Page({
  data:{state:'request',email:'',link:'',password:'',confirmation:'',busy:false,error:'',cooldown:0},
  onUnload(){clearInterval(this.timer);this.resetToken=''},
  input(e){const field=e.currentTarget.dataset.field;if(['email','link','password','confirmation'].includes(field))this.setData({[field]:e.detail.value,error:''})},
  async request(){
    if(this.data.busy||this.data.cooldown)return
    const email=this.data.email.trim()
    if(!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)||email.length>200){this.setData({error:'请输入有效的邮箱地址'});return}
    this.setData({busy:true,error:''})
    try{await api.post('/user/security/requestReset',{email},{public:true});this.setData({state:'sent',cooldown:60});clearInterval(this.timer);this.timer=setInterval(()=>{const cooldown=Math.max(0,this.data.cooldown-1);this.setData({cooldown});if(!cooldown)clearInterval(this.timer)},1000)}
    catch(error){this.setData({error:error.message||'暂时无法发送邮件'})}
    finally{this.setData({busy:false})}
  },
  enterLink(){this.setData({state:'link',link:'',error:''})},
  requestAgain(){if(!this.data.cooldown)this.setData({state:'request',error:''})},
  async checkLink(){
    if(this.data.busy)return
    const token=extractToken(this.data.link)
    if(!token){this.setData({error:'请输入邮件中的完整重置链接'});return}
    this.setData({busy:true,error:''})
    try{await api.post('/user/security/resetInfo',{token},{public:true});this.resetToken=token;this.setData({state:'reset',link:'',password:'',confirmation:''})}
    catch(error){this.setData({error:error.message||'重置链接无效或已过期'})}
    finally{this.setData({busy:false})}
  },
  async confirm(){
    if(this.data.busy)return
    const {password,confirmation}=this.data
    if(!this.resetToken){this.setData({state:'link',error:'请重新验证重置链接'});return}
    if(password.length<8||password.length>64){this.setData({error:'密码长度须为 8 至 64 位'});return}
    if(password!==confirmation){this.setData({error:'两次输入的密码不一致'});return}
    this.setData({busy:true,error:''})
    try{await api.post('/user/security/confirmReset',{token:this.resetToken,newPassword:password},{public:true});session.clear();this.resetToken='';this.setData({state:'done',password:'',confirmation:''})}
    catch(error){this.setData({error:error.message||'密码重置失败'})}
    finally{this.setData({busy:false})}
  },
  back(){wx.navigateBack({fail:()=>wx.reLaunch({url:'/pages/login/index'})})}
})
module.exports={extractToken}
