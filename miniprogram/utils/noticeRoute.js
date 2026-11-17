function noticeRoute(item) {
  if(!item)return null
  if(item.notificationType==='TODO' && item.relatedId)return {label:'查看待办',method:'navigateTo',url:`/pages/todo-detail/index?id=${encodeURIComponent(item.relatedId)}`}
  if(item.notificationType==='WANT')return {label:'查看想吃',method:'switchTab',url:'/pages/wanted/index'}
  if(item.notificationType==='COMMUTE')return {label:'查看通勤',method:'navigateTo',url:'/pages/commute/index'}
  return null
}
function withNoticeRoute(item){const route=noticeRoute(item);return {...item,routeLabel:route?.label || ''}}
function openNotice(item){const route=noticeRoute(item);if(!route)return false;wx[route.method]({url:route.url});return true}
module.exports={noticeRoute,withNoticeRoute,openNotice}
