const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path')
const api=require('../services/api'),session=require('../services/session')
const {loadPage,makePage}=require('./page-helper')
const list=loadPage('../pages/todos/index'),detail=loadPage('../pages/todo-detail/index')
function deferred(){let resolve,reject;const promise=new Promise((ok,fail)=>{resolve=ok;reject=fail});return {promise,resolve,reject}}
test('returning to the list keeps existing content while refreshing',async()=>{
  const pending=deferred(),user={id:'u1',householdId:'h1',token:'token'}
  session.get=()=>user
  api.post=async name=>name==='/todo/page'?pending.promise:name==='/todo/summary'?{}:name==='/user/members'?[]:name==='/want/page'?{records:[],total:0}:0
  const page=makePage(list,{hasSnapshot:true,items:[{id:'old'}],groups:[{key:'old'}]});page.loadedIdentity='u1:h1'
  page.onShow()
  assert.equal(page.data.loading,true);assert.equal(page.data.hasSnapshot,true);assert.equal(page.data.items[0].id,'old')
  pending.resolve({records:[],total:0});await new Promise(resolve=>setImmediate(resolve))
  assert.equal(page.data.loading,false);assert.equal(page.data.hasSnapshot,true);assert.equal(page.data.items.length,0)
  page.stopUnreadRefresh()
})
test('switching accounts clears the previous household immediately',async()=>{
  const pending=deferred();session.get=()=>({id:'u2',householdId:'h2',token:'token'})
  api.post=async name=>name==='/todo/page'?pending.promise:name==='/todo/summary'?{}:name==='/user/members'?[]:name==='/want/page'?{records:[],total:0}:0
  const page=makePage(list,{hasSnapshot:true,items:[{id:'private'}],groups:[{key:'private'}]});page.loadedIdentity='u1:h1'
  page.onShow();assert.equal(page.data.hasSnapshot,false);assert.deepEqual(page.data.items,[]);assert.deepEqual(page.data.groups,[])
  pending.resolve({records:[],total:0});await new Promise(resolve=>setImmediate(resolve));page.stopUnreadRefresh()
})
test('detail retains the last record on refresh failure',async()=>{
  session.get=()=>({id:'u1',token:'token'});api.post=async()=>{throw new Error('网络错误')}
  const page=makePage(detail,{todo:{id:'old',title:'待办'}});page.id='old';await page.load()
  assert.equal(page.data.todo.id,'old');assert.equal(page.data.error,'网络错误')
})
test('a newer refresh cannot be overwritten by a slower old request',async()=>{
  const first=deferred(),second=deferred();let count=0
  session.get=()=>({id:'u1',householdId:'h1',token:'token'})
  api.post=async name=>name==='/todo/page'?(++count===1?first.promise:second.promise):name==='/todo/summary'?{}:0
  const page=makePage(list,{hasSnapshot:true});const old=page.load(true),recent=page.load(true)
  second.resolve({records:[{id:'new',title:'new'}],total:1});await recent
  first.resolve({records:[{id:'old',title:'old'}],total:1});await old
  assert.equal(page.data.items[0].id,'new')
})
test('loading placeholders do not replace a saved snapshot',()=>{
  const read=name=>fs.readFileSync(path.join(__dirname,'..','pages',name,'index.wxml'),'utf8')
  assert.match(read('todos'),/\(loading \|\| planLoading\) && !hasSnapshot/)
  assert.match(read('todo-detail'),/loading && !todo/)
})
