<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import AccountSecurityLayout from '../components/AccountSecurityLayout.vue'
import { confirmEmailVerification, securityStatus, sendEmailVerification } from '../api/accountSecurity'
import { useUserStore } from '../stores/user'

const router = useRouter()
const store = useUserStore()
const status = ref(null)
const loading = ref(true)
const busy = ref(false)
const error = ref('')
const request = ref(null)
const done = ref(false)
const mode = ref('verify')
const title = computed(() => mode.value === 'change' ? '更换邮箱' : mode.value === 'bind' ? '绑定邮箱' : mode.value === 'overview' ? '邮箱安全' : '验证当前邮箱')
const form = reactive({ newEmail: '', currentPassword: '', oldCode: '', newCode: '' })

async function load() {
  loading.value = true; error.value = ''
  try { status.value = (await securityStatus()).data; setMode(status.value.verified ? 'overview' : status.value.email ? 'verify' : 'bind') }
  catch (e) { error.value = e.message || '无法读取账号信息' }
  finally { loading.value = false }
}
onMounted(load)

function setMode(value) {
  mode.value = value; request.value = null; error.value = ''
  form.newEmail = value === 'verify' ? status.value?.email || '' : ''
  form.currentPassword = ''; form.oldCode = ''; form.newCode = ''
}

async function send() {
  if (busy.value) return
  busy.value = true; error.value = ''
  try {
    request.value = (await sendEmailVerification({ newEmail: form.newEmail.trim(), currentPassword: form.currentPassword })).data
    form.currentPassword = ''; form.oldCode = ''; form.newCode = ''
  } catch (e) { error.value = e.message || '邮件发送失败' }
  finally { busy.value = false }
}

async function confirm() {
  if (busy.value) return
  busy.value = true; error.value = ''
  try {
    await confirmEmailVerification({ requestId: request.value.requestId, oldCode: form.oldCode, newCode: form.newCode })
    store.logout()
    form.oldCode = ''; form.newCode = ''; done.value = true
  } catch (e) { error.value = e.message || '邮箱验证失败' }
  finally { busy.value = false }
}
</script>

<template>
  <AccountSecurityLayout :title="title" :back="done ? '/login' : '/profile'">
    <p v-if="loading" class="security-note" role="status">正在读取账号信息…</p>
    <div v-else-if="done" class="security-result" role="status">
      <van-icon name="passed" /><h2>{{ mode === 'change' ? '邮箱更换成功' : mode === 'bind' ? '邮箱绑定成功' : '邮箱验证成功' }}</h2>
      <p class="security-note">账号安全信息已更新，请重新登录。</p>
      <button class="security-primary" @click="router.replace('/login')">重新登录</button>
    </div>
    <template v-else-if="status">
      <div class="security-email-status"><p class="security-note">{{ mode === 'change' ? '原邮箱' : '当前邮箱' }}</p><strong>{{ status.email || '尚未绑定邮箱' }}</strong><p class="security-note">{{ status.verified ? '已验证' : status.email ? '未验证' : '未绑定' }}</p></div>
      <div v-if="mode === 'overview'"><button class="security-primary" @click="setMode('change')">更换邮箱</button></div>
      <form v-else-if="!request" class="security-form" @submit.prevent="send">
        <label v-if="mode !== 'verify'" class="security-field">{{ mode === 'change' ? '新邮箱地址' : '邮箱地址' }}<input v-model="form.newEmail" type="email" maxlength="200" autocomplete="email" required placeholder="请输入邮箱地址" /></label>
        <label class="security-field">生活助手登录密码<input v-model="form.currentPassword" type="password" maxlength="64" autocomplete="current-password" required placeholder="请输入生活助手登录密码" /></label>
        <p v-if="mode === 'change'" class="security-note">原邮箱、新邮箱分别验证</p>
        <p class="security-note">每账号、每邮箱每日最多 3 封 · 发送间隔 60 秒</p>
        <p v-if="error" class="security-error" role="alert">{{ error }}</p>
        <button class="security-primary" :disabled="busy"><van-icon name="envelop-o" />{{ busy ? '正在发送…' : mode === 'change' ? '向两个邮箱发送验证码' : '向此邮箱发送验证码' }}</button>
        <button v-if="mode === 'verify'" type="button" class="security-secondary" :disabled="busy" @click="setMode('change')">更换邮箱</button>
        <button v-if="mode === 'change'" type="button" class="security-secondary" :disabled="busy" @click="setMode(status.verified ? 'overview' : 'verify')">取消更换</button>
      </form>
      <form v-else class="security-form" @submit.prevent="confirm">
        <label v-if="request.needsOldCode" class="security-field">原邮箱验证码<span class="security-note">{{ status.email }}</span><input v-model="form.oldCode" inputmode="numeric" pattern="[0-9]{6}" maxlength="6" autocomplete="off" required placeholder="6 位验证码" /></label>
        <label class="security-field">{{ request.needsOldCode ? '新邮箱验证码' : '邮箱验证码' }}<span class="security-note">{{ form.newEmail }}</span><input v-model="form.newCode" inputmode="numeric" pattern="[0-9]{6}" maxlength="6" autocomplete="one-time-code" required placeholder="6 位验证码" /></label>
        <p class="security-note">验证码 10 分钟内有效。验证成功后需要重新登录。</p>
        <p v-if="error" class="security-error" role="alert">{{ error }}</p>
        <button class="security-primary" :disabled="busy"><van-icon name="passed" />{{ busy ? '正在验证…' : '确认验证' }}</button>
        <button type="button" class="security-secondary" :disabled="busy" @click="request = null; error = ''">返回修改</button>
      </form>
    </template>
    <div v-else><p class="security-error" role="alert">{{ error }}</p><button class="security-secondary" @click="load">重新加载</button></div>
  </AccountSecurityLayout>
</template>
