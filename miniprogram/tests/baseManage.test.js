const test = require('node:test'), assert = require('node:assert/strict')
const api = require('../services/api'), session = require('../services/session')
let definition; global.Page = value => { definition = value }; require('../pages/base-manage/index'); delete global.Page
function page(data = {}) { return { ...definition, data: { ...definition.data, ...data }, setData(update) { for (const [key,value] of Object.entries(update)) { if (key.startsWith('form.')) this.data.form[key.slice(5)] = value; else this.data[key] = value } } } }
test('pagination passes a bounded page and hides public actions for ordinary users', async () => {
  let calls = []; api.post = async (path, data) => { calls.push([path,data]); return { records: [{ id: String(data.current), scope: data.current === 1 ? 'platform' : 'personal', ingredientName: '番茄' }], total: 2 } }
  const p = page({ kind: 'ingredient', keyword: ' 番茄 ' }); await p.load(true); await p.load(false)
  assert.deepEqual(calls.map(v => v[1]), [{current:1,size:50,ingredientName:'番茄'},{current:2,size:50,ingredientName:'番茄'}]); assert.equal(p.data.items[0].canManage,false); assert.equal(p.data.items[1].canManage,true)
})
test('old requests cannot overwrite a newer tab', async () => {
  let finish; api.post = path => path.startsWith('/category') ? new Promise(r => { finish = r }) : Promise.resolve({ records:[{id:'new',seasoningName:'盐'}],total:1 })
  const p = page(); const old = p.load(true); p.switchKind({currentTarget:{dataset:{kind:'seasoning'}}}); await new Promise(r=>setImmediate(r)); finish({records:[{id:'old'}],total:1}); await old
  assert.equal(p.data.kind,'seasoning'); assert.equal(p.data.items[0].id,'new')
})
test('validation and cancelled deletion never write data', async () => {
  let calls=0; api.post=async()=>{calls++}; global.wx={showModal:({success})=>success({confirm:false})}
  const p=page({form:{name:'  ',description:''},items:[{id:'x',name:'盐',canManage:true}]}); await p.save(); p.remove({currentTarget:{dataset:{id:'x'}}}); assert.equal(calls,0); assert.match(p.data.formError,/名称/); delete global.wx
})
test('upload uses the existing backend and reports domain configuration failures', () => {
  session.get=()=>({token:'test'}); global.wx={uploadFile:options=>{assert.equal(options.url,'https://recipe.wgxhl.space/recipe/file/upload');assert.equal(options.formData.folder,'ingredient');options.fail({errMsg:'url not in domain list'});options.complete()}}
  const p=page({kind:'ingredient'}); p.uploadImage('tmp'); assert.match(p.data.formError,/uploadFile/); assert.equal(p.data.uploading,false); delete global.wx
})
test('successful deletion keeps the current list and overlaps the next page without duplicates', async () => {
  const original = Array.from({length: 102}, (_, i) => ({ id: String(i + 1), ingredientName: '食材' + (i + 1), scope: 'personal' }))
  let server = original.slice(), calls = [], confirm
  api.post = async (path, data) => { calls.push([path,data]); if (path.endsWith('/delete')) { server = server.filter(item => item.id !== data.id); return }; return { records:server.slice((data.current-1)*50,data.current*50),total:server.length } }
  global.wx = { showModal: options => { confirm = options.success }, showToast: () => {} }
  const p = page({ kind:'ingredient' }); await p.load(true); await p.load(false); const before = p.data.items.slice()
  p.remove({currentTarget:{dataset:{id:'1'}}}); await confirm({confirm:true})
  assert.equal(p.data.items.length,99); assert.equal(p.data.items[0].id,before[1].id); assert.equal(p.data.page,2); assert.equal(p.data.total,101); assert.equal(calls.filter(v=>v[0].endsWith('/page')).length,2)
  await p.load(false); assert.equal(p.data.items.length,101); assert.equal(new Set(p.data.items.map(v=>v.id)).size,101); assert.equal(p.data.items[99].id,'101'); assert.equal(p.data.items[100].id,'102')
  delete global.wx
})
test('failed deletion retains the existing list', async () => {
  let confirm; api.post=async()=>{throw new Error('正在使用')}; global.wx={showModal:o=>{confirm=o.success},showToast:()=>{}}
  const p=page({items:[{id:'x',name:'盐',canManage:true}],total:1,page:1}); p.remove({currentTarget:{dataset:{id:'x'}}}); await confirm({confirm:true}); assert.equal(p.data.items.length,1); assert.equal(p.data.total,1); delete global.wx
})
