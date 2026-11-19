<script setup>
import { onMounted, ref } from 'vue'

defineProps({ report: Object, vehicle: Object, photo: String, fallbackPhoto: String })
const emit = defineEmits(['close'])
const panel = ref(null)
onMounted(() => panel.value.showModal())
function fallback(event) {
  const img = event.currentTarget
  const src = img.getAttribute('data-fallback')
  if (src && img.getAttribute('src') !== src) img.src = src
}
</script>

<template>
  <dialog ref="panel" class="trip-report" aria-labelledby="trip-report-title" @cancel.prevent="emit('close')">
    <header class="report-head">
      <button type="button" aria-label="返回车辆页面" title="返回" autofocus @click="emit('close')"><van-icon name="arrow-left" /></button>
      <h2 id="trip-report-title">行程报告</h2><span></span>
    </header>
    <div class="report-vehicle">
      <img :src="photo" :data-fallback="fallbackPhoto" alt="" @error="fallback" />
      <div><strong>{{ vehicle.name }}</strong><p v-if="vehicle.model">{{ vehicle.model }}</p><p>已记录 {{ report.days }} 天 · {{ report.count }} 次加油</p></div>
    </div>
    <div class="report-content">
      <div class="report-period">
        <time>{{ report.date }}</time>
        <span v-if="report.start">{{ report.start }} 至 {{ report.end }}</span>
      </div>
      <section class="report-highlights" aria-label="本段能耗">
        <div><span>区间油耗</span><strong>{{ report.consumption }}</strong><small>升/百公里</small></div>
        <div><span>每公里油费</span><strong>{{ report.perKm }}</strong><small>元/公里</small></div>
      </section>
      <p v-if="report.warning" class="report-notice">{{ report.warning }}</p>
      <section class="report-band">
        <h3>区间行程</h3>
        <div class="report-metrics"><div v-for="item in report.journey" :key="item.label"><span>{{ item.label }}</span><div><strong>{{ item.value }}</strong><small>{{ item.unit }}</small></div></div></div>
      </section>
      <section class="report-band">
        <h3>加油与优惠</h3>
        <div class="report-metrics">
          <div><span>本次加油量</span><div><strong>{{ report.added }}</strong><small>升</small></div></div>
          <div><span>本次实付</span><div><strong>{{ report.paid }}</strong><small>元</small></div></div>
          <div v-for="item in report.savings" :key="item.label"><span>{{ item.label }}</span><div><strong>{{ item.value }}</strong><small>{{ item.unit }}</small></div></div>
        </div>
      </section>
    </div>
  </dialog>
</template>

<style scoped>
.trip-report { position: fixed; inset: 0; width: min(100%,430px); height: 100dvh; max-width: none; max-height: none; margin: auto; padding: 0; border: 0; box-sizing: border-box; overflow-y: auto; overscroll-behavior: contain; background: #edf6fb; color: #173141; font-variant-numeric: tabular-nums; }
.trip-report::backdrop { background: #14283a65; }
.report-head { position: sticky; top: 0; z-index: 1; display: grid; grid-template-columns: 44px minmax(0,1fr) 44px; align-items: center; gap: 8px; padding: calc(6px + env(safe-area-inset-top)) 12px 6px; background: #17618d; color: #fff; }
.report-head h2,.report-band h3 { margin: 0; font-size: 16px; }
.report-head h2 { text-align: center; }
.report-head button { width: 44px; height: 44px; padding: 0; border: 0; background: transparent; color: inherit; font-size: 20px; cursor: pointer; }
.report-vehicle { display: grid; grid-template-columns: 112px minmax(0,1fr); align-items: center; gap: 14px; padding: 13px 16px 21px; background: #17618d; color: #fff; }
.report-vehicle img { width: 112px; height: 76px; box-sizing: border-box; object-fit: contain; background: #e3f0f8; border-radius: 8px; }
.report-vehicle strong { font-size: 19px; }
.report-vehicle p { font-size: 12px; margin: 4px 0 0; color: #e6f4fb; }
.report-vehicle > div,.report-content strong { min-width: 0; overflow-wrap: anywhere; }
.report-content { padding: 13px 14px calc(24px + env(safe-area-inset-bottom)); }
.report-period { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: baseline; gap: 5px 10px; padding: 2px 0 11px; font-size: 12px; color: #4c6574; }
.report-period time { font-weight: 700; }
.report-highlights { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 8px; }
.report-highlights > div { display: grid; gap: 4px; padding: 10px 12px; min-width: 0; border-radius: 7px; background: #deedf4; color: #174e68; }
.report-highlights > div:last-child { background: #e3f3ea; color: #1b5942; }
.report-highlights strong { font-size: 24px; }
.report-content span,.report-content small,.report-notice { font-size: 12px; }
.report-notice { margin: 10px 0 0; padding: 8px 10px; border-left: 3px solid #a4672c; background: #fff4e5; color: #734515; }
.report-band { margin-top: 14px; border-top: 1px solid #cadde7; padding-top: 10px; }
.report-metrics { display: grid; grid-template-columns: repeat(3,minmax(0,1fr)); gap: 10px 7px; margin-top: 8px; }
.report-metrics > div { min-width: 0; }
.report-metrics > div > div { display: flex; align-items: baseline; flex-wrap: wrap; gap: 2px 4px; }
.report-metrics span,.report-metrics small { color: #4c6574; }
.report-metrics strong { font-size: 17px; }
</style>
