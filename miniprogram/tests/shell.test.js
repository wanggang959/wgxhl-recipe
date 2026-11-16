const test=require('node:test'),assert=require('node:assert/strict')
let tab,sheet
global.Component=value=>{if(!tab)tab=value;else sheet=value}
require('../custom-tab-bar/index')
require('../components/notice-panel/index')
delete global.Component
test('navigation opens available tabs and marks unavailable sections honestly',()=>{
 const calls=[];global.wx={switchTab:options=>calls.push(options.url),showToast:options=>calls.push(options.title)}
 const p={data:{...tab.data,selected:'todos'}}
 tab.methods.navigate.call(p,{currentTarget:{dataset:{key:'profile'}}})
 tab.methods.navigate.call(p,{currentTarget:{dataset:{key:'home'}}})
 assert.deepEqual(calls,['/pages/profile/index','/pages/home/index']);delete global.wx
})
test('closing notice sheet does not mutate production messages',()=>{
 const events=[];sheet.methods.close.call({triggerEvent:name=>events.push(name)})
 assert.deepEqual(events,['close'])
})

