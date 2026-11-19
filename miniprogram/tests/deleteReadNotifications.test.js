const test=require('node:test'),assert=require('node:assert/strict')
const api=require('../services/api')
const {loadPage,makePage}=require('./page-helper')
let component;global.Component=value=>{component=value};require('../components/notice-panel/index');delete global.Component
const page=loadPage('../pages/notifications/index')

function make(definition,data){const instance=makePage(definition,data);instance.triggerEvent=name=>instance.events.push(name);instance.events=[];return instance}
for(const [label,definition] of [['弹窗',{...component,...component.methods}],['页面',page]]){
 test(`${label} only deletes after confirmation and refreshes`,async()=>{
  const calls=[],toasts=[];let modal;api.post=async(path)=>{calls.push(path)};global.wx={showModal:options=>{modal=options},showToast:options=>toasts.push(options.title)}
  const p=make(definition,{total:35,unread:30,items:[{id:'1',isRead:false}],readonly:false,busy:false,loading:false});p.load=async reset=>{calls.push(`load:${reset}`)}
  p.deleteRead();assert.match(modal.content,/未读消息会保留/);await modal.success({confirm:false});assert.deepEqual(calls,[])
  p.deleteRead();await modal.success({confirm:true});assert.deepEqual(calls,['/notification/deleteRead','load:true']);assert.equal(p.data.busy,false);assert.deepEqual(toasts,['已删除已读消息']);if(label==='弹窗')assert.deepEqual(p.events,['change']);delete global.wx
 })
 test(`${label} blocks deletion for guests, all-unread, busy or loading`,()=>{
  let dialogs=0;global.wx={showModal(){dialogs++}};const p=make(definition,{total:2,unread:2,readonly:false,busy:false,loading:false})
  p.deleteRead();p.setData({total:3,readonly:true});p.deleteRead();p.setData({readonly:false,busy:true});p.deleteRead();p.setData({busy:false,loading:true});p.deleteRead();assert.equal(dialogs,0);delete global.wx
 })
 test(`${label} preserves data and reports failures`,async()=>{
  let modal;const toasts=[];api.post=async()=>{throw Error('网络异常')};global.wx={showModal:options=>{modal=options},showToast:options=>toasts.push(options.title)}
  const p=make(definition,{total:2,unread:1,items:[{id:'read',isRead:true}],readonly:false,busy:false,loading:false});p.deleteRead();await modal.success({confirm:true});assert.deepEqual(p.data.items,[{id:'read',isRead:true}]);assert.deepEqual(toasts,['网络异常']);assert.equal(p.data.busy,false);delete global.wx
 })
}
