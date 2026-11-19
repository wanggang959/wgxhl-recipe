<script setup>
import { ref } from 'vue'
import { saveVehicleExpenseType } from '../api/vehicle'

defineProps({ types: Array, loading: Boolean, loadError: String })
const emit = defineEmits(['close', 'updated', 'retry'])
const colors = ['#FA6356', '#F8BF19', '#DC358A', '#24BF63', '#88978F', '#2B83CF', '#826AC1', '#287D78']
const draft = ref(null)
const managing = ref(false)
const busy = ref(false)
const error = ref('')

function edit(type) {
  error.value = ''
  draft.value = type ? { ...type } : { name: '', color: colors[5], enabled: true }
}
async function save(type) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    const { data } = await saveVehicleExpenseType(type)
    emit('updated', data)
    draft.value = null
  } catch (e) {
    error.value = e.message || '保存失败'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="types-screen" aria-label="费用类型管理">
    <header class="types-head">
      <button type="button" class="type-icon" aria-label="返回费用" :disabled="busy" @click="draft ? draft = null : emit('close')"><van-icon name="arrow-left" /></button>
      <h2>{{ draft ? (draft.key ? '编辑费用类型' : '新增费用类型') : '费用类型管理' }}</h2>
      <div class="type-tools" v-if="!draft">
        <button type="button" class="type-icon" :aria-label="managing ? '完成类型编辑' : '编辑费用类型'" :aria-pressed="managing" :disabled="loading || !!loadError || busy" @click="managing = !managing"><van-icon :name="managing ? 'success' : 'edit'" /></button>
        <button type="button" class="type-icon add-type" aria-label="新增费用类型" :disabled="loading || !!loadError || busy" @click="edit()"><van-icon name="plus" /></button>
      </div>
    </header>
    <form v-if="draft" class="type-form" @submit.prevent="save(draft)">
      <label>类型名称<input v-model.trim="draft.name" maxlength="80" required /></label>
      <fieldset><legend>颜色</legend>
        <div class="palette"><button v-for="color in colors" :key="color" type="button" :style="{ background: color }" :aria-label="'颜色 ' + color" :aria-pressed="draft.color.toUpperCase() === color" @click="draft.color = color"><van-icon v-if="draft.color.toUpperCase() === color" name="success" /></button></div>
      </fieldset>
      <button type="submit" class="type-save" :disabled="busy || !draft.name">保存</button>
    </form>
    <template v-else>
      <h3>支出类型</h3>
      <p v-if="loading" class="type-state">加载中…</p>
      <p v-else-if="loadError" class="type-state">{{ loadError }} <button type="button" @click="emit('retry')">重试</button></p>
      <ul v-else class="type-list">
        <li v-for="type in types" :key="type.key" :class="{ 'type-row-managing': managing }">
          <i :style="{color: type.color}" aria-hidden="true"></i><span>{{ type.name }}<small v-if="!type.enabled">已停用</small></span>
          <template v-if="managing">
            <button type="button" class="type-icon" :aria-label="'编辑 ' + type.name" :disabled="busy" @click="edit(type)"><van-icon name="edit" /></button>
            <button type="button" class="type-toggle" :aria-label="(type.enabled ? '停用 ' : '启用 ') + type.name" :disabled="busy" @click="save({ ...type, enabled: !type.enabled })">{{ type.enabled ? '停用' : '启用' }}</button>
          </template>
        </li>
      </ul>
    </template>
    <p v-if="error" class="type-error" role="alert">{{ error }}</p>
  </section>
</template>

<style scoped>
.types-screen { position: fixed; inset: 0; z-index: 110; width: min(100%,430px); margin: auto; overflow-y: auto; overscroll-behavior: contain; padding-bottom: env(safe-area-inset-bottom); background: #edf8ef; color: #162c29; }
.types-head { position: sticky; top: 0; display: grid; grid-template-columns: 73px minmax(0,1fr) 73px; gap: 6px; align-items: center; padding: calc(8px + env(safe-area-inset-top)) 14px 8px; background: #e6f5e9; }
.types-head h2,.types-screen h3 { margin: 0; text-align: center; font-size: 17px; }
.types-screen h3 { padding: 12px; background: #e6f5e9; font-size: 15px; }
.type-icon { display: grid; place-items: center; width: 38px; height: 38px; padding: 0; border: 0; background: transparent; color: #126747; font-size: 18px; }
.type-tools { display: flex; justify-content: flex-end; align-items: center; gap: 5px; }
.type-tools .type-icon { width: 34px; height: 34px; }
.add-type,.type-save { background: #087e49; color: #fff; border-radius: 6px; }
.type-list { list-style: none; margin: 0; padding: 0 14px; background: #f8fdf8; }
.type-list li { display: grid; grid-template-columns: 20px minmax(0,1fr); min-height: 48px; align-items: center; gap: 10px; border-bottom: 1px solid #e2ede5; }
.type-list .type-row-managing { grid-template-columns: 20px minmax(0,1fr) 38px auto; }
.type-list i { width: 17px; height: 17px; box-sizing: border-box; border: 5px solid currentColor; border-radius: 50%; }
.type-list span { overflow-wrap: anywhere; }
.type-list small { display: block; color: #52685e; font-size: 11px; }
.type-toggle { min-height: 36px; padding: 0 5px; color: #126747; border: 0; background: transparent; font-size: 13px; }
.type-form { display: grid; gap: 16px; padding: 14px; }
.type-form label { display: grid; gap: 6px; font-size: 13px; }
.type-form input { width: 100%; box-sizing: border-box; min-height: 40px; border: 1px solid #c8e0cf; border-radius: 6px; padding: 8px; background: #fff; }
.type-form fieldset { margin: 0; padding: 7px 0 0; border: 0; font-size: 13px; }
.palette { display: flex; flex-wrap: wrap; gap: 10px; }
.palette button { width: 28px; height: 28px; border: 0; border-radius: 50%; color: #112e23; }
.palette button[aria-pressed=true] { outline: 2px solid #154631; outline-offset: 2px; }
.type-save { min-height: 40px; border: 0; }
.types-screen button:disabled { opacity: .5; }
.type-state,.type-error { padding: 0 14px; }
.type-error { color: #a42b32; }
</style>
