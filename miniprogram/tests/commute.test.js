const test=require('node:test'),assert=require('node:assert/strict')
const api=require('../services/api')
let definition;global.Page=value=>{definition=value};require('../pages/commute/index');delete global.Page
function page(data={}){return {...definition,data:{...definition.data,settings:{...definition.data.settings},form:{...definition.data.form},...data},setData(update){for(const [key,value] of Object.entries(update)){if(key.startsWith('form.'))this.data.form[key.slice(5)]=value;else if(key.startsWith('settings.'))this.data.settings[key.slice(9)]=value;else this.data[key]=value}}}}
test('invalid commute times do not send a request',async()=>{let calls=0;api.post=async()=>{calls++};const p=page({form:{commuteDate:'2026-11-16',departureTime:'09:00',arrivalTime:'08:00'}});await p.saveRecord();assert.equal(calls,0);assert.match(p.data.error,/晚于/)})
test('cannot save settings when loading failed',async()=>{let calls=0;api.post=async()=>{calls++};const p=page({settingsLoaded:false});await p.saveSettings();assert.equal(calls,0)})
test('deleting a record requires confirmation',()=>{let calls=0;api.post=async()=>{calls++};global.wx={showModal:({success})=>success({confirm:false})};const p=page({records:[{id:'a',dateLabel:'2026-11-16'}]});p.remove({currentTarget:{dataset:{id:'a'}}});assert.equal(calls,0);delete global.wx})
