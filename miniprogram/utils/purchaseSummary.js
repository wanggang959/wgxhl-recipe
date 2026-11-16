const rangeOptions = [
  { key: 'today', label: '今日' },
  { key: 'tomorrow', label: '明日' },
  { key: 'next2', label: '近两日' },
  { key: 'next3', label: '近三日' },
  { key: 'all', label: '全部' },
]

function parseNumber(text) {
  if (!text) return null
  if (!/^\d+(\.\d+)?$/.test(String(text).trim())) return null
  return Number(text)
}

function formatAmount(item) {
  const parts = []
  item.unitParts.forEach(unitPart => {
    if (unitPart.hasNumeric) {
      const total = Number.isInteger(unitPart.total) ? String(unitPart.total) : String(Number(unitPart.total.toFixed(2)))
      parts.push(`${total}${unitPart.unit}`)
    }
    unitPart.extras.forEach(text => parts.push(`${text}${unitPart.unit}`))
  })
  return parts.length > 0 ? parts.join('+') : '适量'
}

function buildIngredientSummary(details) {
  const map = new Map()
  details.forEach(detail => {
    ;(detail.ingredientList || []).forEach(item => {
      const name = item.ingredientName || '未命名食材'
      const unit = item.unit || ''
      const amountText = String(item.amount || '').trim()
      const numeric = parseNumber(amountText)
      if (!map.has(name)) map.set(name, { name, image: item.ingredientImage || '', unitParts: new Map() })
      const target = map.get(name)
      if (!target.image && item.ingredientImage) target.image = item.ingredientImage
      if (!target.unitParts.has(unit)) target.unitParts.set(unit, { unit, total: 0, hasNumeric: false, extras: [] })
      const unitPart = target.unitParts.get(unit)
      if (numeric !== null) {
        unitPart.total += numeric
        unitPart.hasNumeric = true
      } else if (amountText) {
        unitPart.extras.push(amountText)
      }
    })
  })
  return [...map.values()].map(item => {
    const amount = formatAmount(item)
    return { key: `ingredient:${item.name}:${amount}`, name: item.name, image: item.image, amount }
  })
}

function buildSeasoningSummary(details) {
  const map = new Map()
  details.forEach(detail => {
    ;(detail.seasoningList || []).forEach(item => {
      const name = item.seasoningName || '未命名调料'
      if (!map.has(name)) map.set(name, { key: `seasoning:${name}`, name, image: item.seasoningImage || '' })
    })
  })
  return [...map.values()]
}

function summarySignature(ingredients, seasonings) {
  return JSON.stringify({
    ingredients: ingredients.map(item => [item.name, item.amount]).sort(),
    seasonings: seasonings.map(item => item.name).sort(),
  })
}

function isInRange(dateText, range, today) {
  if (!dateText) return range === 'all'
  const text = String(dateText).slice(0, 10)
  if (range === 'all') return true
  if (range === 'today') return text === today
  const add = days => {
    const date = new Date(`${today}T00:00:00`)
    date.setDate(date.getDate() + days)
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
  }
  if (range === 'tomorrow') return text === add(1)
  const end = range === 'next2' ? add(1) : range === 'next3' ? add(2) : ''
  return Boolean(end) && text >= today && text <= end
}

function formatPurchaseLine(item, mark = {}) {
  const quantity = mark.quantity || item.amount || ''
  return quantity ? `${item.name} ${quantity}` : item.name
}

function statusLabel(mark = {}) {
  if (mark.status === 'enough') return '已够'
  if (mark.status === 'buy') return mark.quantity ? `待购 ${mark.quantity}` : '待购'
  return ''
}

module.exports = {
  rangeOptions,
  buildIngredientSummary,
  buildSeasoningSummary,
  summarySignature,
  isInRange,
  formatPurchaseLine,
  statusLabel,
}
