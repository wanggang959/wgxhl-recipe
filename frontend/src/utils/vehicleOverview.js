function chartMonths(records, range, now) {
  const current = now.getFullYear() * 12 + now.getMonth()
  const count = { year: now.getMonth() + 1, rolling: 12, '6m': 6, '2y': 24, '3y': 36 }[range] || 12
  let first = current - count + 1
  if (range === 'all') {
    first = current
    for (const row of records) {
      const date = /^([0-9]{4})-(0[1-9]|1[0-2])/.exec(String(row.occurredAt || ''))
      if (date && Number(date[1]) >= 1900) first = Math.min(first, Number(date[1]) * 12 + Number(date[2]) - 1)
    }
  }
  return Array.from({length: current - first + 1}, (_, i) => {
    const date = new Date(Math.floor((first + i) / 12), (first + i) % 12, 1)
    const year = date.getFullYear(), month = date.getMonth() + 1
    return { key: `${year}-${String(month).padStart(2, '0')}`, label: i === 0 || month === 1 ? `${year}-${month}` : `${month}月`, amount: 0 }
  })
}

export function fuelOverview(records, now = new Date(), range = 'rolling') {
  const ordered = records.filter(row => row.occurredAt && row.mileage != null && Number.isFinite(Number(row.mileage)))
    .slice().sort((a, b) => a.occurredAt.localeCompare(b.occurredAt))
  const first = ordered[0], last = ordered[ordered.length - 1]
  const distance = ordered.length > 1 && ordered.every((row, i) => !i || Number(row.mileage) >= Number(ordered[i - 1].mileage))
    ? Number(last.mileage) - Number(first.mileage) : null
  const days = distance == null ? 0 : (Date.parse(last.occurredAt.slice(0, 10)) - Date.parse(first.occurredAt.slice(0, 10))) / 86400000
  const cents = n => Math.round(Number(n ?? 0) * 100)
  const total = records.reduce((sum, row) => sum + cents(row.paidAmount), 0)
  const discount = records.reduce((sum, row) => sum + (row.displayedAmount == null ? 0 : Math.max(0, cents(row.displayedAmount) - cents(row.paidAmount))), 0)
  const months = chartMonths(records, range, now)
  const totals = new Map(months.map(month => [month.key, month]))
  for (const row of records) {
    const month = totals.get(String(row.occurredAt || '').slice(0, 7))
    if (month) month.amount += cents(row.paidAmount)
  }
  const peak = Math.max(1, ...months.map(month => month.amount))
  return {
    distance,
    dailyMileage: days > 0 ? distance / days : null,
    costPerKm: distance > 0 ? total / 100 / distance : null,
    total: total / 100,
    discount: discount / 100,
    months: months.map(month => ({ ...month, amount: month.amount / 100, height: month.amount / peak * 100 })),
  }
}

const spendColors = ['#159ce4', '#f8bf19', '#dc358a', '#fa6356', '#24bf63', '#8b7bcc']

export function spendOverview(fuels, expenses, range = 'year', now = new Date(), expenseTypes = []) {
  const months = chartMonths(fuels.concat(expenses), range, now)
  const byMonth = new Map(months.map(month => [month.key, new Map()]))
  const byCategory = new Map()
  const rows = fuels.map(row => ({ ...row, category: '油费', amount: row.paidAmount })).concat(expenses)
  for (const row of rows) {
    const month = byMonth.get(String(row.occurredAt || '').slice(0, 7))
    const amount = Math.round(Number(row.amount) * 100)
    if (!month || !Number.isFinite(amount) || amount <= 0) continue
    const category = row.category || '其他'
    month.set(category, (month.get(category) || 0) + amount)
    byCategory.set(category, (byCategory.get(category) || 0) + amount)
  }
  const configuredColors = new Map(expenseTypes.map(type => [type.name, type.color]))
  const categoryColor = (name, index) => {
    if (name === '油费') return spendColors[0]
    if (configuredColors.has(name)) return configuredColors.get(name)
    if (/保险|规费/.test(name)) return spendColors[2]
    if (/保养|维修/.test(name)) return spendColors[1]
    if (/洗车/.test(name)) return spendColors[4]
    if (/行车|停车|路费/.test(name)) return spendColors[3]
    return index < 4 ? spendColors[5] : `hsl(${index * 137 % 360}, 55%, 48%)`
  }
  const categories = Array.from(byCategory).sort(([a, av], [b, bv]) => Number(b === '油费') - Number(a === '油费') || bv - av)
    .map(([name, value], index) => ({ name, amount: value / 100, color: categoryColor(name, index) }))
  const total = Array.from(byCategory.values()).reduce((sum, amount) => sum + amount, 0) / 100
  const bars = months.map(month => {
    const amounts = byMonth.get(month.key)
    const segments = categories.map(category => ({ ...category, amount: (amounts.get(category.name) || 0) / 100 })).filter(s => s.amount > 0)
    return { ...month, segments, amount: Array.from(amounts.values()).reduce((sum, amount) => sum + amount, 0) / 100 }
  })
  const peak = Math.max(1, ...bars.map(bar => bar.amount))
  let start = 0
  const slices = categories.map(category => {
    const end = start + category.amount / total * 100
    const slice = { ...category, start, end }
    start = end
    return slice
  })
  return { total, categories, slices, bars: bars.map(bar => ({ ...bar, height: bar.amount / peak * 100, segments: bar.segments.map(segment => ({ ...segment, height: segment.amount / bar.amount * 100 })) })) }
}

export function expenseSummary(overview, expenses, ownedSince, now = new Date()) {
  const extra = expenses.reduce((sum, row) => sum + Math.round(Number(row.amount || 0) * 100), 0) / 100
  const total = (Math.round(overview.total * 100) + Math.round(extra * 100)) / 100
  const today = Date.UTC(now.getFullYear(), now.getMonth(), now.getDate())
  const ownedDays = ownedSince ? (today - Date.parse(ownedSince)) / 86400000 : 0
  const days = ownedDays > 0 ? ownedDays : overview.dailyMileage > 0 ? overview.distance / overview.dailyMileage : 0
  return { extra, total, perKm: overview.distance > 0 ? total / overview.distance : null, perDay: days > 0 ? total / days : null, ownedDays: ownedDays >= 0 && ownedSince ? ownedDays : null }
}
