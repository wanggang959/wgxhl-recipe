import { test } from 'node:test'
import assert from 'node:assert/strict'
import { expenseRecordList, fuelRecordList, fuelRecordYears, fuelTripReport } from './vehicleRecords.js'

test('yearly fuel timeline uses estimates from the preceding interval and does not invent missing figures', () => {
  const rows = [
    { id:'new',occurredAt:'2026-03-08T08:00',mileage:395.67,paidAmount:38.41,liters:4.54,fullTank:true },
    { id:'old',occurredAt:'2026-02-12T08:00',mileage:190.12,paidAmount:33.14,liters:3.89,fullTank:true },
    { id:'previous-year',occurredAt:'2025-12-20T08:00',mileage:100,paidAmount:31,liters:4,fullTank:false },
  ]
  const stats = { fuelYearAverages: {'2026':2.21}, trend:[{recordId:'old',value:2.21,method:'FULL_SINGLE'}] }
  assert.deepEqual(fuelRecordYears(rows),['2026','2025'])
  const result = fuelRecordList(rows,stats,'2026')
  assert.deepEqual(result.summary.map(metric => metric.value),['71.55','2.21','205.55','2'])
  assert.deepEqual(result.entries[0].segment,{distance:'205.55',liters:'4.54',cost:'0.19'})
  assert.equal(result.entries[0].consumption,'2.21')
  assert.equal(result.entries[1].segment.liters,'—')
  assert.equal(fuelRecordList([{...rows[0],missedPrevious:true},...rows.slice(1)],stats,'2026').entries[0].consumption,'—')
})

test('trip report uses the selected interval and excludes later purchases from its running discounts', () => {
  const rows = [
    { id:'future',occurredAt:'2026-02-01T08:00',mileage:900,paidAmount:40,displayedAmount:50,liters:5 },
    { id:'selected',occurredAt:'2026-01-17T08:00',mileage:405,paidAmount:38.41,displayedAmount:39.23,liters:4.54 },
    { id:'prior',occurredAt:'2025-12-29T08:00',mileage:200,paidAmount:39,displayedAmount:40.1,liters:4.5 },
  ]
  const trend = [{ recordId:'prior', value:2.21, method:'FULL_SINGLE' }]
  const report = fuelTripReport(rows,{trend},'selected')
  assert.equal(report.start, '2025-12-29')
  assert.equal(report.days,20)
  assert.equal(report.count,2)
  assert.equal(report.consumption,'2.21')
  assert.equal(report.perKm,'0.187')
  assert.deepEqual(report.journey.map(item => item.value), ['19','205','10.79','4.54','38.41'])
  assert.deepEqual(report.savings.map(item => item.value), ['0.82','0.82','1.92'])
  const light = fuelTripReport(rows,{trend:[{...trend[0],method:'LIGHT_SINGLE'}]},'selected')
  assert.equal(light.journey[3].value,'4.50')
  assert.equal(light.journey[4].value,'39.00')
  const estimate = fuelTripReport(rows,{trend:[{...trend[0],method:'FULL_PHASE'}]},'selected')
  assert.equal(estimate.consumption,'2.21')
  assert.equal(estimate.perKm,'—')
  assert.match(estimate.warning,/估算/)
})

test('first fill and missed fill remain viewable without unsupported consumption or cost', () => {
  const rows = [
    { id:'new',occurredAt:'2026-11-02T08:00',mileage:110,missedPrevious:true,paidAmount:28,liters:4 },
    { id:'old',occurredAt:'2026-11-01T08:00',mileage:10,paidAmount:30,liters:4 },
  ]
  const stats = {trend:[{recordId:'old',value:4,method:'FULL_SINGLE'}]}
  const first = fuelTripReport(rows,stats,'old')
  assert.equal(first.paid,'30.00')
  assert.equal(first.consumption,'—')
  assert.match(first.warning,/第一条/)
  const missed = fuelTripReport(rows,stats,'new')
  assert.equal(missed.consumption,'—')
  assert.equal(missed.perKm,'—')
  assert.match(missed.warning,/漏记/)
  assert.equal(fuelTripReport(rows,stats,'unavailable'),null)
})

test('expense filters apply before calendar grouping and round totals in cents', () => {
  const rows = [
    {id:'a',occurredAt:'2026-11-30T23:59',category:'保养',amount:0.1,remark:'ABC 机油'},
    {id:'b',occurredAt:'2026-11-01T08:00',category:'保养',amount:0.2,remark:'机油'},
    {id:'c',occurredAt:'2026-10-03T08:00',category:'旧类型',amount:22},
  ]
  const sorted = expenseRecordList(rows)
  assert.deepEqual(sorted.groups.map(row => row.total),['0.30','22.00'])
  assert.equal(sorted.total,'22.30')
  const filtered = expenseRecordList(rows,{query:'abc',start:'2026-11-30',end:'2026-11-30',category:'保养'},[{name:'保养',color:'#123456'}])
  assert.equal(filtered.groups[0].entries[0].color,'#123456')
  assert.equal(filtered.groups[0].entries[0].id,'a')
  assert.equal(filtered.total,'0.10')
  assert.equal(expenseRecordList(rows,{start:'2026-11-01',end:'2026-10-01'}).invalidRange,true)
})
