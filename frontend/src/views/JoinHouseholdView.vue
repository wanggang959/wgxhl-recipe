<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showFailToast } from 'vant'
import { joinHousehold, previewHouseholdInvite } from '../api/household'
import { useUserStore } from '../stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const code = computed(() => String(route.query.code || '').trim().toUpperCase())
const loading = ref(true)
const joining = ref(false)
const joined = ref(false)
const errorText = ref('')
const preview = ref(null)

onMounted(loadPreview)

async function loadPreview() {
  if (!code.value) {
    errorText.value = '邀请码不能为空'
    loading.value = false
    return
  }
  try {
    const res = await previewHouseholdInvite(code.value)
    preview.value = res.data
  } catch (error) {
    errorText.value = error.message || '邀请码无效或已过期'
  } finally {
    loading.value = false
  }
}

async function join() {
  if (joining.value) return
  joining.value = true
  try {
    const res = await joinHousehold(code.value)
    userStore.setUser(res.data)
    joined.value = true
    window.setTimeout(() => {
      window.location.hash = '#/recipes'
      window.location.reload()
    }, 600)
  } catch (error) {
    errorText.value = error.message || '加入失败'
    showFailToast(errorText.value)
  } finally {
    joining.value = false
  }
}

function roleLabel(role) {
  return role === 'admin' ? '管理员' : '成员'
}

function formatDate(value) {
  if (!value) return '长期有效'
  return String(value).slice(0, 16).replace('T', ' ')
}

function goHome() {
  router.replace('/recipes')
}
</script>

<template>
  <div class="app-shell join-shell">
    <main class="join-page">
      <p class="join-brand">王师傅生活助手</p>
      <h1>加入家庭</h1>

      <van-loading v-if="loading" size="24px" class="join-loading">正在读取邀请...</van-loading>

      <div v-else-if="joined" class="join-result success">
        <van-icon name="success" size="36" />
        <strong>已加入家庭</strong>
        <span>正在进入...</span>
      </div>

      <div v-else-if="errorText" class="join-result">
        <van-icon name="warning-o" size="32" />
        <strong>{{ errorText }}</strong>
        <van-button round type="warning" @click="goHome">返回首页</van-button>
      </div>

      <div v-else-if="preview" class="join-card">
        <p class="join-label">你被邀请加入</p>
        <h2>{{ preview.householdName }}</h2>
        <p class="join-meta">{{ preview.inviterName }} 邀请你以「{{ roleLabel(preview.memberRole) }}」身份加入</p>
        <p class="join-meta">有效期至 {{ formatDate(preview.expiresAt) }} · 已使用 {{ preview.usedCount }}/{{ preview.maxUses }}</p>
        <van-button block round type="warning" :loading="joining" @click="join">
          接受邀请
        </van-button>
      </div>
    </main>
  </div>
</template>

<style scoped>
.join-shell {
  min-height: 100dvh;
  display: flex;
  justify-content: center;
}

.join-page {
  width: min(100%, 430px);
  padding: 64px 20px 32px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.join-brand {
  margin: 0;
  color: var(--app-primary);
  font-size: 13px;
  font-weight: 800;
}

.join-page h1 {
  margin: 0 0 12px;
  font-size: 28px;
}

.join-loading {
  margin-top: 40px;
  text-align: center;
}

.join-card,
.join-result {
  border: 1px solid var(--app-border);
  border-radius: 20px;
  background: #fff;
  box-shadow: var(--app-shadow);
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.join-result {
  align-items: center;
  text-align: center;
  color: var(--app-muted);
}

.join-result strong {
  color: var(--app-text);
  font-size: 16px;
}

.join-label {
  margin: 0;
  color: var(--app-muted);
  font-size: 12px;
}

.join-card h2 {
  margin: 0;
  font-size: 24px;
}

.join-meta {
  margin: 0;
  color: var(--app-muted);
  font-size: 13px;
  line-height: 1.6;
}
</style>
