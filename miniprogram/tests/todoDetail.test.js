const test=require('node:test'),assert=require('node:assert/strict')
const api=require('../services/api'),session=require('../services/session')
let detail,notification
global.Page=value=>{ if (!detail) detail=value; else notification=value }
require('../pages/todo-detail/index')
require('../pages/notifications/index')
delete global.Page
function page(definition,data){return {...definition,data,setData(patch){Object.assign(this.data,patch)}}}
test('loads associated todo without changing production data',async()=>{
 const calls=[];api.post=async(path,payload)=>{calls.push({path,payload});return {id:'x',title:'test',ownerId:'u',category:'LIFE',repeatType:'MONTHLY'}}
 session.get=()=>({id:'u',token:'token'})
 const p=page(detail,{todo:null});p.id='x';await p.load()
 assert.deepEqual(calls,[{path:'/todo/getById',payload:{id:'x'}}])
 assert.equal(p.data.canManage,true);assert.equal(p.data.repeatLabel,'每月')
})
test('only todo notifications can open their detail',()=>{
 let url='';global.wx={navigateTo:options=>{url=options.url}}
 const p=page(notification,{items:[{id:'a',relatedId:'x/y',notificationType:'TODO'},{id:'b',relatedId:'x',notificationType:'COMMUTE'}]})
 p.openTodo({currentTarget:{dataset:{id:'a'}}});assert.equal(url,'/pages/todo-detail/index?id=x%2Fy')
 url='';p.openTodo({currentTarget:{dataset:{id:'b'}}});assert.equal(url,'')
 delete global.wx
})

