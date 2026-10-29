<script setup>
import { computed, onMounted, ref } from 'vue'
import request from '../api/request'
import { useUserStore } from '../stores/user'

const user = useUserStore()
const tab = ref('summary')
const loading = ref(false)
const error = ref('')
const summary = ref(null)
const users = ref({ items: [], total: 0, page: 1 })
const notices = ref({ items: [], total: 0, page: 1 })
const uncertain = ref({ items: [], total: 0, page: 1 })
const keyword = ref('')
const noticeStatus = ref('')
const noticeChannel = ref('')
const noticeKeyword = ref('')
const noticeFrom = ref('')
const noticeTo = ref('')
const canLogin = computed(() => user.isLogin)
const stats = computed(() => summary.value ? [
  ['账号总数', summary.value.users], ['家庭总数', summary.value.households],
  ['菜谱总数', summary.value.recipes], ['未完成待办', summary.value.todos],
  ['近24小时发送成功', summary.value.sent24h], ['近24小时发送失败', summary.value.failed24h],
] : [])

async function load(nextTab = tab.value, page = 1) {
  tab.value = nextTab
  if (!user.isLogin) return
  loading.value = true
  error.value = ''
  try {
    if (nextTab === 'summary') summary.value = (await request.get('/platform-admin/summary')).data
    if (nextTab === 'users') users.value = (await request.get('/platform-admin/users', { params: { page, keyword: keyword.value } })).data
    if (nextTab === 'notices') notices.value = (await request.get('/platform-admin/notices', { params: { page, status: noticeStatus.value, channel: noticeChannel.value, keyword: noticeKeyword.value, from: noticeFrom.value, to: noticeTo.value } })).data
    if (nextTab === 'uncertain') uncertain.value = (await request.get('/platform-admin/notices/uncertain', { params: { page } })).data
  } catch (e) {
    error.value = e.message || '加载失败，请稍后重试'
  } finally { loading.value = false }
}

async function changeStatus(item) {
  const status = item.status === 'normal' ? 'disabled' : 'normal'
  if (!window.confirm(`确定${status === 'disabled' ? '停用' : '恢复'}账号“${item.nickname || item.username}”吗？`)) return
  try {
    await request.post('/platform-admin/users/status', { id: item.id, status })
    await load('users', users.value.page)
  } catch (e) { error.value = e.message || '操作失败' }
}

function time(value) { return value ? String(value).replace('T', ' ').slice(0, 19) : '暂无记录' }
function channel(value) { return ({ site: '站内', email: '邮件', push: 'App', none: '未开启' })[value] || value }
function statusText(value) { return ({ SUCCESS: '发送成功', FAIL: '发送失败', SKIP: '已跳过' })[value] || value }
onMounted(() => load())
</script>

<template>
  <div class="admin-shell">
    <header class="admin-header">
      <div><span class="brand-mark">王师傅家</span><h1>系统管理</h1></div>
      <a class="back-link" href="/#/recipes"><van-icon name="wap-home-o" /> 返回家庭</a>
    </header>
    <main class="admin-main">
      <div v-if="!canLogin" class="empty-state"><h2>请先登录</h2><p>使用王师傅账号登录后访问管理后台。</p><a class="primary-link" href="/#/login">去登录</a></div>
      <template v-else>
        <nav class="admin-nav" aria-label="管理页面">
          <button v-for="item in [['summary','系统概览'],['users','用户管理'],['notices','通知记录'],['uncertain','待核实']]" :key="item[0]" :class="{ active: tab === item[0] }" @click="load(item[0])">{{ item[1] }}</button>
        </nav>
        <p v-if="error" class="error" role="alert">{{ error }}</p>
        <p v-if="loading" class="muted">正在加载...</p>
        <template v-if="tab === 'summary' && summary">
          <div class="section-heading"><div><span class="eyebrow">系统概览</span><h2>运行情况</h2></div><button class="icon-button" title="刷新" aria-label="刷新" @click="load()"><van-icon name="replay" /></button></div>
          <div class="stat-grid"><div v-for="stat in stats" :key="stat[0]" class="stat"><span>{{ stat[0] }}</span><strong>{{ stat[1] }}</strong></div></div>
          <section class="scan"><div><h3>提醒扫描</h3><p>最近开始：{{ time(summary.scanStartedAt) }}</p><p>最近结束：{{ time(summary.scanFinishedAt) }}</p></div><span class="scan-badge">{{ summary.scanRunning ? '扫描中' : '等待下一次' }}</span></section>
          <p class="footnote">统计为发送尝试次数，邮件或推送服务接收并不代表已送达。</p>
        </template>
        <template v-if="tab === 'users'">
          <div class="section-heading"><div><span class="eyebrow">账号</span><h2>用户管理 <small>{{ users.total }}</small></h2></div></div>
          <form class="filters" @submit.prevent="load('users')"><input v-model="keyword" maxlength="100" placeholder="搜索用户名、昵称或邮箱" aria-label="搜索用户"><button class="primary" type="submit">搜索</button></form>
          <div class="table-wrap"><table><thead><tr><th>用户</th><th>邮箱</th><th>所属家庭</th><th>注册时间</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="item in users.items" :key="item.id"><td><strong>{{ item.nickname || item.username }}</strong><div class="muted">{{ item.username }}</div></td><td>{{ item.email || '未填写' }}</td><td>{{ item.households || '暂无' }}</td><td>{{ time(item.create_time) }}</td><td><span :class="['pill', item.status === 'normal' ? 'good' : 'bad']">{{ item.status === 'normal' ? '正常' : '已停用' }}</span></td><td><button v-if="item.id !== user.userId" class="text-action" @click="changeStatus(item)">{{ item.status === 'normal' ? '停用' : '恢复' }}</button><span v-else class="muted">当前账号</span></td></tr></tbody></table><div v-if="!loading && !users.items.length" class="empty-inline">没有找到用户</div></div>
          <div class="pagination"><span>共 {{ users.total }} 条</span><button :disabled="users.page <= 1" @click="load('users', users.page - 1)">上一页</button><span>{{ users.page }}</span><button :disabled="users.page * 20 >= users.total" @click="load('users', users.page + 1)">下一页</button></div>
        </template>
        <template v-if="tab === 'notices'">
          <div class="section-heading"><div><span class="eyebrow">提醒</span><h2>通知记录 <small>{{ notices.total }}</small></h2></div><button class="icon-button" title="刷新" aria-label="刷新" @click="load('notices')"><van-icon name="replay" /></button></div>
          <form class="filters" @submit.prevent="load('notices')"><input v-model="noticeKeyword" maxlength="100" placeholder="搜索接收人" aria-label="搜索接收人"><select v-model="noticeStatus" aria-label="发送状态"><option value="">全部状态</option><option value="SUCCESS">发送成功</option><option value="FAIL">发送失败</option><option value="SKIP">已跳过</option></select><select v-model="noticeChannel" aria-label="通知渠道"><option value="">全部渠道</option><option value="site">站内</option><option value="email">邮件</option><option value="push">App</option></select><input v-model="noticeFrom" type="date" aria-label="开始日期"><input v-model="noticeTo" type="date" aria-label="结束日期"><button class="primary" type="submit">筛选</button></form>
          <div class="table-wrap"><table><thead><tr><th>发送时间</th><th>接收人</th><th>渠道</th><th>状态</th><th>计划时间</th><th>失败原因</th></tr></thead><tbody><tr v-for="item in notices.items" :key="item.id"><td>{{ time(item.send_time) }}</td><td>{{ item.recipient_name || item.recipient_id || '未指定' }}</td><td>{{ channel(item.notify_type) }}</td><td><span :class="['pill', item.send_status === 'FAIL' ? 'bad' : 'good']">{{ statusText(item.send_status) }}</span></td><td>{{ time(item.target_time) }}</td><td class="reason">{{ item.error_msg || '—' }}</td></tr></tbody></table><div v-if="!loading && !notices.items.length" class="empty-inline">暂无通知记录</div></div>
          <div class="pagination"><span>共 {{ notices.total }} 条</span><button :disabled="notices.page <= 1" @click="load('notices', notices.page - 1)">上一页</button><span>{{ notices.page }}</span><button :disabled="notices.page * 20 >= notices.total" @click="load('notices', notices.page + 1)">下一页</button></div>
        </template>
        <template v-if="tab === 'uncertain'">
          <div class="section-heading"><div><span class="eyebrow">提醒</span><h2>待核实 <small>{{ uncertain.total }}</small></h2></div><button class="icon-button" title="刷新" aria-label="刷新" @click="load('uncertain')"><van-icon name="replay" /></button></div>
          <p class="footnote">发送结果不确定，请先核查接收记录；系统不会自动重复发送。</p>
          <div class="table-wrap"><table><thead><tr><th>记录时间</th><th>接收人</th><th>渠道</th><th>计划时间</th><th>待办 ID</th><th>状态</th></tr></thead><tbody><tr v-for="item in uncertain.items" :key="`${item.todo_id}-${item.recipient_key}-${item.notify_type}-${item.target_time}`"><td>{{ time(item.update_time) }}</td><td>{{ item.recipient_name || item.recipient_key || '未指定' }}</td><td>{{ channel(item.notify_type) }}</td><td>{{ time(item.target_time) }}</td><td>{{ item.todo_id }}</td><td><span class="pill bad">待核实</span></td></tr></tbody></table><div v-if="!loading && !uncertain.items.length" class="empty-inline">暂无待核实记录</div></div>
          <div class="pagination"><span>共 {{ uncertain.total }} 条</span><button :disabled="uncertain.page <= 1" @click="load('uncertain', uncertain.page - 1)">上一页</button><span>{{ uncertain.page }}</span><button :disabled="uncertain.page * 20 >= uncertain.total" @click="load('uncertain', uncertain.page + 1)">下一页</button></div>
          <template v-if="uncertain.gaps?.length">
            <h3 class="gap-heading">超过补扫范围的时间段</h3>
            <p class="footnote">这些时间段可能有未发送提醒，具体事项需要核查。</p>
            <div class="table-wrap"><table><thead><tr><th>家庭</th><th>开始时间</th><th>结束时间</th></tr></thead><tbody><tr v-for="gap in uncertain.gaps" :key="gap.household_id"><td>{{ gap.household_name || gap.household_id }}</td><td>{{ time(gap.last_gap_start) }}</td><td>{{ time(gap.last_gap_end) }}</td></tr></tbody></table></div>
          </template>
        </template>
      </template>
    </main>
  </div>
</template>

<style scoped>
.admin-shell{min-height:100vh;background:#f8f7f4;color:#282724}.admin-header{background:white;border-bottom:1px solid #eee8df;padding:18px max(20px,calc((100% - 1120px)/2));display:flex;align-items:center;justify-content:space-between;gap:16px}.brand-mark,.eyebrow{font-size:12px;font-weight:700;color:#ee7018}.admin-header h1{font-size:20px;margin:3px 0 0}.back-link{color:#725b49;text-decoration:none;font-size:14px;white-space:nowrap}.admin-main{max-width:1160px;margin:auto;padding:24px 20px 60px}.admin-nav{display:flex;gap:8px;border-bottom:1px solid #e9e5de;margin-bottom:26px}.admin-nav button{border:0;border-bottom:2px solid transparent;background:transparent;padding:12px 16px;cursor:pointer;color:#716f6a;font-size:14px}.admin-nav button.active{color:#df6510;border-color:#ef791f;font-weight:700}.section-heading{display:flex;align-items:center;justify-content:space-between;margin-bottom:18px}h2{font-size:22px;margin:4px 0}h2 small{font-size:13px;font-weight:400;color:#8b8881}h3{font-size:15px;margin:0 0 10px}.stat-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}.stat{background:white;border:1px solid #ede8e1;border-radius:8px;padding:18px}.stat span{display:block;color:#7c7771;font-size:13px}.stat strong{display:block;font-size:27px;margin-top:10px}.scan{display:flex;justify-content:space-between;align-items:center;background:white;border:1px solid #ede8e1;border-radius:8px;margin-top:16px;padding:18px}.scan p,.footnote,.muted{font-size:13px;color:#8b8881}.scan p{margin:5px 0}.scan-badge,.pill{font-size:12px;padding:5px 8px;border-radius:5px;background:#f5f4f0;color:#696760;white-space:nowrap}.pill.good{background:#edf7ef;color:#39794a}.pill.bad{background:#fff0ee;color:#bd4b40}.footnote{margin-top:14px}.filters{display:flex;gap:8px;margin-bottom:16px;flex-wrap:wrap}.filters input,.filters select{height:38px;border:1px solid #e8e2d9;border-radius:6px;padding:0 12px;background:white;color:#39352f;font-size:14px}.filters input{width:min(320px,100%)}button{font-family:inherit}.primary,.primary-link{background:#ef781b;color:white;border:0;border-radius:6px;padding:0 16px;height:38px;cursor:pointer}.primary-link{display:inline-flex;align-items:center;text-decoration:none}.icon-button{width:36px;height:36px;border:1px solid #eee4d8;border-radius:6px;background:white;color:#d36c1c;font-size:18px;cursor:pointer}.table-wrap{overflow-x:auto;background:white;border:1px solid #ede8e1;border-radius:8px}table{width:100%;border-collapse:collapse;min-width:730px;text-align:left;font-size:13px}th,td{padding:14px 16px;border-bottom:1px solid #f1efea;vertical-align:top}th{background:#faf9f7;color:#89837c;font-weight:500}td{color:#514b45}td strong{color:#302e2b}.reason{max-width:320px;overflow-wrap:anywhere}.text-action{background:none;border:0;color:#db6a17;cursor:pointer}.pagination{display:flex;align-items:center;justify-content:flex-end;gap:12px;margin-top:16px;font-size:13px;color:#827d76}.pagination button{background:white;border:1px solid #e9e2d9;border-radius:6px;padding:7px 10px;cursor:pointer}.pagination button:disabled{opacity:.45;cursor:default}.error{background:#fff0ee;color:#a93535;padding:12px;border-radius:6px;font-size:14px}.empty-state{text-align:center;padding:70px 10px}.empty-state p{color:#8b8881;margin:14px 0 22px}.empty-inline{text-align:center;color:#8b8881;padding:28px;font-size:13px}@media(max-width:600px){.admin-header{padding:16px}.admin-main{padding:16px 12px 48px}.admin-nav button{padding:10px 12px}.stat-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.stat{padding:14px}.stat strong{font-size:23px}.scan{padding:14px}.filters input{flex:1;min-width:0}}
.admin-nav { overflow-x: auto; scrollbar-width: none; }
.admin-nav::-webkit-scrollbar { display: none; }
.admin-nav button { flex: none; white-space: nowrap; }
.gap-heading { margin: 28px 0 4px; }
</style>
