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
  <section class="card-panel page">
    <h3>我的</h3>
    <van-cell-group inset>
      <van-cell title="当前用户" :value="userStore.user?.nickname || userStore.user?.username || '未登录'" />
      <van-cell title="用户ID" :value="userStore.user?.id || '-'" />
    </van-cell-group>

    <div class="actions">
      <van-button
        v-if="userStore.isLogin"
        plain
        type="danger"
        size="small"
        @click="logout"
      >
        退出登录
      </van-button>
      <van-button plain type="warning" size="small" @click="router.push('/manage/base')">
        管理分类/食材
      </van-button>
    </div>

    <div v-if="!userStore.isLogin" class="login-box">
      <h4>账号登录</h4>
      <van-field v-model="loginForm.username" label="用户名" placeholder="请输入用户名" />
      <van-field
        v-model="loginForm.password"
        label="密码"
        type="password"
        placeholder="请输入密码"
      />
      <van-button type="primary" block :loading="loading" @click="doLogin">登录</van-button>
    </div>

    <div class="tip">
      <h4>用户列表（便于调试）</h4>
      <van-empty v-if="users.length === 0" description="暂无用户数据" />
      <van-cell-group v-else inset>
        <van-cell
          v-for="u in users"
          :key="u.id"
          :title="u.nickname || u.username"
          :label="u.id"
          :value="u.status || '-'" />
      </van-cell-group>
    </div>
  </section>
</template>

<style scoped>
.page {
  padding: 16px;
}

h3 {
  margin: 0 0 12px;
}

.actions {
  margin: 14px 0;
  display: flex;
  gap: 8px;
}

.login-box {
  margin-top: 12px;
  padding: 12px;
  border: 1px solid #f0f0f0;
  border-radius: 12px;
}

h4 {
  margin: 0 0 10px;
}

.tip {
  margin-top: 16px;
}
</style>
