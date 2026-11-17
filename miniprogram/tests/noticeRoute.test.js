const test=require('node:test'),assert=require('node:assert/strict')
const {noticeRoute,openNotice}=require('../utils/noticeRoute')
test('notifications link to their own feature and ignore unknown types',()=>{
  assert.deepEqual(noticeRoute({notificationType:'TODO',relatedId:'id/1'}),{label:'查看待办',method:'navigateTo',url:'/pages/todo-detail/index?id=id%2F1'})
  assert.equal(noticeRoute({notificationType:'TODO'}),null)
  assert.deepEqual(noticeRoute({notificationType:'WANT'}),{label:'查看想吃',method:'switchTab',url:'/pages/wanted/index'})
  assert.deepEqual(noticeRoute({notificationType:'COMMUTE',relatedId:'2026-11-17'}),{label:'查看通勤',method:'navigateTo',url:'/pages/commute/index'})
  assert.equal(noticeRoute({notificationType:'OTHER',relatedId:'x'}),null)
})
test('notice navigation uses the appropriate mini program navigation method',()=>{
  let call;global.wx={navigateTo:o=>{call=['navigateTo',o.url]},switchTab:o=>{call=['switchTab',o.url]}}
  assert.equal(openNotice({notificationType:'WANT'}),true);assert.deepEqual(call,['switchTab','/pages/wanted/index'])
  assert.equal(openNotice({notificationType:'OTHER'}),false)
})
