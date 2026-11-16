const api=require('../../services/api')
const session=require('../../services/session')
const {isGuest}=require('../../utils/profile')
const {notices,roles,empty,fromUser,build}=require('../../utils/memberForm')
Page({
  data:{form:empty(),notices,roles,noticeIndex:0,roleIndex:0,loading:false,saving:false,error:'',ready:false,isEditing:false,roleEditable:false,usernameLocked:false,avatarOptions:Array.from({length:15},(_,i)=>{const num=String(i+1).padStart(2,'0');return {path:`/avatars/${num}.png`,image:`/assets/avatars/${num}.webp`}})},
  onLoad(options){
    const user=session.get()
    if(!user?.token){wx.reLaunch({url:'/pages/login/index'});return}
    if(isGuest(user)){this.setData({error:'游客不能管理家庭成员'});return}
    this.user=user
    this.id=options.id||''
    if(this.id)this.load()
    else if(!['owner','admin'].includes(user.memberRole) && user.userRole!=='super_admin')this.setData({error:'只有家庭管理员可以添加家人'})
    else this.setData({roleEditable:user.memberRole==='owner',isEditing:false,ready:true})
  },
  async load(){
    this.setData({loading:true,error:''})
    try{
      let current=1,records=[],total=0
      do{
        const page=await api.post('/user/page',{current,size:50})
        const list=page?.records||[]
        records=records.concat(list);total=Number(page?.total||0);current++
        if(!list.length)break
      }while(records.length<total)
      const member=records.find(item=>item.id===this.id)
      if(!member)throw Error('家庭成员不存在或无权查看')
      const manager=['owner','admin'].includes(this.user.memberRole)||this.user.userRole==='super_admin'
      if(isGuest(member)||(!manager && member.id!==this.user.id)||(member.userRole==='super_admin'&&this.user.userRole!=='super_admin'))throw Error('无权编辑此成员')
      const form=fromUser(member)
      this.setData({form,isEditing:true,ready:true,usernameLocked:member.username==='王师傅',roleEditable:this.user.memberRole==='owner' && member.memberRole!=='owner',roleIndex:Math.max(0,roles.findIndex(item=>item.value===form.memberRole)),noticeIndex:Math.max(0,notices.findIndex(item=>item.value===form.notificationPreference))})
    }catch(error){this.setData({error:error.message||'成员加载失败'})}
    finally{this.setData({loading:false})}
  },
  onInput(e){const key=e.currentTarget.dataset.field;if(['username','nickname','publicName','email','password','lunarBirthdayYear','lunarBirthdayMonth','lunarBirthdayDay','remark'].includes(key))this.setData({[`form.${key}`]:e.detail.value})},
  changeBirthday(e){this.setData({'form.birthday':e.detail.value})},
  calendar(e){this.setData({'form.birthdayCalendar':e.currentTarget.dataset.value})},
  leap(e){this.setData({'form.lunarBirthdayLeap':!!e.detail.value.length})},
  role(e){const index=Number(e.detail.value);if(this.data.roleEditable && roles[index])this.setData({roleIndex:index,'form.memberRole':roles[index].value})},
  notice(e){const index=Number(e.detail.value);if(notices[index])this.setData({noticeIndex:index,'form.notificationPreference':notices[index].value})},
  avatar(e){this.setData({'form.avatar':e.currentTarget.dataset.path})},
  async save(){
    if(this.data.saving || this.data.loading || !this.data.ready)return
    let payload
    try{payload=build(this.data.form)}catch(error){this.setData({error:error.message});return}
    this.setData({saving:true,error:''})
    try{
      await api.post(this.id?'/user/update':'/user/create',payload)
      if(this.id===this.user.id){const updated={...this.user,nickname:payload.nickname,username:payload.username,avatar:payload.avatar,memberRole:payload.memberRole};session.set(updated)}
      this.setData({'form.password':''})
      wx.showToast({title:this.id?'已保存':'已添加',icon:'success'})
      wx.navigateBack()
    }catch(error){this.setData({error:error.message||'保存失败'})}
    finally{this.setData({saving:false})}
  }
})
