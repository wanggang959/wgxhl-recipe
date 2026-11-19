const amount = value => value == null || value === '' || !Number.isFinite(Number(value)) ? '—' : Number(value).toFixed(2)
const compact = value => value == null || value === '' || !Number.isFinite(Number(value)) ? '—' : String(Number(value))
const cents = value => Math.round(Number(value || 0) * 100)
const dayOf = row => String(row.occurredAt || '').slice(0, 10)
const shortDate = row => dayOf(row).slice(5).replace('-', '/')
const timestamp = row => String(row.occurredAt || '').replace('T', ' ').slice(0, 16)
const byRecency = (a, b) => String(b.occurredAt).localeCompare(String(a.occurredAt)) || String(b.id).localeCompare(String(a.id))
const dated = rows => rows.filter(row => /^\d{4}-\d{2}-\d{2}$/.test(dayOf(row))).sort(byRecency)

function fuelRecordYears(rows) {
  return [...new Set(dated(rows).map(row => dayOf(row).slice(0, 4)))]
}

function fuelInterval(row, older, estimates) {
  const difference = older && row.mileage != null && older.mileage != null ? (cents(row.mileage) - cents(older.mileage)) / 100 : 0
  const distance = difference > 0 ? difference : null
  const rate = distance && !row.missedPrevious ? estimates.get(older.id) : null
  const match = rate?.method === 'FULL_SINGLE' ? row : rate?.method === 'LIGHT_SINGLE' ? older : null
  return { distance, rate, match }
}

function fuelRecordList(rows, stats = {}, year) {
  const all = dated(rows)
  const estimates = new Map((stats.trend || []).map(point => [point.recordId, point]))
  const entries = []
  for (let i = 0; i < all.length; i++) {
    const row = all[i]
    if (dayOf(row).slice(0, 4) !== year) continue
    const older = all[i + 1]
    const { distance, rate, match } = fuelInterval(row, older, estimates)
    const segment = distance ? { distance: compact(distance), liters: amount(match?.liters),
      cost: match?.paidAmount != null ? amount(Number(match.paidAmount) / distance) : '—' } : null
    entries.push({ record: row, id: row.id, timestamp: timestamp(row), date: shortDate(row),
      mileage: compact(row.mileage), consumption: amount(rate?.value), aboveAverage: rate?.value != null
        && stats.fuelYearAverages?.[year] != null && Number(rate.value) > Number(stats.fuelYearAverages[year]),
      total: amount(row.paidAmount), liters: amount(row.liters),
      unitPrice: amount(row.unitPrice ?? (Number(row.liters) > 0 && row.paidAmount != null ? Number(row.paidAmount) / Number(row.liters) : null)),
      flags: [row.fuelGrade, row.fullTank ? '加满' : '未加满', row.warningLight ? '亮灯' : '', row.missedPrevious ? '上次漏记' : ''].filter(Boolean),
      segment,
    })
  }
  const first = entries[entries.length - 1]?.record, last = entries[0]?.record
  const validDistance = entries.length > 1 && entries.every(({ record }, i) => record.mileage != null &&
    Number.isFinite(Number(record.mileage)) && (!i || Number(record.mileage) <= Number(entries[i - 1].record.mileage)))
  const summary = [
    { title:'累计花费', value:amount(entries.reduce((sum, row) => sum + cents(row.record.paidAmount), 0) / 100), unit:'元' },
    { title:'平均油耗', value:amount(stats.fuelYearAverages?.[year]), unit:'升/百公里' },
    { title:'累计行程', value:validDistance ? compact((cents(last.mileage) - cents(first.mileage)) / 100) : '—', unit:'公里' },
    { title:'加油次数', value:String(entries.length), unit:'次' },
  ]
  return { entries, summary }
}

function fuelTripReport(rows, stats, recordId) {
  const ordered = dated(rows)
  const index = ordered.findIndex(row => row.id === recordId)
  if (index < 0) return null
  const row = ordered[index], older = ordered[index + 1], day = dayOf(row)
  const history = ordered.slice(index)
  const estimates = new Map((stats?.trend || []).map(point => [point.recordId, point]))
  const { distance, rate, match } = fuelInterval(row, older, estimates)
  const daysSince = (a, b) => Math.round((Date.parse(dayOf(a)) - Date.parse(dayOf(b))) / 86400000)
  const journeyDays = older ? daysSince(row, older) : null
  const perKm = distance != null && match?.paidAmount != null ? (Number(match.paidAmount) / distance).toFixed(3) : '—'
  const ownDiscount = record => record.displayedAmount == null || record.displayedAmount === '' || record.paidAmount == null || record.paidAmount === ''
    ? null : Math.max(0, cents(record.displayedAmount) - cents(record.paidAmount))
  const discounts = records => amount(records.reduce((sum, record) => sum + (ownDiscount(record) || 0), 0) / 100)
  const currentDiscount = ownDiscount(row)
  const metric = (label, value, unit) => ({ label, value, unit })
  return {
    date: timestamp(row), start: older ? dayOf(older) : '', end: day,
    days: daysSince(row, history[history.length - 1]) + 1, count: history.length,
    consumption: amount(rate?.value), perKm, paid: amount(row.paidAmount), added: amount(row.liters),
    warning: !older ? '第一条加油记录，暂不计算行程油耗。' : row.missedPrevious
      ? '上次加油有漏记，本区间暂不计算油耗和油费。' : !distance
        ? '里程不足以计算本次行程。' : rate && !match
          ? '油耗为多段行程的估算值，本区间的耗油量和油费无法单独核算。'
          : !match ? '尚无足够的加满或亮灯记录，暂不能计算本区间油耗和油费。' : '',
    journey: [
      metric('行程天数', compact(journeyDays), '天'),
      metric('行程里程', compact(distance), '公里'),
      metric('日均行程', distance != null && journeyDays > 0 ? amount(distance / journeyDays) : '—', '公里'),
      metric('燃油消耗', amount(match?.liters), '升'),
      metric('区间油费', amount(match?.paidAmount), '元'),
    ],
    savings: [
      metric('本次优惠', amount(currentDiscount == null ? null : currentDiscount / 100), '元'),
      metric('当年优惠', discounts(history.filter(item => dayOf(item).slice(0, 4) === day.slice(0, 4))), '元'),
      metric('累计优惠', discounts(history), '元'),
    ],
  }
}

function expenseRecordList(rows, filters = {}, expenseTypes = []) {
  const categories = [...new Set(rows.map(row => row.category || '其他'))].sort((a, b) => a.localeCompare(b))
  const colors = new Map(expenseTypes.map(type => [type.name, type.color]))
  const query = String(filters.query || '').trim().toLowerCase()
  const start = filters.start || '', end = filters.end || ''
  const invalidRange = Boolean(start && end && start > end)
  const groups = new Map()
  let total = 0
  if (!invalidRange) for (const row of dated(rows)) {
    const date = dayOf(row), name = row.category || '其他'
    if ((start && date < start) || (end && date > end) || (filters.category && filters.category !== name)
      || (query && ![name,row.remark].some(value => String(value || '').toLowerCase().includes(query)))) continue
    const key = date.slice(0, 7), group = groups.get(key) || { key, label:key.slice(0,4) + '年' + Number(key.slice(5)) + '月', cents:0, entries:[] }
    group.cents += cents(row.amount)
    total += cents(row.amount)
    group.entries.push({record:row,id:row.id,date:shortDate(row),timestamp:timestamp(row),name,color:colors.get(name) || '#88978f',
      remark:row.remark, amount:amount(row.amount)})
    groups.set(key, group)
  }
  return { categories, invalidRange, total:amount(total / 100),
    groups:[...groups.values()].map(({cents, ...group}) => ({...group, total:amount(cents / 100)})) }
}

module.exports = { fuelRecordYears, fuelRecordList, fuelTripReport, expenseRecordList }
