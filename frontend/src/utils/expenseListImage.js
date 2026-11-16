const WIDTH = 720
const MAX_HEIGHT = 16000
const FONT = '"PingFang SC", "Microsoft YaHei", sans-serif'

export function expenseTotal(items) {
  return items.reduce((cents, item) => {
    const amount = Number(item.actualAmount ?? 0)
    return cents + (Number.isFinite(amount) ? Math.round((amount + Number.EPSILON) * 100) : 0)
  }, 0) / 100
}

export function wrapCanvasText(context, value, maxWidth) {
  const text = String(value || '—').replace(/\s+/g, ' ').trim() || '—'
  const lines = []
  let line = ''
  for (const char of text) {
    if (line && context.measureText(line + char).width > maxWidth) {
      lines.push(line)
      line = char
    } else line += char
  }
  if (line) lines.push(line)
  return lines
}

export function expenseImageLayout(context, projectName, items) {
  context.font = `bold 34px ${FONT}`
  const title = wrapCanvasText(context, projectName, 360)
  context.font = `bold 28px ${FONT}`
  const total = wrapCanvasText(context, `¥${expenseTotal(items).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`, 245)
  context.font = `26px ${FONT}`
  const rows = items.map((item) => {
    const amount = Number(item.actualAmount ?? 0)
    return {
      name: wrapCanvasText(context, item.itemName, 300),
      quantity: wrapCanvasText(context, item.quantity, 105),
      amount: wrapCanvasText(context, `¥${(Number.isFinite(amount) ? amount : 0).toLocaleString('zh-CN', { maximumFractionDigits: 2 })}`, 175),
    }
  })
  const start = 85 + Math.max(title.length, total.length) * 46 + 72
  const height = start + rows.reduce((sum, row) => sum + Math.max(1, row.name.length, row.quantity.length, row.amount.length) * 38 + 26, 0) + 48
  if (height > MAX_HEIGHT) throw new Error('清单过长，请选择分类或状态后再导出')
  return { title, total, rows, start, height }
}

export async function createExpenseListImage(projectName, items) {
  if (!items.length) throw new Error('当前筛选条件下没有开支项')
  const canvas = document.createElement('canvas')
  canvas.width = WIDTH
  const context = canvas.getContext('2d')
  if (!context) throw new Error('当前设备暂不支持生成图片')
  const layout = expenseImageLayout(context, projectName, items)
  canvas.height = layout.height
  context.fillStyle = '#fffaf5'
  context.fillRect(0, 0, WIDTH, layout.height)
  context.fillStyle = '#ff7912'
  context.font = `bold 21px ${FONT}`
  context.fillText('专项开支清单', 44, 46)
  context.textAlign = 'right'
  context.fillStyle = '#8b6e5b'
  context.font = `21px ${FONT}`
  context.fillText('总花费', 676, 46)
  context.fillStyle = '#da650e'
  context.font = `bold 28px ${FONT}`
  layout.total.forEach((line, index) => context.fillText(line, 676, 91 + index * 46))
  context.textAlign = 'left'
  context.fillStyle = '#302720'
  context.font = `bold 34px ${FONT}`
  layout.title.forEach((line, index) => context.fillText(line, 44, 91 + index * 46))
  const header = layout.start - 47
  context.fillStyle = '#fff0df'
  context.fillRect(32, header - 30, 656, 50)
  context.fillStyle = '#805b43'
  context.font = `bold 23px ${FONT}`
  context.textAlign = 'left'; context.fillText('开支项目', 44, header + 3)
  context.fillText('数量', 370, header + 3)
  context.textAlign = 'right'; context.fillText('实际金额', 676, header + 3)
  context.font = `26px ${FONT}`
  let y = layout.start + 12
  for (const row of layout.rows) {
    const lineCount = Math.max(row.name.length, row.quantity.length, row.amount.length)
    context.fillStyle = '#342d29'; context.textAlign = 'left'
    row.name.forEach((line, index) => context.fillText(line, 44, y + index * 38))
    context.fillStyle = '#7b6b60'
    row.quantity.forEach((line, index) => context.fillText(line, 370, y + index * 38))
    context.fillStyle = '#da650e'; context.textAlign = 'right'
    row.amount.forEach((line, index) => context.fillText(line, 676, y + index * 38))
    y += lineCount * 38 + 26
    context.strokeStyle = '#f0e2d6'; context.beginPath(); context.moveTo(44, y - 23); context.lineTo(676, y - 23); context.stroke()
  }
  return new Promise((resolve, reject) => canvas.toBlob((blob) => blob ? resolve(blob) : reject(new Error('图片生成失败，请稍后重试')), 'image/png'))
}
