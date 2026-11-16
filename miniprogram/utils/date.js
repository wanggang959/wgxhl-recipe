function dateText(date = new Date()) {
  const target = date instanceof Date ? date : new Date(date)
  return `${target.getFullYear()}-${String(target.getMonth() + 1).padStart(2, '0')}-${String(target.getDate()).padStart(2, '0')}`
}

function addDays(days, base = new Date()) {
  const date = new Date(base.getFullYear(), base.getMonth(), base.getDate())
  date.setDate(date.getDate() + Number(days || 0))
  return dateText(date)
}

function formatDateLabel(text) {
  const parts = String(text || '').split('-')
  if (parts.length !== 3) return String(text || '')
  return `${Number(parts[1])}月${Number(parts[2])}日`
}

function isPastDate(text, today = dateText()) {
  return Boolean(text) && String(text).slice(0, 10) < today
}

function dayLabel(text, today = dateText()) {
  if (!text) return '未定日期'
  if (text === today) return '今天'
  if (text === addDays(1, new Date(`${today}T00:00:00`))) return '明天'
  if (text === addDays(2, new Date(`${today}T00:00:00`))) return '后天'
  return formatDateLabel(text)
}

function dateTimeText(value) {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 16)
}

module.exports = { dateText, addDays, formatDateLabel, isPastDate, dayLabel, dateTimeText }
