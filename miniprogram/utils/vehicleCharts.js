function monthlyFuelTotal(records, now = new Date()) {
  const key = `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}`
  return records.reduce((sum,row)=>String(row.occurredAt||'').startsWith(key)?sum+Math.round(Number(row.paidAmount||0)*100):sum,0)/100
}
function monthlyConsumption(trend, months, now = new Date()) {
  const current=now.getFullYear()*12+now.getMonth(), groups=new Map()
  for(const point of trend||[]) {
    const match=/^(\d{4})-(\d{2})/.exec(String(point.date||'')), value=Number(point.value)
    if(!match||point.value==null||!Number.isFinite(value)||value<0)continue
    const month=Number(match[2])-1, offset=Number(match[1])*12+month
    if(month<0||month>11||offset>current||offset<=current-months)continue
    const key=`${match[1]}-${match[2]}`, group=groups.get(key)||{sum:0,count:0}
    group.sum+=value;group.count++;groups.set(key,group)
  }
  return [...groups.entries()].sort(([a],[b])=>a.localeCompare(b)).map(([month,group])=>({month,value:group.sum/group.count}))
}
const VEHICLE_RANGES = [
  { value: '6m', label: '半年' }, { value: 'year', label: '今年' }, { value: 'rolling', label: '近一年' },
  { value: '2y', label: '近两年' }, { value: '3y', label: '近三年' }, { value: 'all', label: '全部' },
]

function readingPoints(trend, range, now = new Date()) {
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

function nearestPlotIndex(x, left, right, count) {
  if (!count || !Number.isFinite(x) || right <= left) return -1
  return Math.max(0, Math.min(count - 1, Math.round((x - left) / (right - left) * (count - 1))))
}

function ringSliceAt(x, y, width, height, slices) {
  if (!(width > 0 && height > 0) || !Number.isFinite(x) || !Number.isFinite(y)) return null
  const dx = x / width - 0.5, dy = y / height - 0.5
  const radius = Math.hypot(dx, dy)
  if (radius < 0.365 || radius > 0.5) return null
  const position = (Math.atan2(dx, -dy) / (Math.PI * 2) * 100 + 100) % 100
  return slices.find(slice => position >= slice.start && position < slice.end) || null
}

module.exports={monthlyFuelTotal,monthlyConsumption,VEHICLE_RANGES,readingPoints,nearestPlotIndex,ringSliceAt}
