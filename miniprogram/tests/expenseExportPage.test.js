const test=require('node:test'),assert=require('node:assert/strict')
const {loadPage,makePage}=require('./page-helper'),api=require('../services/api')
const definition=loadPage('../pages/expense/index')
test('export renders all filtered rows and opens a preview without saving or sharing',async()=>{
 const calls=[],drawn=[];api.post=async(path,data)=>{calls.push({path,data});return {records:data.current===1?[{id:'a',itemName:'牛奶',quantity:'2盒',actualAmount:'12.30'}]:[{id:'b',itemName:'水果',actualAmount:'7.70'}],total:2}}
 const ctx={measureText:text=>({width:Array.from(text).length*10}),fillRect(){},fillText:text=>drawn.push(text),beginPath(){},moveTo(){},lineTo(){},stroke(){}},canvas={getContext:()=>ctx}
 global.wx={nextTick:fn=>fn(),createSelectorQuery:()=>{const q={in:()=>q,select:()=>q,fields:()=>q,exec:fn=>fn([{node:canvas}])};return q},canvasToTempFilePath:options=>options.success({tempFilePath:'/tmp/list.png'}),showToast:()=>{}}
 const p=makePage(definition,{project:{projectName:'采购'},selectedId:'p',category:'FOOD',status:''});await p.exportImage()
 assert.equal(p.data.exportOpen,true);assert.equal(p.data.exportPath,'/tmp/list.png');assert.ok(drawn.includes('牛奶'));assert.ok(drawn.includes('水果'));assert.ok(drawn.includes('¥20.00'));assert.equal(calls.length,2);delete global.wx
})
