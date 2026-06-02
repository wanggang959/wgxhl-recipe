<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showFailToast, showSuccessToast } from 'vant'
import { login, pageUser } from '../api/user'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const loginForm = reactive({
  username: '',
  password: '',
})
const users = ref([])

async function doLogin() {
  if (!loginForm.username || !loginForm.password) {
    showFailToast('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const res = await login({ ...loginForm })
    userStore.setUser(res.data)
    showSuccessToast('登录成功')
    await loadUsers()
  } catch (error) {
    showFailToast(error.message || '登录失败')
  } finally {
    loading.value = false
  }
}

async function loadUsers() {
  try {
    const res = await pageUser({ current: 1, size: 20 })
    users.value = res.data.records || []
  } catch (error) {
    users.value = []
  }
}

function logout() {
  userStore.logout()
  showSuccessToast('已退出登录')
}

loadUsers()
</script>

<template>
  <section class="profile-page">
    <div class="profile-card">
      <div class="avatar">
        <van-icon name="contact-o" size="32" />
      </div>
      <div>
        <p>我的</p>
        <h1>{{ userStore.user?.nickname || userStore.user?.username || '未登录' }}</h1>
        <span>{{ userStore.user?.id || '登录后可收藏家里的菜单' }}</span>
      </div>
    </div>

    <div class="action-grid">
      <button type="button" @click="router.push('/recipe/create')">
        <van-icon name="plus" />
        添加菜谱
      </button>
      <button type="button" @click="router.push('/manage/base')">
        <van-icon name="apps-o" />
        分类食材
      </button>
    </div>

    <section v-if="!userStore.isLogin" class="form-card">
      <h2>账号登录</h2>
      <van-field v-model="loginForm.username" class="form-field" label="用户名" placeholder="请输入用户名" />
      <van-field
        v-model="loginForm.password"
        class="form-field"
        label="密码"
        type="password"
        placeholder="请输入密码"
      />
      <van-button type="warning" round block :loading="loading" @click="doLogin">登录</van-button>
    </section>

    <section v-else class="form-card">
      <h2>账号</h2>
      <van-cell title="当前用户" :value="userStore.user?.nickname || userStore.user?.username" />
      <van-cell title="用户ID" :value="userStore.user?.id" />
      <van-button plain round type="danger" block @click="logout">退出登录</van-button>
    </section>

    <section class="form-card">
      <h2>家里成员</h2>
      <van-empty v-if="users.length === 0" description="暂无用户数据" />
      <van-cell-group v-else>
        <van-cell
          v-for="u in users"
          :key="u.id"
          :title="u.nickname || u.username"
          :label="u.id"
          :value="u.status || '-'"
        />
      </van-cell-group>
    </section>
  </section>
</template>

<style scoped>
.profile-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.profile-card,
.form-card {
  padding: 16px;
  border-radius: 20px;
  background: #fff;
  border: 1px solid var(--app-border);
  box-shadow: 0 10px 24px rgba(154, 52, 18, 0.06);
}

.profile-card {
  display: grid;
  grid-template-columns: 58px 1fr;
  align-items: center;
  gap: 12px;
}

.avatar {
  width: 58px;
  height: 58px;
  border-radius: 18px;
  background: var(--app-primary-soft);
  color: var(--app-primary);
  display: grid;
  place-items: center;
}

.profile-card p,
.profile-card h1,
.profile-card span,
h2 {
  margin: 0;
}

.profile-card p {
  color: var(--app-primary);
  font-size: 13px;
  font-weight: 800;
}

.profile-card h1 {
  margin-top: 2px;
  color: var(--app-text);
  font-size: 22px;
}

.profile-card span {
  display: block;
  margin-top: 4px;
  color: var(--app-muted);
  font-size: 12px;
  overflow-wrap: anywhere;
}

.action-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.action-grid button {
  height: 52px;
  border: 1px solid var(--app-border);
  border-radius: 16px;
  background: #fff;
  color: #7c5c46;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-weight: 700;
}

h2 {
  margin-bottom: 12px;
  color: var(--app-text);
  font-size: 17px;
}

:deep(.form-field) {
  margin-bottom: 10px;
  border: 1px solid var(--app-border);
  border-radius: 12px;
  background: #fffaf2;
}
</style>
