<script setup>
defineProps({
  birthday: {
    type: String,
    default: '',
  },
  calendar: {
    type: String,
    default: 'SOLAR',
  },
  lunarMonth: {
    type: [Number, String],
    default: '',
  },
  lunarYear: {
    type: [Number, String],
    default: '',
  },
  lunarDay: {
    type: [Number, String],
    default: '',
  },
  lunarLeap: {
    type: Boolean,
    default: false,
  },
})

function birthdayLabel(value, calendar, lunarMonth, lunarDay, lunarLeap, lunarYear) {
  if (calendar === 'LUNAR') {
    if (!lunarMonth || !lunarDay) return '未填写生日'
    const year = lunarYear ? `${lunarYear}年` : ''
    return `农历${year}${lunarLeap ? '闰' : ''}${Number(lunarMonth)}月${Number(lunarDay)}日`
  }
  if (!value) return '未填写生日'
  const [, month, day] = String(value).split('-')
  return `${Number(month)}月${Number(day)}日`
}
</script>

<template>
  <span class="birthday-chip">
    <van-icon name="birthday-cake-o" />
    {{ birthdayLabel(birthday, calendar, lunarMonth, lunarDay, lunarLeap, lunarYear) }}
  </span>
</template>

<style scoped>
.birthday-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #c2410c;
  font-size: 12px;
  font-weight: 800;
}
</style>
