export function monthlyFuelTotal(records, now = new Date()) {
  const key = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
  return records.reduce((sum, row) => String(row.occurredAt || '').startsWith(key) ? sum + Math.round(Number(row.paidAmount || 0) * 100) : sum, 0) / 100
}

export function monthlyConsumption(trend, months, now = new Date()) {
  const current = now.getFullYear() * 12 + now.getMonth()
  const groups = new Map()
  for (const point of trend || []) {
    const match = /^(\d{4})-(\d{2})/.exec(String(point.date || ''))
    const value = Number(point.value)
    if (!match || point.value == null || !Number.isFinite(value) || value < 0) continue
    const month = Number(match[2]) - 1
    if (month < 0 || month > 11) continue
    const offset = Number(match[1]) * 12 + month
    if (offset > current || offset <= current - months) continue
    const key = `${match[1]}-${match[2]}`
    const group = groups.get(key) || { sum: 0, count: 0 }
    group.sum += value; group.count += 1; groups.set(key, group)
  }
  return [...groups.entries()].sort(([a], [b]) => a.localeCompare(b)).map(([month, group]) => ({ month, value: group.sum / group.count }))
}

export const VEHICLE_RANGES = [
  { value: '6m', label: '半年' }, { value: 'year', label: '今年' }, { value: 'rolling', label: '近一年' },
  { value: '2y', label: '近两年' }, { value: '3y', label: '近三年' }, { value: 'all', label: '全部' },
]

export function readingPoints(trend, range, now = new Date()) {
  const current = now.getFullYear() * 12 + now.getMonth()
  const months = { '6m': 6, year: now.getMonth() + 1, rolling: 12, '2y': 24, '3y': 36 }[range] || 12
  return (trend || []).flatMap((row, index) => {
    const day = String(row.date || '').slice(0, 10)
    const match = /^([0-9]{4})-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])$/.exec(day)
    const value = Number(row.value)
    if (!match || row.value == null || !Number.isFinite(value) || value < 0) return []
    const offset = Number(match[1]) * 12 + Number(match[2]) - 1
    if (offset > current || (range !== 'all' && offset <= current - months)) return []
    return [{ key: row.recordId || `${day}-${index}`, day, value }]
  }).sort((a, b) => a.day.localeCompare(b.day))
}

export function nearestPlotIndex(x, left, right, count) {
  if (!count || !Number.isFinite(x) || right <= left) return -1
  return Math.max(0, Math.min(count - 1, Math.round((x - left) / (right - left) * (count - 1))))
}

export function ringSliceAt(x, y, width, height, slices) {
  if (!(width > 0 && height > 0) || !Number.isFinite(x) || !Number.isFinite(y)) return null
  const dx = x / width - 0.5, dy = y / height - 0.5
  const radius = Math.hypot(dx, dy)
  if (radius < 0.365 || radius > 0.5) return null
  const position = (Math.atan2(dx, -dy) / (Math.PI * 2) * 100 + 100) % 100
  return slices.find(slice => position >= slice.start && position < slice.end) || null
}

export function chartGeometry(points) {
  if (!points.length) return []
  const values = points.map(point => point.value)
  const min = Math.min(...values), max = Math.max(...values)
  const range = max - min
  return points.map((point, index) => ({ ...point, x: points.length === 1 ? 150 : 20 + index * 260 / (points.length - 1), y: range === 0 ? 65 : 105 - (point.value - min) * 80 / range }))
}
