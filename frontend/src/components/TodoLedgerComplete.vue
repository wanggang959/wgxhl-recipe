<script setup>
import { ref, watch } from 'vue'
import { showFailToast, showSuccessToast } from 'vant'
import { listMembers } from '../api/user'
import { completeTodo } from '../api/todo'
import { completeWithLedger } from '../api/ledger'
import { useUserStore } from '../stores/user'
const props = defineProps({ todo: { type: Object, default: null } })
const emit = defineEmits(['close', 'done'])
const user = useUserStore(), members = ref([]), payer = ref(''), amount = ref(''), busy = ref(false), error = ref('')
watch(() => props.todo, async todo => {
  if (!todo) return
  payer.value = user.userId; amount.value = todo.ledgerSetting?.estimatedAmount ?? ''; error.value = ''
  try { members.value = (await listMembers()).data || [] } catch { members.value = []; error.value = '家庭成员加载失败' }
})
async function finish(record) {
  if (busy.value) return
  if (record && (!payer.value || !amount.value || Number(amount.value) <= 0)) { error.value = '请选择付款人并填写实际金额'; return }
  busy.value = true; error.value = ''
  try {
    if (record) await completeWithLedger({ id:props.todo.id, expectedDueTime:props.todo.dueTime, payerId:payer.value, actualAmount:Number(amount.value) })
    else await completeTodo(props.todo.id)
    showSuccessToast(record ? '已完成并记账' : '已完成'); emit('done'); emit('close')
  } catch (e) { error.value = e.message || '操作失败'; showFailToast(error.value) }
  finally { busy.value = false }
}
</script>
<template>
  <van-popup :show="Boolean(todo)" class="ledger-complete-popup" position="bottom" round @update:show="value => !value && emit('close')">
    <div v-if="todo" class="sheet"><header><h2>完成并记账</h2><button @click="emit('close')" aria-label="关闭"><van-icon name="cross" /></button></header><div class="details"><strong>{{ todo.ledgerSetting.description }}</strong><span>{{ todo.ledgerSetting.category }} · {{ String(todo.dueTime || '').slice(0, 10) }}</span></div><label>付款人<select v-model="payer"><option value="">请选择付款人</option><option v-for="member in members" :key="member.id" :value="member.id">{{ member.nickname || member.username }}</option></select></label><label>实际金额<input v-model="amount" type="number" min="0.01" step="0.01" placeholder="输入实际金额" /></label><p v-if="error" class="error">{{ error }}</p><button class="primary" :disabled="busy" @click="finish(true)">完成并记账</button><button class="secondary" :disabled="busy" @click="finish(false)">仅完成，不记账</button></div>
  </van-popup>
</template>
<style scoped>
:global(.ledger-complete-popup){max-width:430px;left:0;right:0;margin:0 auto}
.sheet{padding:18px;display:grid;gap:14px;color:#382d27}.sheet header{display:flex;justify-content:space-between;align-items:center}.sheet h2{font-size:18px;margin:0}.sheet header button{border:0;background:transparent;color:#987b68;font-size:18px}.details{display:grid;gap:5px;padding:12px;background:#fff7ed;border-radius:6px}.details span{color:#987b68;font-size:12px}.sheet label{display:grid;gap:6px;font-size:13px}.sheet input,.sheet select{border:1px solid #ead9cd;border-radius:6px;background:white;padding:11px}.primary,.secondary{border:0;border-radius:7px;padding:11px}.primary{background:#f77716;color:white}.secondary{background:#fff4e9;color:#a65b24}.error{margin:0;color:#b94c40;font-size:13px}
</style>
