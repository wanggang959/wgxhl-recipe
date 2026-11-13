const day = 86400000
function dateText(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}
function groupItems(todos, plans, category, range, now = new Date()) {
  const today = dateText(now)
  const midnight = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const tomorrow = new Date(midnight); tomorrow.setDate(tomorrow.getDate() + 1)
  const entries = todos.map(data => ({type:'todo', id:data.id, category:data.category, date:String(data.dueTime || '').slice(0, 10), sortTime:String(data.dueTime || '9999-12-31T23:59:59').replace(' ', 'T'), data}))
    .concat(plans.map(data => ({type:'wanted', id:data.id, category:'COOK', date:data.plannedDate || '', sortTime:`${data.plannedDate || '9999-12-31'}T00:00:00`, data})))
  function matchesDate(value, selected) {
    if (selected === 'all') return true
    if (!value) return false
    const parts = value.split('-').map(Number)
    const diff = Math.round((new Date(parts[0], parts[1] - 1, parts[2]) - midnight) / day)
    return selected === 'today' ? diff === 0 : diff >= 0 && diff < (selected === 'three' ? 3 : 7)
  }
  const groups = []
  entries.filter(item => (!category || category === item.category) && matchesDate(item.date, range))
    .sort((a,b) => String(a.sortTime).localeCompare(String(b.sortTime)))
    .forEach(item => {
      const key = item.date || 'none'
      let group = groups[groups.length - 1]
      if (!group || group.key !== key) {
        const [year, month, date] = item.date.split('-')
        const label = !item.date ? '未定时间' : item.date === today ? '今日' : item.date === dateText(tomorrow) ? '明日' : `${year !== String(now.getFullYear()) ? `${Number(year)}年` : ''}${Number(month)}月${Number(date)}日`
        group = { key, label, items:[] }; groups.push(group)
      }
      group.items.push(item)
    })
  return { groups, todayCount:entries.filter(item => matchesDate(item.date, 'today')).length, dueSoonCount:entries.filter(item => matchesDate(item.date, 'week')).length }
}
module.exports = { groupItems, dateText }
