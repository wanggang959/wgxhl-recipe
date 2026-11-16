const test=require('node:test'),assert=require('node:assert/strict')
const api=require('../services/api')
let definition;global.Page=value=>{definition=value};require('../pages/profile/index');delete global.Page
function page(){return {...definition,data:{user:{id:'u',userRole:'super_admin'},canManage:true,isGuest:false,members:[],membersPage:0,membersTotal:0,membersLoading:false,membersError:''},setData(update){Object.assign(this.data,update)}}}
test('loads 20 members per page only when requested',async()=>{
 const calls=[];api.post=async(path,query)=>{calls.push({path,query});return {records:Array.from({length:query.current===1?20:2},(_,i)=>({id:String((query.current-1)*20+i),username:'user'})),total:22}}
 const p=page();await p.loadMembers(true)
 assert.equal(p.data.members.length,20);assert.equal(calls.length,1);assert.deepEqual(calls[0].query,{current:1,size:20})
 await p.loadMembers(false);assert.equal(p.data.members.length,22);assert.deepEqual(calls[1].query,{current:2,size:20})
 await p.loadMembers(false);assert.equal(calls.length,2)
})
test('a failed next page preserves existing members and retries that page',async()=>{
 let fail=true;const calls=[]
 api.post=async(path,query)=>{calls.push(query.current);if(query.current===2 && fail)throw Error('网络错误');return {records:Array.from({length:query.current===1?20:1},(_,i)=>({id:String((query.current-1)*20+i),username:'user'})),total:21}}
 const p=page();await p.loadMembers(true);await p.loadMembers(false)
 assert.equal(p.data.members.length,20);assert.equal(p.data.membersPage,1);assert.equal(p.data.membersError,'网络错误')
 fail=false;await p.loadMembers(false);assert.equal(p.data.members.length,21);assert.deepEqual(calls,[1,2,2])
})
test('stale request cannot overwrite a newer refresh',async()=>{
 let resolveFirst;api.post=async(path,query)=>new Promise(resolve=>{if(!resolveFirst)resolveFirst=resolve;else resolve({records:[{id:'new',username:'new'}],total:1})})
 const p=page();const old=p.loadMembers(true);await p.loadMembers(true)
 resolveFirst({records:[{id:'old',username:'old'}],total:1});await old
 assert.equal(p.data.members[0].id,'new')
})
