const api=require('../../services/api')
const session=require('../../services/session')
Page({
  data:{mode:'email',email:'',code:'',username:'',nickname:'',password:'',confirmPassword:'',inviteCode:'',inviteInput:'',inviteInfo:null,sending:false,checking:false,submitting:false,countdown:0,error:'',bindWechat:false},
  onLoad(options={}){if(session.get()?.token){wx.switchTab({url:'/pages/home/index'});return}this.setData({bindWechat:options.bindWechat==='1'})},
  onUnload(){this.stopCountdown()},
  switchMode(e){const mode=e.currentTarget.dataset.mode;if(!['email','invite'].includes(mode)||mode===this.data.mode)return;this.setData({mode,error:''})},
  field(e){const field=e.currentTarget.dataset.field;if(field)this.setData({[field]:e.detail.value,error:''})},
  stopCountdown(){if(this.timer)clearInterval(this.timer);this.timer=null},
  startCountdown(){this.stopCountdown();this.setData({countdown:60});this.timer=setInterval(()=>{const next=this.data.countdown-1;if(next<=0){this.stopCountdown();this.setData({countdown:0})}else this.setData({countdown:next})},1000)},
  isValidEmail(email){return email.length<=200&&/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)},
  isValidUsername(username){return /^[\u4e00-\u9fa5A-Za-z0-9_-]{2,32}$/.test(username)},
  validateAccount(){
    const username=this.data.username.trim()
    if(!this.isValidUsername(username)){this.setData({error:'用户名需为 2-32 位中文、字母、数字、下划线或短横线'});return false}
    if(this.data.password.length<8||this.data.password.length>64){this.setData({error:'密码长度须为 8 至 64 位'});return false}
    if(this.data.password!==this.data.confirmPassword){this.setData({error:'两次输入的密码不一致'});return false}
    return true
  },
  async sendCode(){
    if(this.data.sending||this.data.countdown>0)return
    const email=this.data.email.trim().toLowerCase()
    if(!this.isValidEmail(email)){this.setData({error:'请输入有效的邮箱地址'});return}
    this.setData({sending:true,error:''})
    try{
      await api.post('/user/register/sendCode',{email},{public:true})
      wx.showToast({title:'验证码已发送',icon:'none'})
      this.startCountdown()
    }catch(error){this.setData({error:error.message||'验证码发送失败'})}
    finally{this.setData({sending:false})}
  },
  async checkInvite(){
    if(this.data.checking)return
    const inviteCode=this.data.inviteInput.trim().toUpperCase()
    if(!inviteCode){this.setData({error:'请输入邀请码'});return}
    this.setData({checking:true,error:'',inviteInfo:null})
    try{
      const info=await api.post('/user/register/inviteInfo',{inviteCode},{public:true})
      this.setData({inviteInfo:info||null})
    }catch(error){this.setData({error:error.message||'邀请码无效'})}
    finally{this.setData({checking:false})}
  },
  async submit(){
    if(this.data.submitting)return
    if(this.data.mode==='email')await this.submitEmail()
    else await this.submitInvite()
  },
  async submitEmail(){
    const email=this.data.email.trim().toLowerCase()
    if(!this.isValidEmail(email)){this.setData({error:'请输入有效的邮箱地址'});return}
    if(!this.data.code.trim()){this.setData({error:'请输入验证码'});return}
    if(!this.validateAccount())return
    this.setData({submitting:true,error:''})
    try{
      const user=await api.post('/user/register/email',{email,code:this.data.code.trim(),username:this.data.username.trim(),nickname:this.data.nickname.trim(),password:this.data.password,inviteCode:this.data.inviteCode.trim()},{public:true})
      await this.enterApp(user)
    }catch(error){this.setData({error:error.message||'注册失败'})}
    finally{this.setData({submitting:false})}
  },
  async submitInvite(){
    const inviteCode=this.data.inviteInput.trim().toUpperCase()
    if(!inviteCode){this.setData({error:'请输入邀请码'});return}
    if(!this.validateAccount())return
    this.setData({submitting:true,error:''})
    try{
      const user=await api.post('/user/register/invite',{inviteCode,username:this.data.username.trim(),nickname:this.data.nickname.trim(),password:this.data.password},{public:true})
      await this.enterApp(user)
    }catch(error){this.setData({error:error.message||'注册失败'})}
    finally{this.setData({submitting:false})}
  },
  async enterApp(user){
    if(!user?.id||!user?.token){this.setData({error:'注册结果不完整，请稍后重试'});return}
    session.set(user)
    if(this.data.bindWechat){
      try{
        const code=await new Promise((resolve,reject)=>wx.login({success:r=>r.code?resolve(r.code):reject(new Error('获取微信登录凭证失败')),fail:()=>reject(new Error('微信登录暂不可用'))}))
        await api.post('/user/wechat/bind',{code,username:this.data.username.trim(),password:this.data.password})
      }catch(_){
        this.setData({password:'',confirmPassword:''})
        wx.showModal({title:'账号已注册',content:'微信绑定未完成。账号已创建，可在“我的 → 微信绑定”中重试。',showCancel:false,success:()=>wx.switchTab({url:'/pages/home/index'})})
        return
      }
    }
    this.setData({password:'',confirmPassword:''})
    wx.showToast({title:'注册成功',icon:'none'})
    wx.switchTab({url:'/pages/home/index'})
  },
  goLogin(){wx.navigateBack({fail:()=>wx.reLaunch({url:'/pages/login/index'})})}
})
