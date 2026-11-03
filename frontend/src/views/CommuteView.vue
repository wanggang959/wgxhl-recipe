<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showFailToast, showSuccessToast, showConfirmDialog } from 'vant'
import { post } from '../api/request'
import { recordTimes, timePart } from '../utils/commuteRecordTime'
const router = useRouter()
const tab = ref('records'), busy = ref(false), error = ref(''), records = ref([]), recommendation = ref(null), page = ref(1), more = ref(true)
const settings = reactive({ noticeDay: 0, arrivalTime: '09:00', noticeTime: '07:00', bufferMinutes: 10, fallbackMinutes: 45, workDays: '1,2,3,4,5', siteEnabled: true, emailEnabled: false, pushEnabled: false, originName: '', originLat: null, originLon: null, destinationName: '', destinationLat: null, destinationLon: null })
const locations = reactive({ origin: { mode: 'city', query: '', items: [], loading: false }, destination: { mode: 'city', query: '', items: [], loading: false } })
const editor = ref(false)
function today() { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}` }
const form = reactive({ id: '', commuteDate: today(), direction: 'OUTBOUND', departureTime: '', arrivalTime: '', transport: '地铁', remark: '', includeStats: true, actualRain:'DRY' })
const sourceNames = { SAME_DAY_WEATHER: '同星期、同类天气', WORKDAY_WEATHER: '工作日、同类天气', SAME_DAY: '同星期历史', FALLBACK: '手动预计时长' }
const mailNames = { OFF:'未开启邮件', PENDING:'等待提醒时间', SUCCESS:'今日邮件已发送', SKIP:'今日邮件已暂停', IN_FLIGHT:'邮件发送结果待确认', UNKNOWN:'邮件发送结果未确认，请检查邮箱' }
const channelNames = {site:'站内',email:'邮箱',push:'App',all:'全部通道'}
const deliveryNames = {SUCCESS:'已发送',SKIP:'已跳过',IN_FLIGHT:'结果待确认',UNKNOWN:'结果未确认'}
async function load(reset = true) {
  if (reset) { page.value = 1; records.value = [] }
  try { const r = await post('/commute/records', { page: page.value }); records.value.push(...r.data); more.value = r.data.length === 30 }
  catch(e) { error.value = e.message }
}
async function refresh() {
  error.value = ''
  try { recommendation.value = (await post('/commute/recommendation')).data } catch(e) { error.value = e.message }
  await load()
}
async function saveSettings() {
  busy.value = true
  try { await post('/commute/settings/save', settings); showSuccessToast('已保存'); tab.value = 'records'; await refresh() }
  catch(e) { showFailToast(e.message) } finally { busy.value = false }
}
async function search(prefix) {
  const state = locations[prefix]; if (state.loading) return
  state.loading = true; state.items = []
  try {
    if (state.mode === 'share') {
      const url = state.query.match(/https:\/\/surl\.amap\.com\/[A-Za-z0-9]+/)?.[0]
      if (!url) throw new Error('请粘贴高德地点分享链接')
      const r = await post('/commute/resolve-share', { url }); choose(prefix, r.data)
    } else { state.items = (await post('/commute/cities', { keyword: state.query })).data; if (!state.items.length) showFailToast('没有找到地区') }
  } catch(e) { showFailToast(e.message) } finally { state.loading = false }
}
function choose(prefix, item) {
  settings[prefix+'Name'] = item.latitude !== undefined ? item.name : `${item.adm1}·${item.adm2}·${item.name}`
  settings[prefix+'Lat'] = Number(item.latitude ?? item.lat); settings[prefix+'Lon'] = Number(item.longitude ?? item.lon)
  locations[prefix].items = []; locations[prefix].query = ''
}
function edit(item) {
  Object.assign(form, { id: item?.id || '', commuteDate: item?.commuteDate?.slice(0,10) || today(), direction: item?.direction || 'OUTBOUND', departureTime: timePart(item?.departureTime), arrivalTime: timePart(item?.arrivalTime), transport: item?.transport || '地铁', remark: item?.remark || '', includeStats: item ? Boolean(item.includeStats) : true, actualRain:item ? (item.actualRain || 'UNKNOWN') : 'DRY' })
  editor.value = true
}
async function saveRecord() {
  busy.value = true
  try { await post('/commute/records/save', { ...form, ...recordTimes(form.commuteDate, form.departureTime, form.arrivalTime) }); editor.value = false; showSuccessToast('已保存'); await refresh() }
  catch(e) { showFailToast(e.message) } finally { busy.value = false }
}
async function remove(item) {
  try { await showConfirmDialog({ title: '删除这条通勤记录？' }); await post('/commute/records/delete', { id: item.id }); await refresh() } catch(e) { if(e?.message) showFailToast(e.message) }
}
async function pause() { try { await post('/commute/pause-today'); showSuccessToast('本次未发送的通知已暂停'); await refresh() } catch(e) { showFailToast(e.message) } }
onMounted(async () => {
  try { const r = await post('/commute/settings'); if (r.data) Object.assign(settings, r.data, { siteEnabled:Boolean(r.data.siteEnabled),emailEnabled: Boolean(r.data.emailEnabled),pushEnabled:Boolean(r.data.pushEnabled) }) } catch(e) { error.value = e.message }
  await refresh()
})
</script>

<template>
  <section class="commute-page">
    <header class="commute-heading"><button aria-label="返回" @click="router.push('/profile')"><van-icon name="arrow-left" /></button><div><small>我的日常</small><h1>通勤记录</h1></div><van-icon name="shield-o" title="仅本人可见" /></header>
    <div class="commute-tabs"><button :class="{active:tab==='records'}" @click="tab='records'">记录</button><button :class="{active:tab==='settings'}" @click="tab='settings'">设置</button></div>
    <p v-if="error" role="alert" class="commute-error">{{ error }} <button @click="refresh">重试</button></p>
    <template v-if="tab==='records'">
      <div v-if="recommendation?.configured" class="commute-advice">
        <small>{{ recommendation.date }} · {{ recommendation.workDay ? '出门建议' : '非设定上班日 · 参考建议' }}</small>
        <template v-if="recommendation.dry && recommendation.rain"><p>不下雨：<strong>{{ recommendation.dry.departure.replace('T',' ') }} 前出门</strong> · {{ recommendation.dry.source === 'FALLBACK' ? '记录不足，按预计时长' : `${recommendation.dry.sampleCount} 条记录` }}</p><p>下雨：<strong>{{ recommendation.rain.departure.replace('T',' ') }} 前出门</strong> · {{ recommendation.rain.source === 'FALLBACK' ? '记录不足，按预计时长' : `${recommendation.rain.sampleCount} 条记录` }}</p><small>已含预留 {{ recommendation.bufferMinutes }} 分钟</small></template>
        <template v-else><h2>{{ recommendation.departure.replace('T',' ') }} 前出门</h2><p>预计 {{ recommendation.minutes }} 分钟 · 预留 {{ recommendation.bufferMinutes }} 分钟</p><small>{{ sourceNames[recommendation.source] }} · {{ recommendation.sampleCount }} 条记录</small></template>
        <p>{{ recommendation.weatherSummary }}</p>
        <p v-for="state in recommendation.channelStatuses" :key="state.channel">{{ channelNames[state.channel] }}：{{ state.channel==='all'?'本次已暂停':deliveryNames[state.status] }}{{ state.message ? ` · ${state.message}`:'' }}</p>
        <p><a href="https://developer.qweather.com/attribution.html" target="_blank" rel="noopener">天气来源：和风天气</a> · 预报仅供参考</p>
        <button class="commute-link" @click="pause">暂停本次通知</button>
      </div>
      <div v-else class="commute-empty"><p>还没有通勤设置</p><button class="commute-primary" @click="tab='settings'">设置通勤</button></div>
      <div class="commute-section-title"><h2>我的记录</h2><button class="commute-primary" @click="edit(null)"><van-icon name="plus" /> 记录</button></div>
      <p v-if="!records.length && !error" class="commute-empty">暂无通勤记录</p>
      <article v-for="item in records" :key="item.id" class="commute-record">
        <div class="commute-row"><strong>{{ item.commuteDate?.slice(0,10) }} · {{ item.direction==='OUTBOUND'?'上班':'下班' }}</strong><b>{{ item.durationMinutes ? `${item.durationMinutes} 分钟` : '待填写' }}</b></div>
        <p v-if="item.departureTime">{{ item.departureTime.replace('T',' ') }} → {{ item.arrivalTime?.replace('T',' ') }}</p>
        <p v-if="item.durationMinutes">实际天气：{{ item.actualRain === 'RAIN' ? '下雨' : item.actualRain === 'DRY' ? '不下雨' : '不确定' }}</p><p v-if="item.weatherSummary">天气预报：{{ item.weatherSummary }}</p><p v-if="item.remark">{{ item.remark }}</p>
        <div class="commute-row"><small>{{ item.transport }}{{ !item.includeStats?' · 不参与统计':'' }}</small><div><button aria-label="编辑记录" title="编辑记录" @click="edit(item)"><van-icon name="edit" /></button><button aria-label="删除记录" title="删除记录" @click="remove(item)"><van-icon name="delete-o" /></button></div></div>
      </article>
      <button v-if="more" class="commute-link" @click="page++;load(false)">加载更多</button>
    </template>
    <form v-else class="commute-form" @submit.prevent="saveSettings">
      <label>期望到达时间<input v-model="settings.arrivalTime" type="time" required /></label>
      <div class="commute-grid"><label>提醒日期<select v-model.number="settings.noticeDay"><option :value="0">当天</option><option :value="1">头天</option></select></label><label>提醒时间<input v-model="settings.noticeTime" type="time" required /></label></div>
      <div class="commute-grid"><label>预留分钟<input v-model.number="settings.bufferMinutes" type="number" min="0" max="180" required /></label><label>初始预计分钟<input v-model.number="settings.fallbackMinutes" type="number" min="1" max="360" required /></label></div>
      <label>上班日<select v-model="settings.workDays"><option value="1,2,3,4,5">周一至周五</option><option value="1,2,3,4,5,6">周一至周六</option><option value="1,2,3,4,5,6,7">每天</option></select></label>
      <fieldset v-for="prefix in ['origin','destination']" :key="prefix"><legend>{{ prefix==='origin'?'出发地点':'上班地点' }}</legend>
        <select v-model="locations[prefix].mode"><option value="city">区县名称</option><option value="share">高德分享链接</option></select>
        <div class="commute-row"><input v-model="locations[prefix].query" :placeholder="locations[prefix].mode==='city'?'例如：双流':'粘贴高德地点分享内容'" maxlength="2000" /><button type="button" :disabled="locations[prefix].loading" @click="search(prefix)">{{ locations[prefix].loading?'查询中':'查询' }}</button></div>
        <button v-for="item in locations[prefix].items" :key="item.id" type="button" class="commute-city" @click="choose(prefix,item)">{{ item.adm1 }} · {{ item.adm2 }} · {{ item.name }}</button>
        <p v-if="settings[prefix+'Name']"><van-icon name="location-o" /> {{ settings[prefix+'Name'] }}</p>
      </fieldset>
      <div class="commute-row"><label class="commute-check"><input v-model="settings.siteEnabled" type="checkbox" /> 站内通知</label><label class="commute-check"><input v-model="settings.emailEnabled" type="checkbox" /> 邮箱通知</label><label class="commute-check"><input v-model="settings.pushEnabled" type="checkbox" /> App</label></div>
      <button class="commute-primary" :disabled="busy">{{ busy?'保存中':'保存设置' }}</button>
    </form>
    <van-popup v-model:show="editor" position="bottom" round :style="{ maxHeight:'88dvh', overflowY:'auto' }">
      <form class="commute-form commute-editor" @submit.prevent="saveRecord"><div class="commute-row"><h2>记录通勤</h2><button type="button" aria-label="关闭" @click="editor=false"><van-icon name="cross" /></button></div>
        <label>日期<input v-model="form.commuteDate" type="date" :max="today()" required /></label><label>方向<select v-model="form.direction"><option value="OUTBOUND">上班</option><option value="RETURN">下班</option></select></label>
        <label>出发时间<input v-model="form.departureTime" type="time" required /></label><label>到达时间<input v-model="form.arrivalTime" type="time" required /></label>
        <label>交通方式<select v-model="form.transport"><option v-for="t in ['地铁','公交','自驾','骑行','步行','其他']" :key="t">{{ t }}</option></select></label><label>通勤途中是否下雨<select v-model="form.actualRain"><option value="DRY">没下雨</option><option value="RAIN">下雨了</option><option value="UNKNOWN">不确定</option></select></label><label>备注<textarea v-model="form.remark" maxlength="500" /></label>
        <label class="commute-check"><input v-model="form.includeStats" type="checkbox" /> 参与通勤推荐统计</label><button class="commute-primary" :disabled="busy">{{ busy?'保存中':'保存记录' }}</button>
      </form>
    </van-popup>
  </section>
</template>

<style scoped>
.commute-page{padding-bottom:120px!important}
.commute-page{padding:16px 12px 32px;color:#352d28}.commute-heading,.commute-row,.commute-section-title{display:flex;align-items:center;justify-content:space-between;gap:10px}.commute-heading{justify-content:flex-start}.commute-heading>div{flex:1}.commute-heading small{color:#f47716}.commute-heading h1{font-size:22px;margin:2px 0 14px}.commute-page button{cursor:pointer;border:1px solid #f7dfcc;background:#fff8ef;color:#81563a;padding:9px 12px;border-radius:8px;flex-shrink:0}.commute-tabs{display:flex;border-bottom:1px solid #f0ddcc;margin-bottom:16px;gap:8px}.commute-tabs button{border:0;border-bottom:2px solid transparent;border-radius:0;background:none}.commute-tabs .active{color:#f77710;border-bottom-color:#f77710}.commute-advice{padding:16px 0;border-bottom:1px solid #f0ddcc}.commute-advice h2{font-size:20px;line-height:1.5;margin:8px 0}.commute-page p{line-height:1.65;font-size:13px;overflow-wrap:anywhere}.commute-page small{font-size:12px;color:#8b7868}.commute-advice a{color:#9b7050;font-size:11px}.commute-section-title{margin:22px 0 12px}.commute-section-title h2,.commute-editor h2{font-size:18px}.commute-page .commute-primary{background:#ff7912;color:white;border-color:#ff7912}.commute-record{background:white;border:1px solid #f4ddc9;border-radius:8px;padding:14px;margin:12px 0}.commute-record strong,.commute-record b{font-size:13px}.commute-record p{font-size:12px;color:#8b7868}.commute-record .commute-row{flex-wrap:wrap}.commute-record button{margin-left:8px}.commute-form{display:grid;gap:16px}.commute-form label{display:grid;gap:7px;font-size:13px;color:#735945}.commute-form input,.commute-form select,.commute-form textarea{box-sizing:border-box;width:100%;min-width:0;max-width:100%;background:white;border:1px solid #f1dcc9;border-radius:8px;padding:10px;color:#352d28;font:inherit}.commute-grid{display:grid;grid-template-columns:1fr 1fr;gap:12px}.commute-form fieldset{border:0;border-top:1px solid #efdfcf;padding:14px 0;min-width:0;display:grid;gap:10px}.commute-form legend{padding:0 8px 0 0;font-weight:600;font-size:14px}.commute-row input{flex:1;width:0}.commute-form .commute-check{display:flex;align-items:center}.commute-check input{width:18px;height:18px}.commute-editor{padding:20px 16px max(24px,env(safe-area-inset-bottom))}.commute-city{text-align:left}.commute-empty{text-align:center;padding:24px 8px;color:#9c8875}.commute-error{color:#b42338}.commute-page .commute-link{background:none;border:0;font-size:12px;text-decoration:underline}
</style>
