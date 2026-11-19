<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showFailToast, showSuccessToast } from 'vant'
import { useUserStore } from '../stores/user'
import { listMembers } from '../api/user'
import { pageLedger, ledgerSummary, createLedger, updateLedger, deleteLedger } from '../api/ledger'

const router = useRouter(), route = useRoute(), user = useUserStore()
const sourceTodoId = computed(() => String(route.query.todoId || ''))
const localDate = () => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}` }
const month = ref(localDate().slice(0, 7))
const items = ref([]), total = ref(0), page = ref(1), busy = ref(false), error = ref(''), members = ref([])
const summary = ref({ totals: [], categories: [] }), editing = ref(false)
const categories = ['餐饮','买菜','居家','交通','购物','订阅','缴费','医疗','娱乐','工资','其他']
const filters = reactive({ entryType: '', category: '', payerId: '' })
const form = reactive({})
let requestVersion = 0
const income = computed(() => Number(summary.value.totals.find(x => x.entry_type === 'INCOME')?.amount || 0))
const expense = computed(() => Number(summary.value.totals.find(x => x.entry_type === 'EXPENSE')?.amount || 0))
const money = (value) => Number(value || 0).toFixed(2)
const name = (id) => members.value.find(m => m.id === id)?.nickname || members.value.find(m => m.id === id)?.username || '已离开成员'
const canManage = (item) => item.createdBy === user.userId || user.canManageMembers
const sourceRoute = item => item.sourceType === 'TODO' ? `/todo/${item.sourceId}` : item.sourceType?.startsWith('VEHICLE_') ? '/vehicles' : '/expenses'
const sourceLabel = item => item.sourceType === 'TODO' ? '待办' : item.sourceType?.startsWith('VEHICLE_') ? '车辆' : '专项'
async function load(reset = true) {
  if (busy.value && !reset) return
  const version = ++requestVersion
  busy.value = true; error.value = ''
  try {
    const next = reset ? 1 : page.value + 1
    const [list, totals] = await Promise.all([pageLedger({ month: month.value, current: next, size: 20, sourceTodoId:sourceTodoId.value, allMonths:Boolean(sourceTodoId.value), ...filters }), ledgerSummary(month.value)])
    if (version !== requestVersion) return
    items.value = reset ? list.data.records : [...items.value, ...list.data.records]
    page.value = next; total.value = list.data.total; summary.value = totals.data
  } catch (e) { if (version === requestVersion) error.value = e.message || '加载失败' } finally { if (version === requestVersion) busy.value = false }
}
function open(item) {
  Object.assign(form, item ? { ...item } : { id:'', entryType:'EXPENSE', category:'餐饮', description:'', amount:'', occurredOn:localDate(), payerId:user.userId, remark:'', sourceType:'', relatedExpenseId:'' })
  editing.value = true
}
async function save() {
  if (!form.description?.trim() || !form.amount || !form.payerId || !form.occurredOn) { showFailToast('请填写账单内容、金额和付款人'); return }
  if (busy.value) return
  busy.value = true
  try { await (form.id ? updateLedger(form) : createLedger(form)); editing.value = false; showSuccessToast('已保存') }
  catch (e) { showFailToast(e.message || '保存失败'); return }
  finally { busy.value = false }
  await load()
}
async function remove(item) {
  try { await showConfirmDialog({ title:'删除账单', message:item.sourceType ? `确认删除「${item.description}」的账单吗？关联的原记录会保留。` : `确认删除「${item.description}」吗？` }); await deleteLedger(item.id); showSuccessToast('已删除'); await load() }
  catch (e) { if (e?.message) showFailToast(e.message) }
}
watch(month, () => load())
onMounted(async () => { try { members.value = (await listMembers()).data || [] } catch { error.value = '家庭成员加载失败' }; await load() })
</script>

<template>
  <section class="ledger">
    <header><button class="back" @click="router.back()" aria-label="返回"><van-icon name="arrow-left" /></button><div><small>家庭生活</small><h1>家庭记账</h1></div><button v-if="!sourceTodoId" class="add" @click="open()"><van-icon name="plus" /> 记一笔</button></header>
    <template v-if="!sourceTodoId"><div class="month"><input v-model="month" type="month" aria-label="选择月份" /></div>
    <div class="totals"><div><small>收入</small><strong>¥{{ money(income) }}</strong></div><div><small>支出</small><strong>¥{{ money(expense) }}</strong></div><div><small>结余</small><strong>¥{{ money(income-expense) }}</strong></div></div>
    <h2>支出分类</h2><div v-if="summary.categories.length" class="categories"><span v-for="item in summary.categories" :key="item.category">{{ item.category }} ¥{{ money(item.amount) }}</span></div><p v-else class="muted">本月暂无支出</p></template>
    <div class="heading"><h2>{{ sourceTodoId ? '待办账单' : '账单明细' }}</h2><span>{{ total }} 笔</span></div>
    <div class="filters"><select v-model="filters.entryType" aria-label="收支类型" @change="load()"><option value="">全部收支</option><option value="EXPENSE">支出</option><option value="INCOME">收入</option></select><select v-model="filters.category" aria-label="分类" @change="load()"><option value="">全部分类</option><option v-for="category in categories" :key="category">{{ category }}</option></select><select v-model="filters.payerId" aria-label="成员" @change="load()"><option value="">全部成员</option><option v-for="member in members" :key="member.id" :value="member.id">{{ member.nickname || member.username }}</option></select></div>
    <div v-if="error" class="error" @click="load()">{{ error }}，点击重试</div><p v-else-if="!busy && !items.length" class="muted">{{ sourceTodoId ? '暂无关联账单' : '本月暂无账单' }}</p>
    <article v-for="item in items" :key="item.id" class="entry"><div><strong>{{ item.description }}</strong><p>{{ item.occurredOn }} · {{ item.category }} · {{ name(item.payerId) }}<span v-if="item.sourceCycleLabel"> · {{ item.sourceCycleLabel }}</span></p><p v-if="item.remark">{{ item.remark }}</p></div><div class="entry-right"><strong>{{ item.entryType === 'INCOME' ? '+' : '-' }}¥{{ money(item.amount) }}</strong><div v-if="canManage(item)"><button v-if="!item.relatedExpenseId" @click="open(item)">编辑</button><button @click="remove(item)">删除</button></div><button v-if="item.sourceType" @click="router.push(sourceRoute(item))">查看{{ sourceLabel(item) }}</button></div></article>
    <button v-if="items.length < total" class="more" :disabled="busy" @click="load(false)">加载更多</button>
    <van-popup v-model:show="editing" class="ledger-popup" position="bottom" round><form class="sheet" @submit.prevent="save"><div class="heading"><h2>{{ form.id ? '编辑账单' : '记一笔' }}</h2><button type="button" @click="editing = false" aria-label="关闭"><van-icon name="cross" /></button></div><div class="types"><button type="button" :class="{active:form.entryType==='EXPENSE'}" @click="form.entryType='EXPENSE'">支出</button><button v-if="!form.sourceType" type="button" :class="{active:form.entryType==='INCOME'}" @click="form.entryType='INCOME'">收入</button></div><label>金额<input v-model="form.amount" type="number" min="0.01" step="0.01" required /></label><label>内容<input v-model="form.description" maxlength="200" required /></label><label>分类<select v-model="form.category"><option v-for="category in categories" :key="category">{{ category }}</option></select></label><label>日期<input v-model="form.occurredOn" type="date" required /></label><label>付款人<select v-model="form.payerId"><option v-for="member in members" :key="member.id" :value="member.id">{{ member.nickname || member.username }}</option></select></label><label>备注<input v-model="form.remark" maxlength="500" /></label><button class="add save" type="submit" :disabled="busy">保存</button></form></van-popup>
  </section>
</template>
<style scoped>
:global(.ledger-popup){max-width:430px;left:0;right:0;margin:0 auto}
.ledger{padding:12px 4px 30px;color:#382d27}.ledger header,.heading,.entry,.month,.filters,.types{display:flex;align-items:center;gap:10px}.ledger header h1,.heading h2{margin:0}.ledger header h1{font-size:21px}.ledger header small{color:#ed7419}.back,.heading button{border:0;background:transparent;color:#a77455;font-size:18px}.add{margin-left:auto;border:0;border-radius:8px;background:#f77716;color:white;padding:9px 13px}.month{margin:18px 0 10px}.month input,.filters select,.sheet input,.sheet select{border:1px solid #ead9cd;background:#fff;border-radius:6px;padding:9px;color:#46372d;min-width:0}.totals{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px;border-block:1px solid #ead9cd;padding:14px 0}.totals small,.totals strong{display:block}.totals small,.muted,.heading span,.entry p,.entry small{color:#987f70;font-size:12px}.totals strong{font-size:16px;margin-top:5px;overflow-wrap:anywhere}.ledger h2{font-size:16px;margin:20px 0 10px}.categories{display:flex;gap:8px;flex-wrap:wrap}.categories span{background:#fff2e5;padding:5px 9px;border-radius:6px;font-size:12px}.heading{justify-content:space-between}.filters select{flex:1;width:0;font-size:12px}.entry{justify-content:space-between;align-items:flex-start;padding:14px 0;border-bottom:1px solid #eee1d7;gap:14px}.entry>div:first-child{min-width:0;overflow-wrap:anywhere}.entry p{margin:5px 0}.entry-right{text-align:right;flex:none}.entry-right button,.more{border:0;background:transparent;color:#df6a19;padding:6px}.sheet{padding:18px;display:grid;gap:10px;max-height:85vh;overflow:auto}.sheet label{display:grid;gap:4px;font-size:13px}.sheet .heading h2{margin:0}.types button{border:1px solid #ead9cd;background:#fff;border-radius:6px;padding:7px 20px}.types .active{background:#fff0e1;color:#ed7419}.save{width:100%;margin:4px 0}.error{color:#b94c40;font-size:13px}
</style>
