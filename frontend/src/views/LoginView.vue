<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { guestLogin as guestLoginApi, login } from '../api/user'
import { useUserStore } from '../stores/user'

const REMEMBER_USERNAME_KEY = 'wgxhl_recipe_login_username'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const passwordVisible = ref(false)
const rememberMe = ref(false)
const statusText = ref('')
const statusType = ref('success')

const form = reactive({
  username: '',
  password: '',
})

onMounted(() => {
  const rememberedUsername = localStorage.getItem(REMEMBER_USERNAME_KEY)
  if (rememberedUsername) {
    form.username = rememberedUsername
    rememberMe.value = true
  }
})

function getRedirectTarget() {
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
  return redirect && redirect.startsWith('/') ? redirect : '/recipes'
}

async function submit() {
  if (!form.username.trim() || !form.password.trim()) {
    showStatus('请输入用户名和密码', 'error')
    return
  }

  loading.value = true
  try {
    const res = await login({
      username: form.username.trim(),
      password: form.password,
    })
    userStore.setUser(res.data)
    saveRememberedUsername()
    showStatus('登录成功，正在进入家常菜单...')
    setTimeout(() => {
      router.replace(getRedirectTarget())
    }, 260)
  } catch (error) {
    showStatus(error.message || '登录失败', 'error')
  } finally {
    loading.value = false
  }
}

async function guestLogin() {
  loading.value = true
  try {
    const res = await guestLoginApi()
    userStore.setUser(res.data)
    showStatus('欢迎随便逛逛～')
    setTimeout(() => {
      router.replace(getRedirectTarget())
    }, 200)
  } catch (error) {
    showStatus(error.message || '游客登录失败', 'error')
  } finally {
    loading.value = false
  }
}

function saveRememberedUsername() {
  if (rememberMe.value) {
    localStorage.setItem(REMEMBER_USERNAME_KEY, form.username.trim())
  } else {
    localStorage.removeItem(REMEMBER_USERNAME_KEY)
  }
}

function showForgotHint() {
  showStatus('这是家里的小菜单，忘记密码可以找管理员重置。', 'error')
}

function showStatus(message, type = 'success') {
  statusText.value = message
  statusType.value = type
}
</script>

<template>
  <div class="login-page">
    <section class="login-hero" aria-label="王师傅家常菜谱">
      <div class="brand-lockup">
        <div class="chef-mark" aria-hidden="true">
          <img src="/login-chef.webp" alt="" />
        </div>
        <div class="brand-main">王师傅家</div>
        <div class="brand-sub">
          <span></span>
          家常菜谱
          <span></span>
        </div>
      </div>
    </section>

    <main class="login-content">
      <div class="wave-card">
        <header class="welcome">
          <p>欢迎来到</p>
          <h1>王师傅和谢老板的家</h1>
          <div class="login-tip">
            <span></span>
            请登录以继续
            <span></span>
          </div>
        </header>

        <section class="form-card" aria-label="登录表单">
          <div class="dish-ornament">
            <span class="steam steam-1"></span>
            <span class="steam steam-2"></span>
            <span class="pot-mark">
              <img class="pot-icon" src="/login-pot.webp" alt="" />
            </span>
            <span class="steam steam-3"></span>
          </div>

          <div v-if="statusText" class="status" :class="{ error: statusType === 'error' }">
            <van-icon :name="statusType === 'error' ? 'warning-o' : 'checked'" />
            <span>{{ statusText }}</span>
          </div>

          <div class="field-group">
            <label for="login-username">
              <van-icon name="contact-o" />
              用户名
            </label>
            <div class="input-shell">
              <input
                id="login-username"
                v-model="form.username"
                class="native-input"
                placeholder="请输入用户名"
                autocomplete="username"
              >
            </div>
          </div>

          <div class="field-group">
            <label for="login-password">
              <van-icon name="lock" />
              密码
            </label>
            <div class="input-shell password-shell">
              <input
                id="login-password"
                v-model="form.password"
                class="native-input"
                :type="passwordVisible ? 'text' : 'password'"
                placeholder="请输入密码"
                autocomplete="current-password"
                @keyup.enter="submit"
              >
              <button class="icon-button" type="button" @click="passwordVisible = !passwordVisible">
                <van-icon :name="passwordVisible ? 'eye-o' : 'closed-eye'" />
              </button>
            </div>
          </div>

          <div class="form-options">
            <van-checkbox v-model="rememberMe" icon-size="18px" checked-color="#ff6b18">
              记住我
            </van-checkbox>
            <button type="button" @click="showForgotHint">
              忘记密码？
              <van-icon name="arrow" />
            </button>
          </div>

          <van-button class="login-button" round block :loading="loading" loading-text="正在进家门..." @click="submit">
            进入家常菜单
          </van-button>

          <div class="guest-row">
            <span>暂无账号？</span>
            <button type="button" @click="guestLogin">游客登录</button>
          </div>

          <img class="family-illustration" src="/login-family.webp" alt="" />
        </section>

        <footer class="footer-note">
          <van-icon name="like" />
          <span>记录家里的味道，今天也要好好吃饭</span>
          <van-icon name="like" />
        </footer>
      </div>
    </main>
  </div>
</template>

<style scoped>
.login-page {
  width: min(100%, 430px);
  min-height: 100vh;
  margin: 0 auto;
  overflow: hidden;
  color: #3e2818;
  font-family: "LXGW WenKai", "STKaiti", "KaiTi", "Microsoft YaHei", "PingFang SC", sans-serif;
  background:
    url('/login-bg.webp') top center / 100% auto no-repeat,
    linear-gradient(180deg, #fff8ec 0%, #fffaf3 48%, #fff6e9 100%);
  box-shadow: 0 0 0 1px rgba(245, 223, 199, 0.55);
}

.login-hero {
  position: relative;
  height: 226px;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding-top: 50px;
}

.brand-lockup {
  position: relative;
  z-index: 1;
  display: grid;
  justify-items: center;
  color: #4b2f1c;
  text-shadow: 0 2px 14px rgba(255, 255, 255, 0.7);
}

.chef-mark {
  position: relative;
  width: 62px;
  height: 50px;
  margin-bottom: 7px;
  overflow: hidden;
  border-radius: 18px;
  filter: drop-shadow(0 8px 12px rgba(255, 112, 18, 0.16));
  mix-blend-mode: multiply;
}

.chef-mark img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
  object-position: center 47%;
  transform: scale(3.25);
}

.brand-main {
  font-family: "STKaiti", "KaiTi", "FangSong", "Microsoft YaHei", serif;
  font-size: 28px;
  line-height: 1;
  font-weight: 900;
  letter-spacing: 0;
}

.brand-sub {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 10px;
  color: #ff6b18;
  font-size: 15px;
  font-weight: 800;
}

.brand-sub span,
.login-tip span {
  width: 28px;
  height: 2px;
  border-radius: 999px;
  background: currentColor;
  opacity: 0.7;
}

.login-content {
  position: relative;
  z-index: 2;
  margin-top: -4px;
  padding: 0 22px 26px;
}

.wave-card {
  position: relative;
}

.welcome {
  text-align: center;
  transform: translateX(8px);
}

.welcome p {
  margin: 0;
  color: #4b2f1c;
  font-size: 20px;
  line-height: 1.2;
  font-weight: 900;
}

.welcome h1 {
  margin: 7px 0 12px;
  color: #ff6b18;
  font-size: 25px;
  line-height: 1.16;
  font-weight: 900;
  letter-spacing: 0;
}

.login-tip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 11px;
  margin-bottom: 18px;
  color: #8f7562;
  font-size: 16px;
  font-weight: 700;
}

.login-tip span {
  width: 8px;
  height: 8px;
  transform: rotate(45deg);
  background: #ff8a2a;
}

.form-card {
  position: relative;
  padding: 68px 18px 0;
  overflow: hidden;
  border: 1px solid rgba(244, 211, 181, 0.86);
  border-radius: 31px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.84) 0%, rgba(255, 255, 255, 0.97) 42%, rgba(255, 249, 238, 0.95) 100%);
  box-shadow:
    0 20px 42px rgba(174, 84, 13, 0.15),
    inset 0 1px 0 rgba(255, 255, 255, 0.95);
}

.form-card::before {
  content: none;
}

.dish-ornament {
  position: absolute;
  top: 16px;
  left: 50%;
  display: flex;
  align-items: center;
  gap: 8px;
  color: #ff7a1a;
  font-size: 33px;
  transform: translateX(-50%);
}

.pot-mark {
  width: 86px;
  height: 62px;
  display: block;
  overflow: hidden;
  border-radius: 18px;
  filter: drop-shadow(0 7px 12px rgba(255, 116, 18, 0.18));
  mix-blend-mode: multiply;
}

.pot-icon {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
  object-position: center 43%;
}

.steam {
  width: 8px;
  height: 16px;
  border: solid #ffbf7a;
  border-width: 0 2px 0 0;
  border-radius: 50%;
}

.steam-1 {
  transform: rotate(-18deg);
}

.steam-2 {
  height: 22px;
}

.steam-3 {
  transform: rotate(18deg);
}

.status {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  padding: 10px 12px;
  border: 1px solid rgba(22, 163, 74, 0.13);
  border-radius: 14px;
  color: #15803d;
  background: rgba(236, 253, 243, 0.9);
  font-size: 13px;
  line-height: 1.35;
}

.status.error {
  border-color: rgba(225, 29, 72, 0.12);
  color: #be123c;
  background: rgba(255, 241, 242, 0.92);
}

.field-group {
  margin-bottom: 18px;
}

.field-group label {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 0 10px 4px;
  color: #4a3323;
  font-size: 16px;
  font-weight: 800;
}

.field-group label .van-icon {
  color: #ff6b18;
  font-size: 22px;
}

.input-shell {
  height: 52px;
  display: flex;
  align-items: center;
  padding: 0 14px;
  border: 2px solid #dfc4b3;
  border-radius: 999px;
  background: #fffaf4;
  box-shadow:
    0 0 0 1px rgba(255, 255, 255, 0.72),
    inset 0 2px 9px rgba(117, 58, 16, 0.04),
    0 1px 0 rgba(255, 255, 255, 0.88);
}

.input-shell:focus-within {
  border-color: rgba(255, 107, 24, 0.72);
  background: rgba(255, 255, 255, 0.98);
  box-shadow:
    0 0 0 3px rgba(255, 122, 26, 0.09),
    inset 0 2px 8px rgba(117, 58, 16, 0.03);
}

.native-input {
  width: 100%;
  min-width: 0;
  height: 100%;
  padding: 0;
  border: 0;
  outline: none;
  color: #3e2818;
  background: transparent;
  font: inherit;
  font-size: 16px;
}

.native-input::placeholder {
  color: #b7aaa1;
}

.password-shell {
  gap: 8px;
}

.icon-button,
.form-options button,
.guest-row button {
  padding: 0;
  border: 0;
  color: inherit;
  background: transparent;
  font: inherit;
}

.icon-button {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  color: #99897d;
  font-size: 21px;
}

.form-options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin: 3px 4px 22px;
  color: #8e7d70;
  font-size: 14px;
}

.form-options button {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  white-space: nowrap;
}

::deep(.van-checkbox__label) {
  color: #8e7d70;
}

.login-button {
  height: 58px;
  border: 0;
  color: #fff;
  font-size: 18px;
  font-weight: 900;
  background: linear-gradient(180deg, #ff9424 0%, #ff6814 100%);
  box-shadow: 0 12px 22px rgba(255, 106, 22, 0.28);
}

.guest-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-top: 16px;
  color: #9a8473;
  font-size: 14px;
}

.guest-row button {
  color: #ff6b18;
  font-weight: 800;
}

.family-illustration {
  display: block;
  width: calc(100% + 28px);
  height: 160px;
  object-fit: cover;
  object-position: 58% 30%;
  margin: 18px -14px 0;
  border-radius: 0 0 28px 28px;
  opacity: 0.92;
}

.footer-note {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-top: 24px;
  color: #694734;
  font-size: 17px;
  line-height: 1.4;
}

.footer-note .van-icon {
  color: #ff8a24;
  font-size: 18px;
}

@media (max-width: 360px) {
  .login-content {
    padding-right: 16px;
    padding-left: 16px;
  }

  .brand-main {
    font-size: 26px;
  }

  .welcome h1 {
    font-size: 23px;
  }

  .welcome p,
  .login-tip {
    font-size: 15px;
  }

  .form-card {
    padding-right: 14px;
    padding-left: 14px;
  }

  .family-illustration {
    margin-right: -14px;
    margin-left: -14px;
  }
}
</style>
