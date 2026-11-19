<script setup>
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showFailToast, showSuccessToast } from 'vant'
import car from '../assets/vehicle/car-v2.png'
import motorcycle from '../assets/vehicle/motorcycle.png'
import VehicleExpenseTypes from '../components/VehicleExpenseTypes.vue'
import VehicleRecords from '../components/VehicleRecords.vue'
import VehicleTripReport from '../components/VehicleTripReport.vue'
import { uploadImage } from '../api/file'
import { listMembers } from '../api/user'
import { useUserStore } from '../stores/user'
import { VEHICLE_RANGES, readingPoints, chartGeometry, nearestPlotIndex, ringSliceAt } from '../utils/vehicleCharts'
import { fuelOverview, spendOverview, expenseSummary } from '../utils/vehicleOverview'
import { fuelTripReport } from '../utils/vehicleRecords'
import { transcodeImageToWebp } from '../utils/image'
import { getImageUrl } from '../utils/imageUrl'
import { listVehicles, saveVehicle, deleteVehicle, listFuel, saveFuel, deleteFuel, listVehicleExpenses, saveVehicleExpense, deleteVehicleExpense, listVehicleExpenseTypes, vehicleSummary, importVehicleFile } from '../api/vehicle'

const router = useRouter(), user = useUserStore(), members = ref([])
const vehicles = ref([]), currentId = ref(''), fuel = ref([]), expenses = ref([]), stats = ref({})
const tripReport = ref(null)
const busy = ref(false), error = ref(''), tab = ref('fuel'), editing = ref(''), paidEdited = ref(false), chooseRecord = ref(false), form = reactive({})
const selected = computed(() => vehicles.value.find(x => x.id === currentId.value))
const vehiclePickerOpen = ref(false)
let vehiclePickerTrigger = null
function openVehiclePicker(trigger) {
  vehiclePickerTrigger = trigger
  vehiclePickerOpen.value = true
}
function restorePickerFocus() {
  if (vehiclePickerTrigger?.isConnected) vehiclePickerTrigger.focus({ preventScroll: true })
  vehiclePickerTrigger = null
}
const photoFile = ref(null), photoPreview = ref('')
const defaultPhoto = vehicle => vehicle.vehicleType === 'CAR' ? car : motorcycle
const photoFor = vehicle => getImageUrl(vehicle.imagePath, defaultPhoto(vehicle))
const draftPhoto = computed(() => photoPreview.value || getImageUrl(form.imagePath))
function restoreDefaultPhoto(event, vehicle) {
  const img = event.currentTarget
  const fallback = new URL(defaultPhoto(vehicle), window.location.href).href
  if (img.src !== fallback) img.src = fallback
}
const now = () => { const d = new Date(); d.setMinutes(d.getMinutes() - d.getTimezoneOffset()); return d.toISOString().slice(0, 16) }
const formatted = v => v == null ? '暂无' : Number(v).toFixed(2)
const trendRange = ref('6m'), fuelRange = ref('rolling')
const overview = computed(() => fuelOverview(fuel.value, new Date(), fuelRange.value))
const spendRange = ref('year'), compositionRange = ref('rolling')
const expenseTypes = ref([]), typesOpen = ref(false), typesLoading = ref(false), typesError = ref('')
const activeTypes = computed(() => expenseTypes.value.filter(type => type.enabled))
const selections = reactive({ trend: -1, fuel: '', spend: '', composition: '' })
const spending = computed(() => spendOverview(fuel.value, expenses.value, spendRange.value, new Date(), expenseTypes.value))
const composition = computed(() => spendOverview(fuel.value, expenses.value, compositionRange.value, new Date(), expenseTypes.value))
const spendTotals = computed(() => expenseSummary(overview.value, expenses.value, selected.value?.ownedSince))
const donut = computed(() => 'conic-gradient(' + composition.value.slices.map(s => `${selections.composition && selections.composition !== s.name ? '#dbe7df' : s.color} ${s.start}% ${s.end}%`).join(', ') + ')')
const fuelDetail = computed(() => overview.value.months.find(month => month.key === selections.fuel))
const spendDetail = computed(() => spending.value.bars.find(month => month.key === selections.spend))
const compositionDetail = computed(() => composition.value.categories.find(type => type.name === selections.composition))
const expenseMetrics = computed(() => [
  ['总支出', spendTotals.value.total], ['油费总计', overview.value.total], ['费用总计', spendTotals.value.extra],
  ['支出/公里', spendTotals.value.perKm], ['油费/公里', overview.value.costPerKm], ['成本/天', spendTotals.value.perDay],
])
const chartPoints = computed(() => chartGeometry(readingPoints(stats.value.trend, trendRange.value)))
const trendDetail = computed(() => chartPoints.value[selections.trend])
const chartLabels = computed(() => {
  const points = chartPoints.value
  return [...new Set(Array.from({ length: Math.min(6, points.length) }, (_, i) =>
    points.length === 1 ? 0 : Math.round(i * (points.length - 1) / (Math.min(6, points.length) - 1))))].map(i => points[i])
})
const chartLine = computed(() => chartPoints.value.map(point => `${point.x},${point.y}`).join(' '))
const chartArea = computed(() => {
  const points = chartPoints.value
  return points.length > 1 ? `${points[0].x},110 ${chartLine.value} ${points.at(-1).x},110` : ''
})
const chartMeanY = computed(() => chartPoints.value.reduce((sum, point) => sum + point.y, 0) / chartPoints.value.length)
const metrics = computed(() => {
  const data = overview.value
  return [
    ['平均油耗', stats.value.average, '升/百公里'],
    ['平均行程', data.dailyMileage, '公里/天'],
    ['平均油费', data.costPerKm, '元/公里'],
    ['统计里程', data.distance, '公里'],
    ['累计油费', data.total, '元'],
    ['累计优惠', data.discount, '元'],
  ]
})
const latest = computed(() => stats.value.latest ?? stats.value.trend?.at(-1)?.value)
const recordKind = computed(() => tab.value === 'expenseRecords' ? 'expense' : 'fuel')
const recordsActive = computed(() => Boolean(selected.value && (tab.value === 'records' || tab.value === 'expenseRecords')))
watch(tab, () => window.scrollTo(0, 0))
watch([currentId, tab], () => Object.assign(selections, { trend: -1, fuel: '', spend: '', composition: '' }))
watch(trendRange, () => { selections.trend = -1 })
watch(fuelRange, () => { selections.fuel = '' })
watch(spendRange, () => { selections.spend = '' })
watch(compositionRange, () => { selections.composition = '' })
function toggleChart(key, value) {
  selections[key] = selections[key] === value ? (key === 'trend' ? -1 : '') : value
}
function selectTrend(event) {
  const box = event.currentTarget.getBoundingClientRect()
  toggleChart('trend', nearestPlotIndex((event.clientX - box.left) / box.width * 300, 20, 280, chartPoints.value.length))
}
function selectTrendKey(event) {
  const count = chartPoints.value.length
  if (event.key === 'Escape') selections.trend = -1
  else if (['Enter', ' ', 'ArrowLeft', 'ArrowRight'].includes(event.key)) {
    event.preventDefault()
    if (event.key === 'Enter' || event.key === ' ') toggleChart('trend', selections.trend < 0 ? 0 : selections.trend)
    else selections.trend = Math.max(0, Math.min(count - 1, selections.trend + (event.key === 'ArrowLeft' ? -1 : 1)))
  }
}
function selectSlice(event) {
  const box = event.currentTarget.getBoundingClientRect()
  const slice = ringSliceAt(event.clientX - box.left, event.clientY - box.top, box.width, box.height, composition.value.slices)
  if (slice) toggleChart('composition', slice.name)
  else selections.composition = ''
}
async function loadTypes() {
  if (typesLoading.value) return
  typesLoading.value = true; typesError.value = ''
  try { updateTypes((await listVehicleExpenseTypes()).data || []) }
  catch (e) { typesError.value = e.message || '费用类型加载失败' }
  finally { typesLoading.value = false }
}
function updateTypes(types) {
  expenseTypes.value = types
  if (editing.value !== 'expense' || !form.categoryKey) return
  const chosen = activeTypes.value.find(type => type.key === form.categoryKey)
  const original = activeTypes.value.find(type => type.name === form.legacyCategory)
  changeExpenseType(chosen?.key || original?.key || (form.legacyCategory ? '__legacy' : ''))
}
function changeExpenseType(key) {
  form.categoryKey = key
  form.category = activeTypes.value.find(type => type.key === key)?.name || form.legacyCategory || ''
}
const previousFuel = computed(() => fuel.value.find(row => row.id !== form.id && String(row.occurredAt) <= String(form.occurredAt || '')))
const discount = computed({ get: () => form.displayedAmount !== '' && form.paidAmount !== '' && form.displayedAmount != null && form.paidAmount != null ? (Number(form.displayedAmount) - Number(form.paidAmount)).toFixed(2) : '', set: value => { if (value !== '' && Number.isFinite(Number(value)) && form.displayedAmount !== '' && form.displayedAmount != null) { paidEdited.value = true; form.paidAmount = (Number(form.displayedAmount) - Number(value)).toFixed(2) } } })
const actualUnit = computed({ get: () => Number(form.liters) > 0 && form.paidAmount !== '' && form.paidAmount != null ? (Number(form.paidAmount) / Number(form.liters)).toFixed(2) : '', set: value => { if (value !== '' && Number.isFinite(Number(value)) && Number(form.liters) > 0) { paidEdited.value = true; form.paidAmount = (Number(value) * Number(form.liters)).toFixed(2) } } })
const importOpen = ref(false), importFile = ref(null), importSheet = ref(''), importVehicle = ref(''), importData = ref(null), sheets = ref([]), importBusy = ref(false)
const importErrors = computed(() => (importData.value?.rows || []).filter(x => x.error).length)
const importDuplicates = computed(() => (importData.value?.rows || []).filter(x => x.duplicate).length)
async function selectFile(e) {
  importFile.value = e.target.files?.[0] || null; importData.value = null; importSheet.value = ''; sheets.value = []
  if (!importFile.value) return
  importBusy.value = true
  try { sheets.value = (await importVehicleFile(importFile.value)).data.sheets || []; importVehicle.value = currentId.value }
  catch (err) { showFailToast(err.message || '无法读取表格') } finally { importBusy.value = false }
}
async function previewImport() {
  if (!importFile.value || !importSheet.value || !importVehicle.value) return showFailToast('请选择文件、工作表和车辆')
  importBusy.value = true; importData.value = null
  try { importData.value = (await importVehicleFile(importFile.value, importSheet.value, importVehicle.value)).data }
  catch (err) { showFailToast(err.message || '预览失败') } finally { importBusy.value = false }
}
async function confirmImport() {
  if (!importData.value || importErrors.value || importBusy.value) return
  try {
    await showConfirmDialog({ title: '确认导入', message: `将导入 ${importData.value.rows.length - importDuplicates.value} 条记录，跳过 ${importDuplicates.value} 条重复记录，不会自动记入家庭账本。` })
    importBusy.value = true
    const result = await importVehicleFile(importFile.value, importSheet.value, importVehicle.value, importData.value.digest)
    showSuccessToast(`已导入 ${result.data.imported} 条`); importData.value = null; importOpen.value = false; currentId.value = importVehicle.value; await loadVehicles()
  } catch (err) { if (err?.message) showFailToast(err.message) } finally { importBusy.value = false }
}
let version = 0
async function loadVehicles() {
  try { vehicles.value = (await listVehicles()).data || []; if (!vehicles.value.some(x => x.id === currentId.value)) currentId.value = vehicles.value[0]?.id || ''; await load() }
  catch (e) { error.value = e.message || '加载失败' }
}
async function load() {
  const id = currentId.value, seq = ++version
  if (!id) { fuel.value = []; expenses.value = []; stats.value = {}; return }
  busy.value = true; error.value = ''
  try {
    const [a, b, c] = await Promise.all([listFuel(id), listVehicleExpenses(id), vehicleSummary(id)])
    if (seq !== version) return
    fuel.value = a.data || []; expenses.value = b.data || []; stats.value = c.data || {}
  } catch (e) { if (seq === version) error.value = e.message || '加载失败' }
  finally { if (seq === version) busy.value = false }
}
function switchVehicle(id) {
  vehiclePickerOpen.value = false
  if (id === currentId.value) return
  currentId.value = id; fuel.value = []; expenses.value = []; stats.value = {}; load()
}
function manageVehiclesFromPicker() {
  vehiclePickerOpen.value = false
  tab.value = 'vehicles'
}
function startRecord(kind) { chooseRecord.value = false; if (!selected.value) { showFailToast('请先添加车辆'); return } open(kind) }
function open(kind, row) {
  paidEdited.value = Boolean(row)
  for (const key of Object.keys(form)) delete form[key]
  if (kind === 'vehicle') Object.assign(form, row || { name: '', vehicleType: 'CAR', model: '' })
  if (kind === 'fuel') Object.assign(form, row || { vehicleId: currentId.value, occurredAt: now(), mileage: '', unitPrice: '', liters: '', displayedAmount: '', paidAmount: '', fullTank: true, warningLight: false, missedPrevious: false, fuelGrade: '', station: '', remark: '' })
  if (kind === 'expense') {
    Object.assign(form, row || { vehicleId: currentId.value, occurredAt: now(), category: '', amount: '', remark: '' })
    const type = activeTypes.value.find(item => item.name === form.category)
    form.categoryKey = type?.key || (row ? '__legacy' : '')
    form.legacyCategory = row?.category || ''
  }
  if (kind !== 'vehicle' && !row) { form.includeLedger = false; form.payerId = user.userId }
  if (form.occurredAt) form.occurredAt = String(form.occurredAt).slice(0, 16)
  editing.value = kind
}
function setPhotoFile(file) {
  if (photoPreview.value) URL.revokeObjectURL(photoPreview.value)
  photoFile.value = file
  photoPreview.value = file ? URL.createObjectURL(file) : ''
}
function selectPhoto(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  if (!/^image\/(jpeg|png|webp)$/i.test(file.type)) return showFailToast('请选择 JPG、PNG 或 WebP 图片')
  setPhotoFile(file)
}
function removePhoto() {
  setPhotoFile(null)
  form.imagePath = ''
}
watch(editing, value => { if (!value) setPhotoFile(null) })
onUnmounted(() => setPhotoFile(null))
function updateAmounts() {
  if (form.unitPrice !== '' && form.liters !== '') {
    form.displayedAmount = (Number(form.unitPrice) * Number(form.liters)).toFixed(2)
    if (!paidEdited.value) form.paidAmount = form.displayedAmount
  }
}
function openTripReport(id, savedRow) {
  const rows = savedRow && !fuel.value.some(row => row.id === id) ? [savedRow, ...fuel.value] : fuel.value
  tripReport.value = fuelTripReport(rows, stats.value, id)
  return Boolean(tripReport.value)
}
async function submit() {
  if (busy.value) return
  if (editing.value === 'expense' && !form.categoryKey) return showFailToast('请选择支出类型')
  busy.value = true
  try {
    const data = { ...form }
    let newFuel
    if (editing.value === 'vehicle') {
      if (photoFile.value) {
        const file = await transcodeImageToWebp(photoFile.value, { maxBytes: 1024 * 1024 })
        const { data: imagePath } = await uploadImage(file, 'vehicle')
        form.imagePath = data.imagePath = imagePath
        setPhotoFile(null)
      }
      const res = await saveVehicle(data); currentId.value = res.data.id
    } else {
      data.occurredAt = data.occurredAt?.length === 16 ? `${data.occurredAt}:00` : data.occurredAt
      for (const key of ['unitPrice', 'displayedAmount']) if (data[key] === '') data[key] = null
      if (editing.value === 'fuel') {
        const { data: saved } = await saveFuel(data)
        if (!data.id && saved?.id) newFuel = { ...data, ...saved }
        if (tab.value !== 'records') tab.value = 'fuel'
      }
      else {
        if (data.categoryKey === '__legacy') data.categoryKey = null
        delete data.legacyCategory
        await saveVehicleExpense(data); tab.value = 'expenseRecords'
      }
    }
    editing.value = ''
    await loadVehicles()
    if (!(newFuel?.id && newFuel.vehicleId === currentId.value && openTripReport(newFuel.id, newFuel))) showSuccessToast('已保存')
  } catch (e) { showFailToast(e.message || '保存失败') } finally { busy.value = false }
}
async function remove(kind, row) {
  try { await showConfirmDialog({ title: '删除记录', message: '删除后无法恢复，已生成的家庭账单会保留。确定继续吗？' }); await (kind === 'fuel' ? deleteFuel(row.id) : deleteVehicleExpense(row.id)); showSuccessToast('已删除'); await load() }
  catch (e) { if (e?.message) showFailToast(e.message) }
}
async function removeVehicle() {
  try { await showConfirmDialog({ title: '删除车辆', message: '只有没有加油和费用记录的车辆才能删除，确定继续吗？' }); await deleteVehicle(form.id); editing.value = ''; await loadVehicles(); showSuccessToast('已删除') }
  catch (e) { if (e?.message) showFailToast(e.message) }
}
onMounted(async () => { loadTypes(); try { members.value = (await listMembers()).data || [] } catch { members.value = [] }; await loadVehicles() })
</script>

<template>
  <div class="vehicles" :class="{ 'expense-theme': tab === 'expense', 'records-theme': recordsActive }">
    <header v-if="!recordsActive" class="page-head">
      <button class="icon-button" aria-label="返回生活助手" title="返回生活助手" @click="router.push('/profile')"><van-icon name="arrow-left" /></button>
      <h1>车辆管理</h1>
      <button v-if="tab !== 'vehicles'" class="icon-button" :aria-label="tab.startsWith('expense') ? '费用明细' : '加油明细'" :title="tab.startsWith('expense') ? '费用明细' : '加油明细'" @click="tab = tab.startsWith('expense') ? 'expenseRecords' : 'records'"><van-icon name="orders-o" /></button><span v-else class="header-spacer"></span>
    </header>
    <div class="vehicle-current" v-if="selected && !recordsActive">
      <button type="button" class="vehicle-picker" aria-label="切换车辆" aria-haspopup="dialog" :aria-expanded="vehiclePickerOpen" @click="openVehiclePicker($event.currentTarget)">
        <span>{{ selected.name }}</span>
        <van-icon name="arrow-down" />
      </button>
      <p>{{ selected.model || (selected.vehicleType === 'CAR' ? '燃油汽车' : '燃油摩托车') }}</p>
    </div>
    <div v-if="error" class="error" role="alert">{{ error }} <button @click="loadVehicles">重试</button></div>
    <div v-if="!selected && !error" class="empty"><van-icon name="logistics" /><p>还没有车辆</p><button class="primary" @click="open('vehicle')">添加车辆</button></div>
    <template v-if="selected && tab === 'fuel'">
      <button class="latest-fuel" @click="tab = 'records'">
        <span class="latest-title">最近油耗 <van-icon name="arrow" /></span>
        <span class="latest-value"><strong>{{ busy ? '—' : latest == null ? '暂无' : formatted(latest) }}</strong><span>升/百公里</span></span>
      </button>
      <section class="dashboard-section" aria-label="车辆统计">
        <div class="section-head"><h2>统计</h2><span class="hint">全部记录</span></div>
        <div class="metrics"><div v-for="[label, value, unit] in metrics" :key="label"><small>{{ label }}</small><strong>{{ value == null ? '—' : formatted(value) }}</strong><span>{{ unit }}</span></div></div>
      </section>
      <section class="dashboard-section trend">
        <div class="section-head"><h2>油耗变化趋势</h2><select v-model="trendRange" aria-label="油耗趋势范围"><option v-for="range in VEHICLE_RANGES" :key="range.value" :value="range.value">{{ range.label }}</option></select></div>
        <p class="hint">升/百公里</p>
        <div v-if="chartPoints.length" class="chart">
          <svg viewBox="0 0 300 124" preserveAspectRatio="none" role="button" tabindex="0" aria-label="查看油耗数据点" @click="selectTrend" @keydown="selectTrendKey">
            <path d="M20 30H280 M20 55H280 M20 80H280 M20 110H280" stroke="#cadbd3" stroke-dasharray="3 3" fill="none" />
            <polygon v-if="chartArea" :points="chartArea" fill="#cff0dd" />
            <line x1="20" x2="280" :y1="chartMeanY" :y2="chartMeanY" stroke="#cc6a75" stroke-dasharray="3 3" />
            <polyline :points="chartLine" fill="none" stroke="#159e66" stroke-width="2.5" vector-effect="non-scaling-stroke" />
            <circle v-for="point in chartPoints" :key="point.key" :cx="point.x" :cy="point.y" r="3" fill="#fff" stroke="#159e66" stroke-width="2" />
            <template v-if="trendDetail"><line :x1="trendDetail.x" :x2="trendDetail.x" y1="8" y2="110" stroke="#087f4b" stroke-dasharray="3 3" /><circle :cx="trendDetail.x" :cy="trendDetail.y" r="5" fill="#087f4b" stroke="#fff" stroke-width="2" /></template>
          </svg>
          <div class="chart-labels"><span v-for="point in chartLabels" :key="point.key">{{ point.day.slice(0, 7) }}</span></div>
          <div v-if="trendDetail" class="chart-note" role="status"><b>{{ trendDetail.day }}</b><span>{{ formatted(trendDetail.value) }} 升/百公里</span></div>
        </div>
        <p v-else class="hint chart-empty">{{ busy ? '加载中…' : '该时段暂无可计算的油耗' }}</p>
      </section>
      <section class="dashboard-section">
        <div class="section-head"><h2>油费月度统计</h2><select v-model="fuelRange" aria-label="油费统计范围"><option v-for="range in VEHICLE_RANGES" :key="range.value" :value="range.value">{{ range.label }}</option></select></div>
        <div v-if="overview.months.some(month => month.amount > 0)" class="chart">
          <div class="plot-scroll"><div class="monthly-bars" :style="{ '--columns': overview.months.length }">
            <button v-for="(month, index) in overview.months" :key="month.key" class="month-bar" type="button" :aria-label="`${month.key} 油费 ¥${formatted(month.amount)}`" :aria-pressed="selections.fuel === month.key" @click="toggleChart('fuel', month.key)"><span class="bar-track"><i :style="{ height: month.height + '%' }"></i></span><small>{{ index % 2 === 0 || index === overview.months.length - 1 ? month.label : '' }}</small></button>
          </div></div>
          <div v-if="fuelDetail" class="chart-note" role="status"><b>{{ fuelDetail.key }}</b><span>油费 ¥{{ formatted(fuelDetail.amount) }}</span></div>
        </div>
        <p v-else class="hint chart-empty">该时段暂无油费记录</p>
      </section>
    </template>
    <template v-if="selected && tab === 'expense'">
      <div class="expense-tools"><button type="button" class="settings-link" aria-label="费用类型管理" @click="typesOpen = true"><van-icon name="setting-o" />费用类型管理</button></div>
      <div v-if="spendTotals.ownedDays != null" class="ownership">爱车已相伴 <strong>{{ spendTotals.ownedDays }}</strong> 天</div>
      <section class="dashboard-section">
        <div class="section-head"><h2>统计</h2><span class="hint">全部记录</span></div>
        <div class="metrics expense-metrics"><div v-for="[label, value] in expenseMetrics" :key="label"><small>{{ label }}</small><strong>{{ value == null ? '—' : formatted(value) }}</strong><span>元</span></div></div>
      </section>
      <section class="dashboard-section cost-section">
        <div class="section-head"><h2>支出月度统计</h2><select v-model="spendRange" aria-label="支出统计范围"><option v-for="range in VEHICLE_RANGES" :key="range.value" :value="range.value">{{ range.label }}</option></select></div>
        <div v-if="spending.total" class="legend"><span v-for="item in spending.categories" :key="item.name"><i :style="{background:item.color}"></i>{{ item.name }}</span></div>
        <div v-if="spending.total" class="chart">
          <div class="plot-scroll"><div class="monthly-bars" :style="{ '--columns': spending.bars.length }">
            <button v-for="(month,index) in spending.bars" :key="month.key" class="month-bar" type="button" :aria-label="`${month.key} 支出 ¥${formatted(month.amount)}`" :aria-pressed="selections.spend === month.key" @click="toggleChart('spend', month.key)">
              <span class="bar-track"><span class="stack" :style="{height:month.height+'%'}"><i v-for="segment in month.segments" :key="segment.name" :style="{height:segment.height+'%',background:segment.color}"></i></span></span>
              <small>{{ index % 2 === 0 || index === spending.bars.length - 1 ? month.label : '' }}</small>
            </button>
          </div></div>
          <div v-if="spendDetail" class="chart-note" role="status"><b>{{ spendDetail.key }} · 支出 ¥{{ formatted(spendDetail.amount) }}</b><span v-for="segment in spendDetail.segments" :key="segment.name">{{ segment.name }} ¥{{ formatted(segment.amount) }}</span></div>
        </div>
        <p v-else class="hint chart-empty">该时段暂无支出</p>
      </section>
      <section class="dashboard-section cost-section">
        <div class="section-head"><h2>费用构成统计</h2><select v-model="compositionRange" aria-label="费用构成范围"><option v-for="range in VEHICLE_RANGES" :key="range.value" :value="range.value">{{ range.label }}</option></select></div>
        <template v-if="composition.total">
          <div class="donut" :style="{background:donut}" @click="selectSlice"><div aria-live="polite"><strong>{{ formatted(compositionDetail?.amount ?? composition.total) }}</strong><span v-if="compositionDetail">{{ compositionDetail.name }} · {{ formatted(compositionDetail.amount / composition.total * 100) }}%</span><span v-else>费用总计（元）</span></div></div>
          <div class="legend breakdown"><button v-for="item in composition.categories" :key="item.name" type="button" :aria-pressed="selections.composition === item.name" @click="toggleChart('composition', item.name)"><i :style="{background:item.color}"></i>{{ item.name }} <strong>¥{{ formatted(item.amount) }}</strong></button></div>
        </template>
        <p v-else class="hint chart-empty">该时段暂无费用</p>
      </section>
      <button class="detail-link" @click="tab = 'expenseRecords'">查看费用明细 <van-icon name="arrow" /></button>
    </template>
    <section v-if="tab === 'vehicles' && selected" class="dashboard-section manage-section">
      <div class="section-head"><h2>我的车辆</h2><button class="primary" @click="open('vehicle')"><van-icon name="plus" /> 添加</button></div>
      <article v-for="vehicle in vehicles" :key="vehicle.id" class="record">
        <img class="manage-image" :src="photoFor(vehicle)" alt="" @error="restoreDefaultPhoto($event, vehicle)" />
        <div class="record-copy"><strong>{{ vehicle.name }}</strong><p>{{ vehicle.model || (vehicle.vehicleType === 'CAR' ? '燃油汽车' : '燃油摩托车') }}</p></div>
        <button class="icon-button" :aria-label="'编辑 ' + vehicle.name" @click="open('vehicle', vehicle)"><van-icon name="edit" /></button>
      </article>
      <button class="import-link" @click="importOpen = true">导入小熊油耗表格</button>
    </section>
    <VehicleRecords v-if="selected && recordsActive" :key="recordKind" :vehicle="selected" :kind="recordKind"
      :rows="recordKind === 'fuel' ? fuel : expenses" :stats="stats" :types="expenseTypes" :loading="busy" :picker-open="vehiclePickerOpen"
      @back="tab = recordKind === 'fuel' ? 'fuel' : 'expense'" @choose="openVehiclePicker" @add="open(recordKind)"
      @edit="row => open(recordKind, row)" @remove="row => remove(recordKind, row)" @report="openTripReport" />
    <nav v-if="!recordsActive" class="vehicle-nav" aria-label="用车导航">
      <button :class="{ active: tab === 'fuel' }" aria-label="油耗" :aria-pressed="tab === 'fuel'" @click="tab = 'fuel'"><van-icon name="fire-o" /><span>油耗</span></button>
      <button :class="{ active: tab === 'expense' || tab === 'expenseRecords' }" aria-label="费用" :aria-pressed="tab.startsWith('expense')" @click="tab = 'expense'"><van-icon name="balance-list-o" /><span>费用</span></button>
      <button class="nav-add" @click="chooseRecord = true" aria-label="记一笔"><van-icon name="plus" /><span>记一笔</span></button>
      <button :class="{ active: tab === 'records' }" aria-label="记录" :aria-pressed="tab === 'records'" @click="tab = 'records'"><van-icon name="records-o" /><span>记录</span></button>
      <button :class="{ active: tab === 'vehicles' }" aria-label="车辆" :aria-pressed="tab === 'vehicles'" @click="tab = 'vehicles'"><van-icon name="logistics" /><span>车辆</span></button>
    </nav>
    <van-popup v-model:show="vehiclePickerOpen" position="bottom" class="vehicle-chooser" :overlay-style="{ background: 'rgba(0,0,0,.28)' }" :tabindex="0" aria-modal="true" aria-labelledby="vehicle-chooser-title" aria-describedby="vehicle-chooser-description" @keydown.esc.stop="vehiclePickerOpen = false" @closed="restorePickerFocus">
      <div class="vehicle-chooser-panel">
        <header class="vehicle-chooser-heading">
          <h2 id="vehicle-chooser-title">选择车辆</h2>
          <p id="vehicle-chooser-description">请选择当前需要显示的车辆</p>
        </header>
        <div class="vehicle-chooser-list">
          <button v-for="vehicle in vehicles" :key="vehicle.id" type="button" :aria-pressed="vehicle.id === currentId" @click="switchVehicle(vehicle.id)">{{ vehicle.name }}</button>
        </div>
        <button type="button" class="vehicle-chooser-manage" @click="manageVehiclesFromPicker">车辆管理</button>
      </div>
      <button type="button" class="vehicle-chooser-cancel" @click="vehiclePickerOpen = false">取消</button>
    </van-popup>
    <van-action-sheet v-model:show="chooseRecord" :actions="[{ name: '记加油', value: 'fuel' }, { name: '记费用', value: 'expense' }]" cancel-text="取消" @select="action => startRecord(action.value)" />
    <van-popup v-model:show="importOpen" position="bottom" round class="vehicle-popup"><div class="sheet"><div class="section-head"><h2>导入小熊油耗记录</h2><button class="icon-button" aria-label="关闭" @click="importOpen = false"><van-icon name="cross" /></button></div><label>选择导出的表格<input type="file" accept=".xlsx" @change="selectFile" /></label><template v-if="sheets.length"><label>工作表<select v-model="importSheet" @change="importData = null"><option value="">请选择</option><option v-for="s in sheets" :key="s">{{ s }}</option></select></label><label>导入到车辆<select v-model="importVehicle" @change="importData = null"><option value="">请选择</option><option v-for="v in vehicles" :key="v.id" :value="v.id">{{ v.name }}</option></select></label><button class="primary save" :disabled="importBusy" @click="previewImport">预览记录</button></template><template v-if="importData"><p>共 {{ importData.rows.length }} 条，重复 {{ importDuplicates }} 条，错误 {{ importErrors }} 条</p><div class="preview-list"><div v-for="row in importData.rows" :key="row.line" class="preview-row"><span>第 {{ row.line }} 行</span><span v-if="row.error" class="error">{{ row.error }}</span><span v-else>{{ row.record.occurredAt }} · {{ importData.type === 'fuel' ? `${row.record.liters} 升` : row.record.category }} · ¥{{ importData.type === 'fuel' ? row.record.paidAmount : row.record.amount }}{{ row.duplicate ? ' · 重复' : '' }}</span></div></div><button class="primary save" :disabled="importBusy || importErrors > 0" @click="confirmImport">确认导入</button></template></div></van-popup>
    <div v-if="editing" class="entry-screen"><form class="sheet entry-form" @submit.prevent="submit"><div class="entry-head"><button type="button" class="icon-button" :disabled="busy" @click="editing = ''" aria-label="返回"><van-icon name="arrow-left" /></button><h2>{{ editing === 'vehicle' ? (form.id ? '编辑车辆' : '添加车辆') : editing === 'fuel' ? '记加油' : '记费用' }}</h2><button class="primary" :disabled="busy" type="submit">保存</button></div>
      <template v-if="editing === 'vehicle'">
        <label>车辆名称<input v-model.trim="form.name" maxlength="80" required /></label><label>车辆类型<select v-model="form.vehicleType"><option value="CAR">燃油汽车</option><option value="MOTORCYCLE">燃油摩托车</option></select></label><label>车型<input v-model.trim="form.model" maxlength="120" /></label><label>购车日期<input v-model="form.ownedSince" type="date" /></label>
        <fieldset class="vehicle-photo">
          <legend>车辆图片</legend>
          <div class="vehicle-photo-row">
            <img v-if="draftPhoto" :src="draftPhoto" class="vehicle-photo-preview" alt="车辆图片预览" />
            <label class="vehicle-photo-select" :class="{ disabled: busy }">
              <van-icon name="photograph" />{{ draftPhoto ? '更换图片' : '选择图片' }}
              <input type="file" aria-label="车辆图片" accept="image/jpeg,image/png,image/webp" :disabled="busy" @change="selectPhoto" />
            </label>
            <button v-if="draftPhoto" type="button" class="vehicle-photo-remove" :disabled="busy" @click="removePhoto">移除</button>
          </div>
        </fieldset>
      </template>
      <template v-else>
        <label>{{ editing === 'fuel' ? '加油时间' : '时间' }}<input v-model="form.occurredAt" type="datetime-local" required /></label>
        <template v-if="editing === 'fuel'">
          <label>当前里程（公里）<input v-model="form.mileage" type="number" min="0" step="0.01" required /><small v-if="previousFuel" class="hint">上一条 {{ String(previousFuel.occurredAt).slice(0, 10) }} · {{ previousFuel.mileage }} 公里</small></label>
          <div class="fuel-fields">
            <div class="fuel-formula" role="group" aria-label="机显单价乘以加油量等于机显金额">
              <label>机显单价<span class="fuel-control"><input v-model="form.unitPrice" type="number" inputmode="decimal" min="0" step="0.0001" @input="updateAmounts" /><span class="fuel-unit">元/升</span></span></label>
              <span class="fuel-operator" aria-hidden="true">×</span>
              <label>加油量<span class="fuel-control"><input v-model="form.liters" type="number" inputmode="decimal" min="0.001" step="0.001" required @input="updateAmounts" /><span class="fuel-unit">升</span></span></label>
              <span class="fuel-operator" aria-hidden="true">=</span>
              <label>机显金额<span class="fuel-control"><input v-model="form.displayedAmount" type="number" inputmode="decimal" min="0" step="0.01" /><span class="fuel-unit">元</span></span></label>
            </div>
            <div class="fuel-formula fuel-payment">
              <label>实付单价<span class="fuel-control"><input :value="actualUnit" type="number" inputmode="decimal" min="0" step="0.01" @change="actualUnit = $event.target.value" /><span class="fuel-unit">元/升</span></span></label>
              <label>优惠金额<span class="fuel-control"><input :value="discount" type="number" inputmode="decimal" step="0.01" @change="discount = $event.target.value" /><span class="fuel-unit">元</span></span></label>
              <label>实付金额<span class="fuel-control"><input v-model="form.paidAmount" type="number" inputmode="decimal" min="0" step="0.01" required @input="paidEdited = true" /><span class="fuel-unit">元</span></span></label>
            </div>
          </div>
          <div class="choice-row"><span>是否加满</span><div class="segmented"><button type="button" :class="{active: form.fullTank}" @click="form.fullTank = true">加满</button><button type="button" :class="{active: !form.fullTank}" @click="form.fullTank = false">没加满</button></div></div>
          <div class="choice-row"><span>油量警告灯</span><div class="segmented"><button type="button" :class="{active: form.warningLight}" @click="form.warningLight = true">亮了</button><button type="button" :class="{active: !form.warningLight}" @click="form.warningLight = false">没亮</button></div></div>
          <label>加油站<input v-model="form.station" maxlength="160" /></label><label>燃油标号<input v-model="form.fuelGrade" maxlength="32" /></label>
          <div class="choice-row"><span>上次加油</span><div class="segmented"><button type="button" :class="{active: !form.missedPrevious}" @click="form.missedPrevious = false">记录了</button><button type="button" :class="{active: form.missedPrevious}" @click="form.missedPrevious = true">没记录</button></div></div>
        </template>
        <template v-else>
          <div class="expense-type-field">
            <label>支出类型<select :value="form.categoryKey" required @change="changeExpenseType($event.target.value)">
              <option value="" disabled>{{ typesLoading ? '加载中…' : '请选择支出类型' }}</option>
              <option v-if="form.legacyCategory && !activeTypes.some(type => type.name === form.legacyCategory)" value="__legacy">{{ form.legacyCategory }}（原类型）</option>
              <option v-for="type in activeTypes" :key="type.key" :value="type.key">{{ type.name }}</option>
            </select></label>
            <button type="button" class="settings-link" :disabled="busy" @click="typesOpen = true"><van-icon name="setting-o" />管理</button>
          </div>
          <label>支出金额（元）<input v-model="form.amount" type="number" min="0.01" step="0.01" required /></label>
        </template>
        <label>备注<textarea v-model="form.remark" maxlength="1000" /></label>
      </template>
      <template v-if="editing !== 'vehicle' && !form.id"><label class="check"><input v-model="form.includeLedger" type="checkbox" /> 同时记入家庭账本</label><label v-if="form.includeLedger">付款人<select v-model="form.payerId" required><option v-for="m in members" :key="m.id" :value="m.id">{{ m.nickname || m.username }}</option></select></label></template><p v-if="form.id && editing !== 'vehicle'" class="hint">如已记账，修改记录不会自动修改账单。</p>
      <button v-if="editing === 'vehicle' && form.id" class="import-link" type="button" :disabled="busy" @click="removeVehicle">删除车辆</button></form></div>
    <VehicleExpenseTypes v-if="typesOpen" :types="expenseTypes" :loading="typesLoading" :load-error="typesError" @close="typesOpen = false" @updated="updateTypes" @retry="loadTypes" />
    <VehicleTripReport v-if="tripReport && selected" :report="tripReport" :vehicle="selected"
      :photo="photoFor(selected)" :fallback-photo="defaultPhoto(selected)" @close="tripReport = null" />
  </div>
</template>
<style scoped src="./vehicle.css"></style>
