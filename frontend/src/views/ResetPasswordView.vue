<script setup>
import { onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AccountSecurityLayout from '../components/AccountSecurityLayout.vue'
import { checkResetLink, confirmPasswordReset, requestPasswordReset } from '../api/accountSecurity'
import { useUserStore } from '../stores/user'

const route = useRoute()
const router = useRouter()
const store = useUserStore()
const token = ref('')
const state = ref('request')
const email = ref('')
const password = ref('')
const confirmation = ref('')
const error = ref('')
const busy = ref(false)
const cooldown = ref(0)
let timer

watch(() => route.query.token, async (value) => {
  if (typeof value !== 'string' || !value) return
  token.value = value
  state.value = 'checking'
  password.value = ''; confirmation.value = ''; error.value = ''
  // Keep the bearer secret in memory only; opening the link never consumes it.
  await router.replace({ path: '/reset-password', query: {} })
  try {
    await checkResetLink(value)
    if (value !== token.value) return
    state.value = 'reset'
  } catch (e) {
    if (value !== token.value) return
    state.value = 'invalid'
    error.value = e.message || '重置链接已失效'
  }
}, { immediate: true })
onUnmounted(() => { clearInterval(timer); token.value = ''; password.value = ''; confirmation.value = '' })

function tickCooldown() {
  cooldown.value = 60
  clearInterval(timer)
  timer = setInterval(() => { if (--cooldown.value <= 0) clearInterval(timer) }, 1000)
}

async function submitRequest() {
  if (busy.value || cooldown.value) return
  busy.value = true
  error.value = ''
  try {
    await requestPasswordReset(email.value.trim())
    state.value = 'sent'
    tickCooldown()
  } catch (e) { error.value = e.message || '暂时无法发送邮件' }
  finally { busy.value = false }
}

async function submitReset() {
  if (busy.value) return
  error.value = ''
  if (password.value.length < 8 || password.value.length > 64) { error.value = '密码长度须为 8 至 64 位'; return }
  if (password.value !== confirmation.value) { error.value = '两次输入的密码不一致'; return }
  busy.value = true
  try {
    await confirmPasswordReset(token.value, password.value)
    store.logout()
    token.value = ''; password.value = ''; confirmation.value = ''
    state.value = 'done'
  } catch (e) { error.value = e.message || '密码重置失败' }
  finally { busy.value = false }
}
</script>

<template>
  <AccountSecurityLayout title="找回密码">
    <p v-if="state === 'checking'" class="security-note" role="status">正在验证重置链接…</p>
    <form v-else-if="state === 'request'" class="security-form" @submit.prevent="submitRequest">
      <label class="security-field">已验证的邮箱<input v-model="email" type="email" maxlength="200" autocomplete="email" required placeholder="请输入账号绑定的邮箱" /></label>
      <p class="security-note">重置链接 30 分钟内有效，只能使用一次。尚未验证邮箱的成员，请登录后先完成邮箱验证。</p>
      <p v-if="error" class="security-error" role="alert">{{ error }}</p>
      <button class="security-primary" :disabled="busy || cooldown > 0"><van-icon name="envelop-o" />{{ busy ? '正在发送…' : cooldown > 0 ? `${cooldown} 秒后重试` : '发送重置邮件' }}</button>
    </form>
    <form v-else-if="state === 'reset'" class="security-form" @submit.prevent="submitReset">
      <label class="security-field">新密码<input v-model="password" type="password" minlength="8" maxlength="64" autocomplete="new-password" required placeholder="8 至 64 位" /></label>
      <label class="security-field">确认新密码<input v-model="confirmation" type="password" maxlength="64" autocomplete="new-password" required placeholder="再次输入新密码" /></label>
      <p class="security-note">修改后，所有设备上的旧登录都会失效。</p>
      <p v-if="error" class="security-error" role="alert">{{ error }}</p>
      <button class="security-primary" :disabled="busy"><van-icon name="passed" />{{ busy ? '正在保存…' : '重置密码' }}</button>
    </form>
    <div v-else class="security-result" role="status">
      <van-icon :name="state === 'invalid' ? 'warning-o' : 'passed'" />
      <h2>{{ state === 'done' ? '密码已重置' : state === 'sent' ? '请查收邮件' : '链接不可用' }}</h2>
      <p class="security-note">{{ state === 'done' ? '请用新密码重新登录。' : state === 'sent' ? '如果邮箱已完成验证，你将收到重置链接，也请查看垃圾邮件。每日发送额度有限，请勿重复申请。' : error }}</p>
      <router-link v-if="state === 'done'" class="security-primary" to="/login">返回登录</router-link>
      <button v-else class="security-primary" :disabled="cooldown > 0" @click="state = 'request'; error = ''">{{ cooldown > 0 ? `${cooldown} 秒后可重新申请` : '重新申请' }}</button>
    </div>
    <router-link v-if="state !== 'done'" class="security-secondary" to="/login">返回登录</router-link>
  </AccountSecurityLayout>
</template>
