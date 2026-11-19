<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { expenseRecordList, fuelRecordList, fuelRecordYears } from '../utils/vehicleRecords'

const props = defineProps({
  vehicle: Object, rows: Array, stats: Object, types: Array, loading: Boolean, pickerOpen: Boolean, kind: String,
})
const emit = defineEmits(['back', 'choose', 'add', 'edit', 'remove', 'report'])
const filters = reactive({ query: '', start: '', end: '', category: '' })
const expanded = ref(new Set()), collapsed = ref(new Set()), pickedYear = ref('')
const isFuel = computed(() => props.kind === 'fuel')
const years = computed(() => fuelRecordYears(props.rows))
const year = computed(() => years.value.includes(pickedYear.value) ? pickedYear.value : years.value[0] || String(new Date().getFullYear()))
const yearIndex = computed(() => years.value.indexOf(year.value))
const fuel = computed(() => isFuel.value ? fuelRecordList(props.rows, props.stats, year.value) : null)
const expenses = computed(() => !isFuel.value ? expenseRecordList(props.rows, filters, props.types) : null)
const allExpanded = computed(() => fuel.value?.entries.length > 0 && fuel.value.entries.every(row => expanded.value.has(row.id)))
const hasFilters = computed(() => Object.values(filters).some(Boolean))
watch(() => [props.vehicle.id, props.kind], () => {
  Object.assign(filters, { query: '', start: '', end: '', category: '' })
  pickedYear.value = ''; expanded.value = new Set(); collapsed.value = new Set()
})
watch(year, () => { expanded.value = new Set() })
function toggle(set, id) {
  if (set.has(id)) set.delete(id)
  else set.add(id)
}
function toggleAll() {
  expanded.value = new Set(allExpanded.value ? [] : fuel.value.entries.map(row => row.id))
}
</script>

<template>
  <section class="vehicle-records" :aria-label="isFuel ? '加油记录' : '费用记录'">
    <header class="records-head" :class="{ 'with-search': !isFuel }">
      <button type="button" class="list-icon" :aria-label="isFuel ? '返回油耗' : '返回费用'" @click="emit('back')"><van-icon name="arrow-left" /></button>
      <button v-if="isFuel" type="button" class="list-vehicle" aria-label="切换车辆" aria-haspopup="dialog" :aria-expanded="pickerOpen" @click="emit('choose', $event.currentTarget)">{{ vehicle.name }} <van-icon name="arrow-down" /></button>
      <label v-else class="record-search"><van-icon name="search" /><input v-model="filters.query" type="search" placeholder="搜索类型、备注" aria-label="搜索费用记录" /></label>
      <div class="head-actions">
        <button v-if="isFuel" type="button" class="list-icon is-primary" :aria-label="allExpanded ? '全部收起' : '全部展开'" :disabled="!fuel.entries.length" @click="toggleAll"><van-icon :name="allExpanded ? 'arrow-up' : 'arrow-down'" /></button>
        <button type="button" class="list-icon is-primary" :aria-label="isFuel ? '添加加油记录' : '添加费用记录'" @click="emit('add')"><van-icon name="plus" /></button>
      </div>
    </header>

    <template v-if="isFuel">
      <section class="year-summary" aria-label="加油年度统计">
        <div class="year-top">
          <select :value="year" aria-label="加油记录年份" :disabled="!years.length" @change="pickedYear = $event.target.value">
            <option v-for="option in years.length ? years : [year]" :key="option" :value="option">{{ option }}年</option>
          </select>
          <div class="year-actions">
            <button type="button" class="year-arrow" aria-label="较早的年份" :disabled="yearIndex < 0 || yearIndex >= years.length - 1" @click="pickedYear = years[yearIndex + 1]"><van-icon name="arrow-left" /></button>
            <button type="button" class="year-arrow" aria-label="较新的年份" :disabled="yearIndex <= 0" @click="pickedYear = years[yearIndex - 1]"><van-icon name="arrow" /></button>
          </div>
        </div>
        <div class="year-metrics">
          <div v-for="metric in fuel.summary" :key="metric.title" class="year-metric"><span>{{ metric.title }}</span><strong>{{ metric.value }}</strong><small>{{ metric.unit }}</small></div>
        </div>
      </section>
      <p v-if="!fuel.entries.length" class="no-records">{{ loading ? '加载中…' : '暂无记录' }}</p>
      <ol v-else class="fuel-timeline">
        <li v-for="row in fuel.entries" :key="row.id" class="fuel-event" :class="{ 'fuel-expanded': expanded.has(row.id) }">
          <span class="timeline-symbol" aria-hidden="true"><van-icon name="fire-o" /></span>
          <article class="fuel-card">
            <button type="button" class="fuel-line" :aria-expanded="expanded.has(row.id)" :aria-label="`加油记录 ${row.timestamp}`" @click="toggle(expanded, row.id)">
              <strong class="record-day">{{ row.date }}</strong>
              <span class="record-reading"><strong :class="{ 'above-average': row.aboveAverage }">{{ row.consumption }}</strong><small>升/百公里</small></span>
              <span class="record-mileage"><strong>{{ row.mileage }}</strong><small>公里</small></span>
              <van-icon :name="expanded.has(row.id) ? 'arrow-up' : 'arrow-down'" />
            </button>
            <div v-if="expanded.has(row.id)" class="record-expansion">
              <div class="fuel-values">
                <div><small>实付</small><span><strong>{{ row.total }}</strong> 元</span></div>
                <div><small>单价</small><span><strong>{{ row.unitPrice }}</strong> 元/升</span></div>
                <div><small>加油量</small><span><strong>{{ row.liters }}</strong> 升</span></div>
              </div>
              <div class="fuel-flags"><span v-for="flag in row.flags" :key="flag" :class="{ 'flag-full': flag === '加满', 'flag-warning': flag === '亮灯' || flag === '上次漏记' }">{{ flag }}</span></div>
              <p v-if="row.record.station">{{ row.record.station }}</p>
              <p v-if="row.record.remark">{{ row.record.remark }}</p>
              <div class="detail-foot"><time>{{ row.timestamp }}</time><div class="record-ops">
                <button type="button" aria-label="编辑加油记录" title="编辑" @click="emit('edit', row.record)"><van-icon name="edit" /></button>
                <button v-if="!row.segment" type="button" aria-label="查看行程报告" title="查看行程报告" @click="emit('report', row.id)"><van-icon name="notes-o" /></button>
                <button type="button" aria-label="删除加油记录" title="删除" @click="emit('remove', row.record)"><van-icon name="delete-o" /></button>
              </div></div>
            </div>
          </article>
          <button v-if="expanded.has(row.id) && row.segment" type="button" class="trip-interval"
            :aria-label="'查看行程报告 ' + row.timestamp" @click="emit('report', row.id)">
            <span class="trip-value"><small>每公里油费</small><span><strong>{{ row.segment.cost }}</strong> 元/公里</span></span>
            <span class="trip-value"><small>区间用油</small><span><strong>{{ row.segment.liters }}</strong> 升</span></span>
            <span class="trip-value"><small>较上次行驶</small><span><strong>+{{ row.segment.distance }}</strong> 公里</span></span>
            <van-icon name="arrow" />
          </button>
        </li>
      </ol>
    </template>

    <template v-else>
      <div class="expense-record-filters">
        <div class="expense-dates">
          <label :class="{ 'date-blank': !filters.start }"><input v-model="filters.start" type="date" aria-label="费用起始日期" :max="filters.end || undefined" /><span v-if="!filters.start">起始日期</span></label>
          <span>至</span>
          <label :class="{ 'date-blank': !filters.end }"><input v-model="filters.end" type="date" aria-label="费用结束日期" :min="filters.start || undefined" /><span v-if="!filters.end">结束日期</span></label>
          <button type="button" class="list-vehicle" aria-label="切换车辆" aria-haspopup="dialog" :aria-expanded="pickerOpen" @click="emit('choose', $event.currentTarget)">{{ vehicle.name }} <van-icon name="arrow-down" /></button>
        </div>
        <div class="filter-total">
          <select v-model="filters.category" aria-label="费用记录类型">
            <option value="">全部类型</option><option v-for="type in expenses.categories" :key="type" :value="type">{{ type }}</option>
          </select>
          <button v-if="hasFilters" type="button" class="clear-filters" aria-label="清除费用筛选" title="清除筛选" @click="Object.assign(filters, {query:'',start:'',end:'',category:''})"><van-icon name="replay" /></button>
          <strong class="expense-total">总计：{{ expenses.total }}<small> 元</small></strong>
        </div>
      </div>
      <p v-if="!expenses.groups.length" class="no-records">{{ loading ? '加载中…' : expenses.invalidRange ? '结束日期不能早于起始日期' : hasFilters ? '没有符合筛选条件的费用' : '暂无记录' }}</p>
      <section v-for="group in expenses.groups" :key="group.key" class="expense-month">
        <button type="button" class="month-heading" :aria-expanded="!collapsed.has(group.key)" @click="toggle(collapsed, group.key)">
          <strong>{{ group.label }} <van-icon :name="collapsed.has(group.key) ? 'arrow-down' : 'arrow-up'" /></strong>
          <span>支出：{{ group.total }} 元</span>
        </button>
        <div v-if="!collapsed.has(group.key)" class="month-entries">
          <article v-for="row in group.entries" :key="row.id" class="expense-card">
            <button type="button" class="expense-line" :aria-expanded="expanded.has(row.id)" @click="toggle(expanded, row.id)">
              <i class="expense-dot" :style="{ color:row.color }" aria-hidden="true"></i>
              <span class="expense-copy"><strong><span>{{ row.date }}</span> {{ row.name }}</strong><small v-if="row.remark">{{ row.remark }}</small></span>
              <span class="expense-amount"><strong>{{ row.amount }}</strong><small> 元</small></span>
            </button>
            <div v-if="expanded.has(row.id)" class="detail-foot expense-detail"><time>{{ row.timestamp }}</time><div class="record-ops">
              <button type="button" aria-label="编辑费用记录" title="编辑" @click="emit('edit', row.record)"><van-icon name="edit" /></button>
              <button type="button" aria-label="删除费用记录" title="删除" @click="emit('remove', row.record)"><van-icon name="delete-o" /></button>
            </div></div>
          </article>
        </div>
      </section>
    </template>
  </section>
</template>

<style scoped src="./vehicleRecords.css"></style>
