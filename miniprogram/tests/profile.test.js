const test=require('node:test'),assert=require('node:assert/strict')
const {avatarUrl,isGuest,roleText,birthdayText}=require('../utils/profile')
test('local default avatars and stable fallback match the web selection',()=>{
 assert.equal(avatarUrl({avatar:'/avatars/01.png'}),'/assets/avatars/01.webp')
 assert.equal(avatarUrl({id:'123'}),avatarUrl({id:'123'}))
 assert.match(avatarUrl({id:'123'}),/^\/assets\/avatars\/\d{2}\.webp$/)
})
test('recognizes roles and respects lunar and solar birthdays',()=>{
 assert.equal(isGuest({id:'guest'}),true)
 assert.equal(roleText({userRole:'super_admin'}),'超级管理员')
 assert.equal(roleText({memberRole:'owner'}),'家庭所有者')
 assert.equal(birthdayText({birthdayCalendar:'LUNAR',lunarBirthdayYear:1999,lunarBirthdayMonth:3,lunarBirthdayDay:9,lunarBirthdayLeap:true}),'农历1999年闰3月9日')
 assert.equal(birthdayText({birthday:'2026-01-02'}),'公历2026-01-02')
})

