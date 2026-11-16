const notices=[{value:'site',label:'仅站内'},{value:'email',label:'仅邮箱'},{value:'site,email',label:'站内+邮箱'},{value:'site,push',label:'站内+App'},{value:'site,email,push',label:'站内+邮箱+App'}]
const roles=[{value:'member',label:'家人'},{value:'admin',label:'家庭管理员'},{value:'owner',label:'家庭所有者'}]
function empty(){return {id:'',username:'',nickname:'',publicName:'',email:'',password:'',birthday:'',birthdayCalendar:'SOLAR',lunarBirthdayYear:'',lunarBirthdayMonth:'1',lunarBirthdayDay:'1',lunarBirthdayLeap:false,memberRole:'member',notificationPreference:'site',remark:'',avatar:'/avatars/01.png',status:'normal'}}
function fromUser(user){return {...empty(),...user,username:user.username||'',nickname:user.nickname||'',publicName:user.publicName||'',email:user.email||'',password:'',birthday:user.birthday||'',birthdayCalendar:user.birthdayCalendar||'SOLAR',lunarBirthdayYear:user.lunarBirthdayYear ? String(user.lunarBirthdayYear):'',lunarBirthdayMonth:String(user.lunarBirthdayMonth||1),lunarBirthdayDay:String(user.lunarBirthdayDay||1),lunarBirthdayLeap:!!user.lunarBirthdayLeap,memberRole:user.memberRole||'member',notificationPreference:user.notificationPreference||'site',remark:user.remark||'',avatar:user.avatar||'/avatars/01.png'}}
function build(form) {
 const username=form.username.trim(),nickname=form.nickname.trim()||username
 if(!username)throw Error('请输入用户名')
 if(form.publicName.trim().length>20)throw Error('公开昵称不能超过20个字符')
 const editing=Boolean(form.id),email=form.email.trim()
 if(!editing && email && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email))throw Error('请输入有效的邮箱地址')
 if(!editing && (form.password.length<8 || form.password.length>64))throw Error('密码长度须为 8 至 64 位')
 const lunar=form.birthdayCalendar==='LUNAR'
 const year=Number(form.lunarBirthdayYear),month=Number(form.lunarBirthdayMonth),day=Number(form.lunarBirthdayDay)
 if(lunar && (!Number.isInteger(year) || year<1900 || year>2100 || !Number.isInteger(month) || month<1 || month>12 || !Number.isInteger(day) || day<1 || day>30))throw Error('请检查农历出生年、月、日')
 const payload={username,nickname,publicName:form.publicName.trim(),birthday:lunar?null:(form.birthday||null),birthdayCalendar:form.birthdayCalendar,lunarBirthdayYear:lunar?year:null,lunarBirthdayMonth:lunar?month:null,lunarBirthdayDay:lunar?day:null,lunarBirthdayLeap:lunar&&form.lunarBirthdayLeap,memberRole:form.memberRole,notificationPreference:form.notificationPreference,remark:form.remark.trim(),avatar:form.avatar,status:form.status||'normal'}
 if(editing)payload.id=form.id
 else {payload.password=form.password;payload.email=email}
 return payload
}
module.exports={notices,roles,empty,fromUser,build}
