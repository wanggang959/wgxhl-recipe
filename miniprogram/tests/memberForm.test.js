const test=require('node:test'),assert=require('node:assert/strict')
const {empty,fromUser,build}=require('../utils/memberForm')
test('new member permits empty email and preserves lunar birthday and preference',()=>{
 const form={...empty(),username:'tester',password:'test-only-password',birthdayCalendar:'LUNAR',lunarBirthdayYear:'1998',lunarBirthdayMonth:'5',lunarBirthdayDay:'12',lunarBirthdayLeap:true,notificationPreference:'site,email'}
 const payload=build(form)
 assert.equal(payload.email,'');assert.equal(payload.lunarBirthdayYear,1998);assert.equal(payload.lunarBirthdayLeap,true);assert.equal(payload.notificationPreference,'site,email');assert.equal(payload.birthday,null)
})
test('editing does not send email or password and preserves original status and role',()=>{
 const form=fromUser({id:'abc',username:'member',email:'a@example.com',memberRole:'admin',status:'disabled',birthday:'2000-01-02'})
 const payload=build(form)
 assert.equal(payload.id,'abc');assert.equal(payload.status,'disabled');assert.equal(payload.memberRole,'admin')
 assert.equal('email' in payload,false);assert.equal('password' in payload,false)
})
test('validates email, password, and lunar date before submission',()=>{
 assert.throws(()=>build({...empty(),username:'a',password:'testpassword',email:'invalid'}),/邮箱/)
 assert.throws(()=>build({...empty(),username:'a',password:'short'}),/密码/)
 assert.throws(()=>build({...empty(),username:'a',password:'testpassword',birthdayCalendar:'LUNAR',lunarBirthdayYear:'2000',lunarBirthdayMonth:'13'}),/农历/)
})
test('legacy nullable profile fields can be edited',()=>{
 const payload=build(fromUser({id:'abc',username:'member',nickname:null,publicName:null}))
 assert.equal(payload.nickname,'member');assert.equal(payload.publicName,'')
})
