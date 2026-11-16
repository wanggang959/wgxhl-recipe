const config = require('../config')
function hashString(value) {
  let hash=0
  const text=String(value || '')
  for(let i=0;i<text.length;i++) hash=(hash*31+text.charCodeAt(i))|0
  return Math.abs(hash)
}
function avatarUrl(user = {}) {
  const avatar=String(user.avatar || '').trim()
  const local=avatar.match(/^\/(?:avatars)\/(\d{2})\.png$/)
  if(local) return `/assets/avatars/${local[1]}.webp`
  if(/^https:\/\//i.test(avatar))return avatar
  if(avatar && !/^https?:\/\//i.test(avatar))return `${config.apiBaseUrl.replace(/\/recipe$/, '')}/${avatar.replace(/^\/+/, '')}`
  const index=hashString(user.id || user.username || 'guest')%15+1
  return `/assets/avatars/${String(index).padStart(2,'0')}.webp`
}
function isGuest(user={}) {return user.id==='guest' || user.username==='guest' || user.userRole==='guest' || user.memberRole==='guest'}
function roleText(user={}) {
  if(isGuest(user))return '游客'
  if(user.userRole==='super_admin' || user.username==='王师傅')return '超级管理员'
  if(user.memberRole==='owner')return '家庭所有者'
  if(user.memberRole==='admin')return '家庭管理员'
  return '家人'
}
function birthdayText(user={}) {
  if(user.birthdayCalendar==='LUNAR' && user.lunarBirthdayMonth && user.lunarBirthdayDay) return `农历${user.lunarBirthdayYear || ''}${user.lunarBirthdayYear ? '年' : ''}${user.lunarBirthdayLeap ? '闰' : ''}${user.lunarBirthdayMonth}月${user.lunarBirthdayDay}日`
  return user.birthday ? `公历${String(user.birthday).slice(0,10)}` : '未填写生日'
}
function displayMember(user) {return {...user,avatarUrl:avatarUrl(user),roleLabel:roleText(user),birthdayLabel:birthdayText(user),statusLabel:user.status==='disabled' ? '已禁用' : '正常'}}
module.exports={avatarUrl,isGuest,roleText,birthdayText,displayMember}

