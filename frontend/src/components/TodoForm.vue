<script setup>
import { computed, reactive, watch } from 'vue'

const props = defineProps({
  modelValue: {
    type: Object,
    default: () => ({}),
  },
  members: {
    type: Array,
    default: () => [],
  },
  saving: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['submit', 'cancel'])

const categories = [
  ['COOK', '做饭'],
  ['GROCERY', '采购'],
  ['LIFE', '生活'],
  ['BIRTHDAY', '生日'],
  ['ANNIVERSARY', '纪念日'],
  ['SERVER', '服务器'],
  ['DOMAIN', '域名'],
  ['SSL', '证书'],
  ['PAYMENT', '缴费'],
  ['OTHER', '其他'],
]

const repeats = [
  ['NONE', '不重复'],
  ['DAILY', '每天'],
  ['WEEKLY', '每周'],
  ['MONTHLY', '每月'],
  ['QUARTERLY', '每三个月'],
  ['HALF_YEARLY', '每半年'],
  ['YEARLY', '每年'],
]

const noticeOptions = [
  [0, '准时'],
  [10, '提前10分钟'],
  [60, '提前1小时'],
  [1440, '提前1天'],
  [4320, '提前3天'],
  [10080, '提前7天'],
  [43200, '提前30天'],
]

const form = reactive({
  id: '',
  title: '',
  category: 'OTHER',
  ownerId: '',
  ownerIds: [],
  dueTime: '',
  dueCalendar: 'SOLAR',
  birthdayDate: '',
  lunarDueMonth: 1,
  lunarDueDay: 1,
  lunarDueLeap: false,
  birthdayYear: '',
  description: '',
  repeatType: 'NONE',
  notifySite: true,
  notifyEmail: false,
  notifyPush: false,
  noticeMinutes: [0],
  relatedType: '',
  relatedId: '',
})

const selectedNotice = computed({
  get() {
    return new Set(form.noticeMinutes)
  },
  set(value) {
    form.noticeMinutes = [...value]
  },
})

watch(
  () => props.modelValue,
  (value) => {
    Object.assign(form, {
      id: value.id || '',
      title: value.title || '',
      category: value.category || 'OTHER',
      ownerId: value.ownerId || '',
      ownerIds: value.ownerIds?.length ? value.ownerIds : (value.ownerId ? [value.ownerId] : []),
      dueTime: toDateTimeInput(value.dueTime),
      dueCalendar: value.dueCalendar || 'SOLAR',
      birthdayDate: birthdayDateInput(value),
      lunarDueMonth: value.lunarDueMonth || 1,
      lunarDueDay: value.lunarDueDay || 1,
      lunarDueLeap: Boolean(value.lunarDueLeap),
      birthdayYear: value.birthdayYear || '',
      description: value.description || '',
      repeatType: value.repeatType || 'NONE',
      notifySite: value.notifySite !== false,
      notifyEmail: Boolean(value.notifyEmail),
      notifyPush: Boolean(value.notifyPush),
      noticeMinutes: value.noticeMinutes?.length ? value.noticeMinutes : defaultNotice(value.category),
      relatedType: value.relatedType || '',
      relatedId: value.relatedId || '',
    })
  },
  { immediate: true, deep: true },
)

watch(
  () => form.category,
  (category) => {
    if (category === 'BIRTHDAY') {
      form.repeatType = 'YEARLY'
      if (!form.noticeMinutes?.length || form.noticeMinutes.length === 1 && form.noticeMinutes[0] === 0) {
        form.noticeMinutes = defaultNotice('BIRTHDAY')
      }
    } else if (form.dueCalendar === 'LUNAR') {
      form.dueCalendar = 'SOLAR'
    }
  },
)

function defaultNotice(category) {
  if (['SERVER', 'DOMAIN', 'SSL'].includes(category)) return [43200, 10080, 4320, 1440]
  if (category === 'BIRTHDAY') return [10080, 4320, 0]
  return [0]
}

function toDateTimeInput(value) {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 16).replace(' ', 'T')
}

function birthdayDateInput(value) {
  if (value.dueCalendar === 'LUNAR') return ''
  const dueDate = value.dueTime ? String(value.dueTime).slice(0, 10) : ''
  if (!dueDate) return ''
  if (value.birthdayYear) {
    return `${value.birthdayYear}-${dueDate.slice(5, 10)}`
  }
  return dueDate
}

function buildBirthdayDueTime() {
  if (form.category !== 'BIRTHDAY') {
    return form.dueTime ? `${form.dueTime}:00` : null
  }
  if (form.dueCalendar === 'LUNAR') {
    return null
  }
  return form.birthdayDate ? `${form.birthdayDate}T11:00:00` : null
}

function buildBirthdayYear() {
  if (form.category !== 'BIRTHDAY') return null
  if (form.dueCalendar === 'SOLAR' && form.birthdayDate) {
    return Number(form.birthdayDate.slice(0, 4))
  }
  return form.birthdayYear ? Number(form.birthdayYear) : null
}

function toggleNotice(minutes) {
  const set = new Set(form.noticeMinutes)
  if (set.has(minutes)) set.delete(minutes)
  else set.add(minutes)
  form.noticeMinutes = [...set].sort((a, b) => b - a)
}

function toggleOwner(ownerId) {
  const set = new Set(form.ownerIds)
  if (set.has(ownerId)) set.delete(ownerId)
  else set.add(ownerId)
  form.ownerIds = [...set]
  form.ownerId = form.ownerIds[0] || ''
}

function submit() {
  const ownerIds = [...new Set(form.ownerIds.filter(Boolean))]
  emit('submit', {
    ...form,
    ownerIds,
    ownerId: ownerIds[0] || '',
    title: form.title.trim(),
    description: form.description.trim(),
    dueCalendar: form.category === 'BIRTHDAY' ? form.dueCalendar : 'SOLAR',
    lunarDueMonth: form.category === 'BIRTHDAY' && form.dueCalendar === 'LUNAR' ? Number(form.lunarDueMonth) : null,
    lunarDueDay: form.category === 'BIRTHDAY' && form.dueCalendar === 'LUNAR' ? Number(form.lunarDueDay) : null,
    lunarDueLeap: form.category === 'BIRTHDAY' && form.dueCalendar === 'LUNAR' ? Boolean(form.lunarDueLeap) : false,
    birthdayYear: buildBirthdayYear(),
    dueTime: buildBirthdayDueTime(),
  })
}
</script>

<template>
  <form class="todo-form" @submit.prevent="submit">
    <label>
      <span>标题</span>
      <input v-model="form.title" placeholder="例如：腾讯云服务器续费" required />
    </label>
    <div class="two-cols">
      <label>
        <span>分类</span>
        <select v-model="form.category">
          <option v-for="[value, label] in categories" :key="value" :value="value">{{ label }}</option>
        </select>
      </label>
    </div>
    <div class="owner-picker">
      <span>负责人</span>
      <div>
        <button
          v-for="member in members"
          :key="member.id"
          type="button"
          :class="{ active: form.ownerIds.includes(member.id) }"
          @click="toggleOwner(member.id)"
        >
          {{ member.nickname || member.username }}
        </button>
      </div>
      <p v-if="form.ownerIds.length === 0">不指定负责人</p>
    </div>
    <label v-if="form.category !== 'BIRTHDAY'">
      <span>日期时间</span>
      <input v-model="form.dueTime" type="datetime-local" />
    </label>
    <div v-if="form.category === 'BIRTHDAY'" class="birthday-date-box">
      <label>
        <span>生日历法</span>
        <select v-model="form.dueCalendar">
          <option value="SOLAR">公历生日</option>
          <option value="LUNAR">农历生日</option>
        </select>
      </label>
      <label v-if="form.dueCalendar === 'SOLAR'">
        <span>公历生日</span>
        <input v-model="form.birthdayDate" type="date" />
      </label>
      <div v-if="form.dueCalendar === 'LUNAR'" class="lunar-fields">
        <label>
          <span>出生年</span>
          <input v-model.number="form.birthdayYear" type="number" min="1900" max="2100" />
        </label>
        <label>
          <span>农历月</span>
          <input v-model.number="form.lunarDueMonth" type="number" min="1" max="12" />
        </label>
        <label>
          <span>农历日</span>
          <input v-model.number="form.lunarDueDay" type="number" min="1" max="30" />
        </label>
        <label class="lunar-leap">
          <input v-model="form.lunarDueLeap" type="checkbox" />
          闰月
        </label>
      </div>
    </div>
    <label>
      <span>备注</span>
      <textarea v-model="form.description" rows="3" placeholder="补充说明、缴费账号、采购明细等" />
    </label>
    <label>
      <span>重复规则</span>
      <select v-model="form.repeatType">
        <option v-for="[value, label] in repeats" :key="value" :value="value">{{ label }}</option>
      </select>
    </label>
    <div class="switch-row">
      <label><input v-model="form.notifySite" type="checkbox" />站内通知</label>
      <label><input v-model="form.notifyEmail" type="checkbox" />邮箱通知</label>
      <label><input v-model="form.notifyPush" type="checkbox" />App</label>
    </div>
    <div class="notice-options">
      <span>提醒规则</span>
      <div>
        <button
          v-for="[minutes, label] in noticeOptions"
          :key="minutes"
          type="button"
          :class="{ active: selectedNotice.has(minutes) }"
          @click="toggleNotice(minutes)"
        >
          {{ label }}
        </button>
      </div>
    </div>
    <div class="form-actions">
      <button type="submit" class="primary" :disabled="saving">
        <van-icon name="success" />
        保存
      </button>
      <button type="button" @click="emit('cancel')">取消</button>
    </div>
  </form>
</template>

<style scoped>
.todo-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

label {
  display: grid;
  gap: 6px;
}

label span,
.notice-options > span,
.owner-picker > span {
  color: var(--app-muted);
  font-size: 13px;
  font-weight: 800;
}

input,
select,
textarea {
  width: 100%;
  border: 1px solid var(--app-border);
  border-radius: 14px;
  padding: 0 12px;
  background: #fff;
  color: var(--app-text);
  outline: 0;
}

input,
select {
  height: 44px;
}

textarea {
  padding-top: 10px;
  resize: vertical;
  line-height: 1.5;
}

.two-cols {
  display: block;
}

.owner-picker {
  display: grid;
  gap: 8px;
}

.owner-picker div {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.owner-picker button {
  height: 34px;
  border: 1px solid var(--app-border);
  border-radius: 999px;
  padding: 0 12px;
  background: #fff;
  color: #7c5c46;
  font-weight: 800;
}

.owner-picker button.active {
  border-color: var(--app-primary);
  background: var(--app-primary);
  color: #fff;
}

.owner-picker p {
  margin: 0;
  color: var(--app-muted);
  font-size: 12px;
}

.birthday-date-box {
  display: grid;
  gap: 10px;
}

.lunar-fields {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 0.78fr) minmax(0, 0.78fr) auto;
  align-items: end;
  gap: 8px;
}

.lunar-leap {
  height: 44px;
  padding: 0 10px;
  border-radius: 14px;
  background: #fff7e8;
  color: #c2410c;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  font-weight: 800;
}

.lunar-leap input,
.lunar-fields input[type='checkbox'] {
  width: auto;
  height: auto;
}

.switch-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.switch-row label {
  height: 34px;
  padding: 0 10px;
  border-radius: 999px;
  background: #fff7e8;
  color: #c2410c;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  font-weight: 800;
}

.switch-row input {
  width: auto;
  height: auto;
}

.notice-options {
  display: grid;
  gap: 8px;
}

.notice-options div {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.notice-options button,
.form-actions button {
  height: 36px;
  border: 1px solid var(--app-border);
  border-radius: 999px;
  padding: 0 11px;
  background: #fff;
  color: #7c5c46;
  font-weight: 800;
}

.notice-options button.active {
  background: var(--app-primary);
  color: #fff;
  border-color: var(--app-primary);
}

.form-actions {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 0.72fr);
  gap: 10px;
}

.form-actions .primary {
  background: var(--app-primary);
  border-color: var(--app-primary);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
}
</style>
