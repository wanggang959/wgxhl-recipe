const categories = [{value:'COOK',label:'做饭'},{value:'GROCERY',label:'采购'},{value:'LIFE',label:'生活'},{value:'BIRTHDAY',label:'生日'},{value:'ANNIVERSARY',label:'纪念日'},{value:'SERVER',label:'服务器'},{value:'DOMAIN',label:'域名'},{value:'SSL',label:'证书'},{value:'PAYMENT',label:'缴费'},{value:'OTHER',label:'其他'}]
const repeats = [{value:'NONE',label:'不重复'},{value:'DAILY',label:'每天'},{value:'WEEKLY',label:'每周'},{value:'MONTHLY',label:'每月'},{value:'QUARTERLY',label:'每三个月'},{value:'HALF_YEARLY',label:'每半年'},{value:'YEARLY',label:'每年'},{value:'CUSTOM_DAYS',label:'自定义天数'}]
const notices = [{value:0,label:'准时'},{value:10,label:'提前10分钟'},{value:60,label:'提前1小时'},{value:1440,label:'提前1天'},{value:4320,label:'提前3天'},{value:10080,label:'提前7天'},{value:43200,label:'提前30天'}]
function defaultNotices(category) {
  if (category === 'BIRTHDAY') return [10080,4320,0]
  if (['SERVER','DOMAIN','SSL'].includes(category)) return [43200,10080,4320,1440]
  return [0]
}
function fromTodo(todo = {}, nextCycle = false) {
  const due = String(todo.dueTime || '').replace(' ','T')
  const category = todo.category || 'OTHER'
  const birthdayDate = due && todo.dueCalendar !== 'LUNAR' ? `${todo.birthdayYear || due.slice(0,4)}${due.slice(4,10)}` : ''
  return { id:todo.id || '', title:todo.title || '', category, ownerIds:todo.ownerIds?.length ? [...todo.ownerIds] : todo.ownerId ? [todo.ownerId] : [], date:nextCycle ? '' : due.slice(0,10), time:nextCycle ? '' : due.slice(11,16), dueCalendar:todo.dueCalendar || 'SOLAR', birthdayDate, birthdayYear:todo.birthdayYear == null ? '' : String(todo.birthdayYear), lunarDueMonth:String(todo.lunarDueMonth || 1), lunarDueDay:String(todo.lunarDueDay || 1), lunarDueLeap:Boolean(todo.lunarDueLeap), description:todo.description || '', repeatType:todo.repeatType || 'NONE', repeatIntervalDays:String(todo.repeatIntervalDays ?? 30), notifySite:todo.notifySite !== false, notifyEmail:Boolean(todo.notifyEmail), notifyPush:Boolean(todo.notifyPush), noticeMinutes:todo.noticeMinutes?.length ? [...todo.noticeMinutes] : defaultNotices(category), relatedType:todo.relatedType || '', relatedId:todo.relatedId || '' }
}
function toPayload(form, nextCycle = false) {
  const title = form.title.trim()
  if (!title) throw new Error('请输入待办标题')
  if (nextCycle && (!form.date || !form.time)) throw new Error('请填写下一个到期日期和时间')
  if (Boolean(form.date) !== Boolean(form.time) && form.category !== 'BIRTHDAY') throw new Error('请同时填写日期和时间')
  if (form.repeatType === 'CUSTOM_DAYS' && form.category !== 'BIRTHDAY') {
    const days=String(form.repeatIntervalDays ?? '').trim()
    if (!/^\d+$/.test(days) || Number(days)<1 || Number(days)>3650) throw new Error('重复天数须为 1 至 3650 的整数')
    if (!form.date || !form.time) throw new Error('请设置首次到期时间')
  }
  if (form.category === 'BIRTHDAY' && form.dueCalendar === 'SOLAR' && !form.birthdayDate) throw new Error('请选择公历生日')
  const birthdayYear = form.category !== 'BIRTHDAY' ? null : form.dueCalendar === 'SOLAR' ? Number(form.birthdayDate.slice(0,4)) : form.birthdayYear ? Number(form.birthdayYear) : null
  const month=Number(form.lunarDueMonth), day=Number(form.lunarDueDay)
  if (form.category === 'BIRTHDAY' && form.dueCalendar === 'LUNAR' && (!Number.isInteger(month) || month<1 || month>12 || !Number.isInteger(day) || day<1 || day>30 || (birthdayYear != null && (!Number.isInteger(birthdayYear) || birthdayYear<1900 || birthdayYear>2100)))) throw new Error('请检查农历生日和出生年')
  const dueTime = form.category === 'BIRTHDAY' ? (form.dueCalendar === 'LUNAR' ? null : `${form.birthdayDate}T11:00:00`) : form.date ? `${form.date}T${form.time}:00` : null
  const ownerIds = [...new Set(form.ownerIds.filter(Boolean))]
  return { id:form.id || undefined, title, category:form.category, ownerIds, ownerId:ownerIds[0] || '', dueTime, dueCalendar:form.category === 'BIRTHDAY' ? form.dueCalendar : 'SOLAR', lunarDueMonth:form.category === 'BIRTHDAY' && form.dueCalendar === 'LUNAR' ? month : null, lunarDueDay:form.category === 'BIRTHDAY' && form.dueCalendar === 'LUNAR' ? day : null, lunarDueLeap:form.category === 'BIRTHDAY' && form.dueCalendar === 'LUNAR' && form.lunarDueLeap, birthdayYear, description:form.description.trim(), repeatType:form.category === 'BIRTHDAY' ? 'YEARLY' : form.repeatType, repeatIntervalDays:form.category !== 'BIRTHDAY' && form.repeatType === 'CUSTOM_DAYS' ? Number(form.repeatIntervalDays) : null, notifySite:form.notifySite, notifyEmail:form.notifyEmail, notifyPush:form.notifyPush, noticeMinutes:form.noticeMinutes, relatedType:form.relatedType, relatedId:form.relatedId }
}
module.exports = { categories, repeats, notices, defaultNotices, fromTodo, toPayload }
