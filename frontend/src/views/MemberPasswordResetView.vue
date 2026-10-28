<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { showConfirmDialog } from 'vant'
import AccountSecurityLayout from '../components/AccountSecurityLayout.vue'
import { memberResetOptions, requestMemberReset, resetManagedMember } from '../api/accountSecurity'

const userId = useRoute().params.id
const mode = ref('')
const memberName = ref('')
const error = ref('')
const busy = ref(false)
const loading = ref(true)
const done = ref(false)
const currentPassword = ref('')
const password = ref('')
const confirmation = ref('')

async function load() {
  loading.value = true; error.value = ''
  try {
    const data = (await memberResetOptions(userId)).data
    mode.value = data.mode; memberName.value = data.memberName || ''
  }
  catch (e) { error.value = e.message || '无法读取重置权限' }
  finally { loading.value = false }
}
onMounted(load)

async function submit() {
  if (busy.value) return
  error.value = ''
  if (mode.value === 'DIRECT' && password.value !== confirmation.value) { error.value = '两次输入的密码不一致'; return }
  try {
    await showConfirmDialog({ title: '确认重置密码', message: mode.value === 'DIRECT' ? '重置后，成员所有设备上的旧登录都会失效。确认继续吗？' : '将向成员本人已验证的邮箱发送重置链接，由成员自行设置新密码。确认发送吗？' })
  } catch { return }
  busy.value = true
  try {
    if (mode.value === 'DIRECT') await resetManagedMember({ userId, currentPassword: currentPassword.value, newPassword: password.value })
    else await requestMemberReset(userId)
    currentPassword.value = ''; password.value = ''; confirmation.value = ''; done.value = true
  } catch (e) { error.value = e.message || '重置失败' }
  finally { busy.value = false }
}
</script>

<template>
  <AccountSecurityLayout title="重置成员密码" back="/profile">
    <p v-if="loading" class="security-note" role="status">正在确认操作权限…</p>
    <div v-else-if="done" class="security-result" role="status"><van-icon name="passed" /><h2>{{ mode === 'DIRECT' ? '密码已重置' : '重置邮件已发送' }}</h2><p class="security-note">{{ mode === 'DIRECT' ? '请将新密码告知成员，使用新密码重新登录。' : '请成员查收本人邮箱，并在 30 分钟内设置新密码。' }}</p><router-link to="/profile" class="security-primary">返回我的</router-link></div>
    <form v-else-if="mode === 'DIRECT' || mode === 'EMAIL'" class="security-form" @submit.prevent="submit">
      <p v-if="memberName" class="security-note">成员：{{ memberName }}</p>
      <template v-if="mode === 'DIRECT'">
        <label class="security-field">你的当前密码<input v-model="currentPassword" type="password" maxlength="64" autocomplete="current-password" required placeholder="验证家庭拥有者身份" /></label>
        <label class="security-field">成员新密码<input v-model="password" type="password" minlength="8" maxlength="64" autocomplete="new-password" required placeholder="8 至 64 位" /></label>
        <label class="security-field">确认成员新密码<input v-model="confirmation" type="password" maxlength="64" autocomplete="new-password" required placeholder="再次输入新密码" /></label>
      </template>
      <p v-else class="security-note">此账号由成员独立注册或加入了其他家庭，密码只能由成员本人通过已验证邮箱设置。</p>
      <p v-if="error" class="security-error" role="alert">{{ error }}</p>
      <button class="security-primary" :disabled="busy"><van-icon :name="mode === 'DIRECT' ? 'lock' : 'envelop-o'" />{{ busy ? '正在处理…' : mode === 'DIRECT' ? '重置密码' : '发送重置邮件' }}</button>
    </form>
    <div v-else><p class="security-error" role="alert">{{ error || '请该成员先登录自己的账号，在邮箱安全中完成验证，再申请重置。' }}</p><button class="security-secondary" @click="load">重新检查</button></div>
  </AccountSecurityLayout>
</template>
