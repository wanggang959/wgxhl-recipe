const categories = { COOK:'做饭', GROCERY:'采购', LIFE:'生活', BIRTHDAY:'生日', ANNIVERSARY:'纪念日', SERVER:'服务器', DOMAIN:'域名', SSL:'证书', PAYMENT:'缴费', OTHER:'其他' }
function present(todo) {
  return { ...todo, categoryLabel: categories[todo.category] || '其他', displayDate: todo.birthdaySolarLabel || todo.dueLabel || String(todo.dueTime || '').replace('T',' ').slice(0,16) || '未设置时间', displayOwner: todo.ownerName || '未指定' }
}
module.exports = { present }
