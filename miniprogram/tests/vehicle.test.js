const test = require('node:test')
const assert = require('node:assert/strict')
const { loadPage, makePage } = require('./page-helper')
const api = require('../services/api')
const vehiclePage = loadPage('../pages/vehicle/index')
const { monthlyFuelTotal, monthlyConsumption } = require('../utils/vehicleCharts')

test('fuel amount is suggested without overwriting a manually adjusted payment', () => {
  const page = makePage(vehiclePage, { currentId:'vehicle', user:{id:'me'}, members:[{id:'me',displayName:'我'}] })
  page.open({currentTarget:{dataset:{type:'fuel'}}})
  const field = (name,value) => page.field({currentTarget:{dataset:{field:name}},detail:{value}})
  field('unitPrice','8'); field('liters','10')
  assert.equal(page.data.form.displayedAmount,'80.00')
  assert.equal(page.data.form.paidAmount,'80.00')
  assert.equal(page.data.form.includeLedger,false)
  field('paidAmount','75'); field('liters','11')
  assert.equal(page.data.form.displayedAmount,'88.00')
  assert.equal(page.data.form.paidAmount,'75')
})

test('only a new successful fuel save opens the report, and it can be reopened from history', async t => {
  const current = {id:'v',name:'Test car'}
  const old = {id:'old',occurredAt:'2026-11-01T08:00:00',mileage:100,liters:4,paidAmount:20}
  const form = {vehicleId:'v',date:'2026-11-07',time:'08:00',mileage:300,liters:5,paidAmount:30,displayedAmount:33}
  const page = makePage(vehiclePage, {currentId:'v',current,tab:'records',fuels:[old],formType:'fuel',form})
  let saved
  const toast = []
  t.mock.method(api, 'post', async (path, input) => {
    assert.equal(path,'/vehicle/fuel/save')
    saved = {...input,id:'new'}
    return saved
  })
  t.mock.method(page, 'loadVehicles', async () => {
    page.setData({fuels:[saved,old],stats:{trend:[{recordId:'old',value:2.5,method:'FULL_SINGLE'}]}})
  })
  global.wx = {showToast(value) { toast.push(value.title) }}
  t.after(() => { delete global.wx })
  await page.save()
  assert.equal(page.data.formType,'')
  assert.equal(page.data.tripReport.consumption,'2.50')
  assert.equal(page.data.tripReport.perKm,'0.150')
  assert.deepEqual(toast,[])
  page.closeTripReport()
  page.setData({formType:'fuel',form:{...form,id:'new',paidAmount:28}})
  await page.save()
  assert.equal(page.data.tripReport,null)
  assert.deepEqual(toast,['已保存'])
  page.openTripReport({currentTarget:{dataset:{id:'new'}}})
  assert.equal(page.data.tripReport.perKm,'0.140')
})

test('invalid import preview does not upload on confirmation', () => {
  let calls=0
  api.upload=async()=>{calls++}
  const page=makePage(vehiclePage,{importData:{rows:[{error:'invalid'}],digest:'abc'},importErrors:1})
  global.wx={showModal(){throw new Error('should not show confirmation')}}
  page.confirmImport()
  assert.equal(calls,0)
  delete global.wx
})

test('independent navigation selects record type without losing vehicle', () => {
  const page=makePage(vehiclePage,{currentId:'v',user:{id:'me'},members:[]})
  let options
  global.wx={showActionSheet(value){options=value},showToast(){}}
  page.chooseRecord()
  assert.deepEqual(options.itemList,['记加油','记费用'])
  options.success({tapIndex:1})
  assert.equal(page.data.formType,'expense')
  assert.equal(page.data.form.vehicleId,'v')
  delete global.wx
})

test('discount and real unit price update paid amount and remain editable', () => {
  const page=makePage(vehiclePage,{currentId:'v',user:{id:'me'},members:[]})
  page.open({currentTarget:{dataset:{type:'fuel'}}})
  const field=(name,value)=>page.field({currentTarget:{dataset:{field:name}},detail:{value}})
  field('unitPrice','8');field('liters','10')
  page.updateDiscount({detail:{value:'5'}})
  assert.equal(page.data.form.paidAmount,'75.00')
  assert.equal(page.data.form.actualUnit,'7.50')
  page.updateActualUnit({detail:{value:'7'}})
  assert.equal(page.data.form.paidAmount,'70.00')
  assert.equal(page.data.form.discount,'10.00')
  field('liters','11')
  assert.equal(page.data.form.paidAmount,'70.00')
})

test('chart groups actual readings by month and respects the selected range', () => {
  const now=new Date(2026,10,19)
  const trend=[{date:'2026-02-10',value:3},{date:'2026-10-01',value:4},{date:'2026-10-15',value:6},{date:'2026-11-01',value:7},{date:'2026-11-02',value:null}]
  assert.deepEqual(monthlyConsumption(trend,6,now),[{month:'2026-10',value:5},{month:'2026-11',value:7}])
  assert.equal(monthlyConsumption(trend,12,now).length,3)
  assert.equal(monthlyFuelTotal([{occurredAt:'2026-11-01',paidAmount:0.1},{occurredAt:'2026-11-02',paidAmount:0.2}],now),0.3)
})

test('trend canvas draws the actual points when shown', () => {
  let draws=0, segments=0
  const context={setStrokeStyle(){},setLineWidth(){},beginPath(){},moveTo(){},lineTo(){segments++},stroke(){},setFillStyle(){},arc(){},fill(){},setFontSize(){},setTextAlign(){},fillText(){},draw(){draws++}}
  global.wx={createSelectorQuery(){return {select(){return this},boundingClientRect(cb){this.cb=cb;return this},exec(){this.cb({width:320})}}},createCanvasContext(){return context}}
  const page=makePage(vehiclePage,{tab:'fuel',chartPoints:[{month:'2026-10',value:5},{month:'2026-11',value:6}]})
  page.drawTrend()
  assert.equal(draws,1)
  assert.ok(segments>=2)
  page.openVehiclePicker()
  page.drawTrend()
  assert.equal(draws,1)
  delete global.wx
})

test('vehicle picker replaces the selected vehicle and clears previous figures before loading', async () => {
  const page = makePage(vehiclePage, {
    currentId: 'a', current: {id:'a',name:'A'}, vehicles: [{id:'a',name:'A'}, {id:'b',name:'B'}],
    fuels: [{id:'old'}], expenses: [{id:'old-expense'}], stats:{latest:2.1},
  })
  const loaded = []
  page.load = async () => { loaded.push(page.data.currentId) }
  const select = id => page.selectVehicle({currentTarget:{dataset:{id}}})
  page.openVehiclePicker()
  assert.equal(page.data.showVehiclePicker, true)
  select('a')
  assert.equal(page.data.showVehiclePicker, false)
  assert.deepEqual(loaded, [])
  page.openVehiclePicker()
  await select('b')
  assert.equal(page.data.showVehiclePicker, false)
  assert.deepEqual(loaded, ['b'])
  assert.equal(page.data.current.name, 'B')
  assert.deepEqual(page.data.fuels, [])
  assert.deepEqual(page.data.expenses, [])
  assert.equal(page.data.latestLabel, '暂无')
})

test('vehicle picker manages vehicles without changing the selection', t => {
  const page = makePage(vehiclePage, {currentId:'a'})
  t.mock.method(page, 'drawTrend', () => {})
  global.wx = {}
  t.after(() => { delete global.wx })
  page.openVehiclePicker()
  page.manageVehiclesFromPicker()
  assert.equal(page.data.showVehiclePicker, false)
  assert.equal(page.data.currentId, 'a')
  assert.equal(page.data.tab, 'vehicles')
})

test('vehicle photo is previewed locally, uploaded only on save and can be removed', async t => {
  const page = makePage(vehiclePage)
  const imagePath = 'vehicle/9fb4babe-edaf-4bb6-b713-0ee482852782.jpg'
  const saves = []
  const uploads = []
  t.mock.method(page, 'loadVehicles', async () => {})
  t.mock.method(api, 'post', async (path, form) => { saves.push({path, form}); return {id:'a'} })
  t.mock.method(api, 'upload', async (...args) => { uploads.push(args); return imagePath })
  global.wx = {
    showToast() {},
    chooseMedia({success}) { success({tempFiles:[{tempFilePath:'local.jpg'}]}) },
    getImageInfo({success}) { success({width:640, height:480}) },
    getFileSystemManager: () => ({ getFileInfo({success}) { success({size:4000}) } }),
  }
  t.after(() => { delete global.wx })
  page.open({currentTarget:{dataset:{type:'vehicle'}}})
  page.setData({'form.name':'Test car'})
  await page.selectPhoto()
  assert.equal(page.data.photoPreview, 'local.jpg')
  assert.equal(uploads.length, 0)
  await page.save()
  assert.deepEqual(uploads, [['/file/upload','local.jpg',{folder:'vehicle'}]])
  assert.equal(saves[0].form.imagePath, imagePath)
  page.setData({vehicles:[{id:'a',name:'Test car',vehicleType:'CAR',imagePath}]})
  page.open({currentTarget:{dataset:{type:'vehicle',id:'a'}}})
  assert.ok(page.data.photoPreview.endsWith(imagePath))
  page.removePhoto()
  assert.equal(page.data.photoPreview, '')
  await page.save()
  assert.equal(saves[1].form.imagePath, '')
  assert.equal(uploads.length, 1)
})

test('expense entry selects only enabled types and retains a disabled historical label on edit', async t => {
  const page = makePage(vehiclePage, {currentId:'v',user:{id:'me'}, members:[], expenseTypes:[
    {key:'wash',name:'洗车',enabled:true},{key:'old',name:'旧类型',enabled:false}
  ], expenses:[{id:'old-record',occurredAt:'2026-11-01T12:00:00',category:'旧类型'}]})
  let saved = null
  t.mock.method(api, 'post', async (path, input) => { saved = {path, input}; return {id:'expense'} })
  t.mock.method(page, 'loadVehicles', async () => {})
  global.wx = {showToast() {}}
  t.after(() => { delete global.wx })
  page.open({currentTarget:{dataset:{type:'expense'}}})
  await page.save()
  assert.equal(saved, null)
  assert.deepEqual(page.data.expenseChoices.map(type => type.name), ['洗车'])
  page.chooseExpenseType({detail:{value:0}})
  await page.save()
  assert.equal(saved.input.categoryKey, 'wash')
  page.open({currentTarget:{dataset:{type:'expense',id:'old-record'}}})
  await page.save()
  assert.equal(saved.input.category, '旧类型')
  assert.equal(saved.input.categoryKey, undefined)
})

test('chart taps show monthly details, choose ring slices and clear on range change', t => {
  const now = new Date()
  const month = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
  const page = makePage(vehiclePage, {expenses:[{occurredAt:month + '-01',category:'洗车',amount:20}]})
  global.wx = { createSelectorQuery() { return { select() { return this }, boundingClientRect(fn) { this.fn=fn; return this }, exec() { this.fn({left:10,top:20,width:200,height:200}) } } } }
  t.after(() => { delete global.wx })
  page.updateDashboard()
  const bar = page.data.spending.bars.at(-1)
  page.tapBar({currentTarget:{dataset:{kind:'spend',key:bar.key}}})
  assert.equal(page.data.spendDetail.segments[0].label,'20.00')
  page.tapDonut({detail:{x:110,y:30}})
  assert.equal(page.data.compositionDetail.name,'洗车')
  assert.equal(page.data.compositionDetail.percent,'100.00')
  page.setChartRange({currentTarget:{dataset:{field:'spendRange'}},detail:{value:3}})
  assert.equal(page.data.spendDetail,null)
  assert.equal(page.data.spending.bars.length,24)
})

test('fuel record years and expand all are independent from expense filters', () => {
  const page = makePage(vehiclePage, {tab:'records',fuels:[
    {id:'a',occurredAt:'2026-06-03T10:30',mileage:1200,paidAmount:44,liters:5},
    {id:'b',occurredAt:'2025-12-12T10:30',mileage:1000,paidAmount:31,liters:4},
  ]})
  page.updateRecordLists()
  assert.equal(page.data.recordYear,'2026')
  page.toggleAllRecords()
  assert.equal(page.data.recordExpanded.a,true)
  page.stepRecordYear({currentTarget:{dataset:{step:1}}})
  assert.equal(page.data.recordYear,'2025')
  assert.deepEqual(page.data.recordExpanded,{})
  page.setData({tab:'expenseRecords', expenses:[
    {id:'a',occurredAt:'2026-06-03',category:'停车',amount:10},
    {id:'b',occurredAt:'2026-05-03',category:'洗车',amount:20},
  ]})
  page.updateRecordLists()
  assert.equal(page.data.expenseList.groups.length,2)
  page.changeRecordFilter({currentTarget:{dataset:{key:'query'}},detail:{value:'停车'}})
  assert.equal(page.data.expenseList.total,'10.00')
  page.toggleExpenseMonth({currentTarget:{dataset:{key:'2026-06'}}})
  assert.equal(page.data.recordCollapsed['2026-06'],true)
  page.clearRecordFilters()
  assert.equal(page.data.expenseList.total,'30.00')
})
