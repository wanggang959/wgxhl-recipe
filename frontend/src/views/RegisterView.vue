<script setup>
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showFailToast, showSuccessToast } from 'vant'
import {
  getRegisterInviteInfo,
  registerByEmail,
  registerByInvite,
  sendRegisterCode,
} from '../api/register'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()

const mode = ref('email')
const sending = ref(false)
const submitting = ref(false)
const checkingInvite = ref(false)
const countdown = ref(0)
const inviteInfo = ref(null)
let countdownTimer = null

const emailForm = reactive({
  email: '',
  code: '',
  username: '',
  nickname: '',
  password: '',
  confirmPassword: '',
  inviteCode: '',
})

const inviteForm = reactive({
  inviteCode: '',
  username: '',
  nickname: '',
  password: '',
  confirmPassword: '',
})

const canSendCode = computed(() => countdown.value <= 0 && !sending.value)

function isValidEmail(email) {
  return /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)
}

function isValidUsername(username) {
  return /^[\p{L}\p{N}_-]{2,32}$/u.test(username)
}

function validateAccount(username, password, confirmPassword) {
  if (!isValidUsername(username)) {
    showFailToast('用户名需为 2-32 位中文、字母、数字、下划线或短横线')
    return false
  }
  if (password.length < 8) {
    showFailToast('密码至少 8 位')
    return false
  }
  if (password !== confirmPassword) {
    showFailToast('两次输入的密码不一致')
    return false
  }
  return true
}

async function sendCode() {
  if (!canSendCode.value) return
  const email = emailForm.email.trim().toLowerCase()
  if (!isValidEmail(email)) {
    showFailToast('请输入有效的邮箱地址')
    return
  }
  sending.value = true
  try {
    await sendRegisterCode(email)
    showSuccessToast('验证码已发送')
    startCountdown()
  } catch (error) {
    showFailToast(error.message || '验证码发送失败')
  } finally {
    sending.value = false
  }
}

function startCountdown() {
  countdown.value = 60
  window.clearInterval(countdownTimer)
  countdownTimer = window.setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) {
      window.clearInterval(countdownTimer)
      countdownTimer = null
    }
  }, 1000)
}

onBeforeUnmount(() => {
  window.clearInterval(countdownTimer)
})

async function submitEmail() {
  if (submitting.value) return
  const email = emailForm.email.trim().toLowerCase()
  if (!isValidEmail(email)) {
    showFailToast('请输入有效的邮箱地址')
    return
  }
  if (!emailForm.code.trim()) {
    showFailToast('请输入验证码')
    return
  }
  const username = emailForm.username.trim()
  if (!validateAccount(username, emailForm.password, emailForm.confirmPassword)) return
  submitting.value = true
  try {
    const res = await registerByEmail({
      email,
      code: emailForm.code.trim(),
      username,
      nickname: emailForm.nickname.trim(),
      password: emailForm.password,
      inviteCode: emailForm.inviteCode.trim(),
    })
    enterApp(res.data)
  } catch (error) {
    showFailToast(error.message || '注册失败')
  } finally {
    submitting.value = false
  }
}

async function checkInvite() {
  const code = inviteForm.inviteCode.trim().toUpperCase()
  if (!code) {
    showFailToast('请输入邀请码')
    return
  }
  checkingInvite.value = true
  inviteInfo.value = null
  try {
    const res = await getRegisterInviteInfo(code)
    inviteInfo.value = res.data
  } catch (error) {
    showFailToast(error.message || '邀请码无效')
  } finally {
    checkingInvite.value = false
  }
}

async function submitInvite() {
  if (submitting.value) return
  const inviteCode = inviteForm.inviteCode.trim().toUpperCase()
  if (!inviteCode) {
    showFailToast('请输入邀请码')
    return
  }
  const username = inviteForm.username.trim()
  if (!validateAccount(username, inviteForm.password, inviteForm.confirmPassword)) return
  submitting.value = true
  try {
    const res = await registerByInvite({
      inviteCode,
      username,
      nickname: inviteForm.nickname.trim(),
      password: inviteForm.password,
    })
    enterApp(res.data)
  } catch (error) {
    showFailToast(error.message || '注册失败')
  } finally {
    submitting.value = false
  }
}

function enterApp(user) {
  userStore.setUser(user)
  showSuccessToast('注册成功')
  window.setTimeout(() => {
    window.location.hash = '#/recipes'
    window.location.reload()
  }, 500)
}

function roleLabel(role) {
  return role === 'admin' ? '管理员' : '成员'
}

function goLogin() {
  router.replace('/login')
}
</script>

<template>
  <div class="app-shell register-shell">
    <main class="register-page">
      <header class="register-hero">
        <p>王师傅家</p>
        <h1>生活助手</h1>
        <span>注册一个新账号，开始记录一家人的日子</span>
      </header>

      <div class="register-tabs">
        <button type="button" :class="{ active: mode === 'email' }" @click="mode = 'email'">
          邮箱注册
        </button>
        <button type="button" :class="{ active: mode === 'invite' }" @click="mode = 'invite'">
          邀请码注册
        </button>
      </div>

      <section v-if="mode === 'email'" class="register-card">
        <van-field v-model="emailForm.email" label="邮箱" placeholder="用于接收验证码" />
        <div class="code-row">
          <van-field v-model="emailForm.code" label="验证码" placeholder="6 位验证码" maxlength="6" />
          <van-button
            size="small"
            type="warning"
            :disabled="!canSendCode"
            :loading="sending"
            @click="sendCode"
          >
            {{ countdown > 0 ? `${countdown}s` : '获取验证码' }}
          </van-button>
        </div>
        <van-field v-model="emailForm.username" label="用户名" placeholder="2-32 位，登录用" />
        <van-field v-model="emailForm.nickname" label="昵称" placeholder="可选，默认同用户名" />
        <van-field v-model="emailForm.password" type="password" label="密码" placeholder="至少 8 位" />
        <van-field v-model="emailForm.confirmPassword" type="password" label="确认密码" placeholder="再输一次" />
        <van-field
          v-model="emailForm.inviteCode"
          label="邀请码"
          placeholder="可选，填写后加入对方家庭"
          maxlength="16"
        />
        <p class="register-hint">不填邀请码会自动创建「你的昵称的家」，之后也可以加入其他家庭。</p>
        <van-button block round type="warning" :loading="submitting" @click="submitEmail">
          注册并进入
        </van-button>
      </section>

      <section v-else class="register-card">
        <div class="code-row">
          <van-field
            v-model="inviteForm.inviteCode"
            label="邀请码"
            placeholder="输入家人发来的邀请码"
            maxlength="16"
          />
          <van-button size="small" :loading="checkingInvite" @click="checkInvite">查看</van-button>
        </div>
        <p v-if="inviteInfo" class="invite-info">
          加入「{{ inviteInfo.householdName }}」· {{ roleLabel(inviteInfo.memberRole) }} ·
          {{ inviteInfo.inviterName }} 邀请
        </p>
        <van-field v-model="inviteForm.username" label="用户名" placeholder="2-32 位，登录用" />
        <van-field v-model="inviteForm.nickname" label="昵称" placeholder="可选，默认同用户名" />
        <van-field v-model="inviteForm.password" type="password" label="密码" placeholder="至少 8 位" />
        <van-field v-model="inviteForm.confirmPassword" type="password" label="确认密码" placeholder="再输一次" />
        <van-button block round type="warning" :loading="submitting" @click="submitInvite">
          注册并加入
        </van-button>
      </section>

      <p class="register-footer">
        已有账号？
        <button type="button" @click="goLogin">返回登录</button>
      </p>
    </main>
  </div>
</template>

<style scoped>
.register-shell {
  min-height: 100dvh;
}

.register-page {
  width: min(100%, 430px);
  margin: 0 auto;
  padding: 40px 16px 32px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.register-hero p {
  margin: 0;
  color: var(--app-primary);
  font-size: 13px;
  font-weight: 800;
}

.register-hero h1 {
  margin: 4px 0 6px;
  font-size: 28px;
}

.register-hero span {
  color: var(--app-muted);
  font-size: 13px;
}

.register-tabs {
  display: flex;
  gap: 8px;
  padding: 4px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid var(--app-border);
}

.register-tabs button {
  flex: 1;
  height: 36px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: var(--app-muted);
  font-size: 14px;
  font-weight: 700;
}

.register-tabs button.active {
  background: var(--app-primary);
  color: #fff;
  box-shadow: 0 8px 16px rgba(249, 115, 22, 0.18);
}

.register-card {
  border: 1px solid var(--app-border);
  border-radius: 20px;
  background: #fff;
  box-shadow: var(--app-shadow);
  padding: 14px 6px 16px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.register-card :deep(.van-cell) {
  padding: 10px 12px;
}

.register-card :deep(.van-button) {
  margin: 10px 12px 0;
}

.code-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding-right: 10px;
}

.code-row :deep(.van-cell) {
  flex: 1;
  min-width: 0;
}

.code-row :deep(.van-button) {
  margin: 0;
  flex: 0 0 auto;
}

.register-hint,
.invite-info {
  margin: 2px 14px 0;
  color: var(--app-muted);
  font-size: 12px;
  line-height: 1.5;
}

.invite-info {
  color: var(--app-primary);
}

.register-footer {
  margin: 2px 0 0;
  text-align: center;
  color: var(--app-muted);
  font-size: 13px;
}

.register-footer button {
  border: 0;
  background: transparent;
  color: var(--app-primary);
  font-weight: 700;
  padding: 0 2px;
}
</style>
