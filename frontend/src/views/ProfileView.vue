<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { closeToast, showConfirmDialog } from 'vant'
import { createUser, deleteUser, pageUser, updateUser } from '../api/user'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()
const savingUser = ref(false)
const users = ref([])
const actionStatus = ref('')
const actionStatusType = ref('success')
const showMemberForm = ref(false)
let actionStatusTimer = null

const userForm = reactive({
  id: '',
  username: '',
  nickname: '',
  password: '',
  userRole: 'user',
})

const BUILTIN_ADMIN_ID = 'admin-wangshifu'
const BUILTIN_ADMIN_USERNAME = '王师傅'

const isEditingUser = computed(() => Boolean(userForm.id))
const editingBuiltinAdmin = ref(false)

function isBuiltinAdminUser(item) {
  return item?.id === BUILTIN_ADMIN_ID || item?.username === BUILTIN_ADMIN_USERNAME
}

async function loadUsers() {
  if (!userStore.isAdmin) {
    users.value = []
    return
  }
  try {
    const res = await pageUser({ current: 1, size: 50 })
    users.value = res.data.records || []
  } catch (error) {
    users.value = []
  }
}

function logout() {
  userStore.logout()
  users.value = []
  resetUserForm()
  showActionStatus('已退出登录')
}

function resetUserForm() {
  userForm.id = ''
  userForm.username = ''
  userForm.nickname = ''
  userForm.password = ''
  userForm.userRole = 'user'
  editingBuiltinAdmin.value = false
  showMemberForm.value = false
}

function beginAddUser() {
  userForm.id = ''
  userForm.username = ''
  userForm.nickname = ''
  userForm.password = ''
  userForm.userRole = 'user'
  editingBuiltinAdmin.value = false
  showMemberForm.value = true
}

function editUser(item) {
  userForm.id = item.id
  userForm.username = item.username
  userForm.nickname = item.nickname || ''
  userForm.password = ''
  userForm.userRole = item.userRole || 'user'
  editingBuiltinAdmin.value = isBuiltinAdminUser(item)
  showMemberForm.value = true
}

async function saveUser() {
  if (!userStore.isAdmin) {
    showActionStatus('只有管理员可以管理家庭成员', 'error')
    return
  }
  if (!userForm.username.trim()) {
    showActionStatus('请输入用户名', 'error')
    return
  }
  if (!isEditingUser.value && !userForm.password.trim()) {
    showActionStatus('新增用户需要设置密码', 'error')
    return
  }

  savingUser.value = true
  try {
    const payload = {
      ...userForm,
      username: userForm.username.trim(),
      nickname: userForm.nickname.trim() || userForm.username.trim(),
      status: 'normal',
    }
    if (!payload.password) {
      delete payload.password
    }
    if (isEditingUser.value) {
      await updateUser(payload)
      showActionStatus('成员已更新')
    } else {
      await createUser(payload)
      showActionStatus('成员已新增')
    }
    resetUserForm()
    await loadUsers()
  } catch (error) {
    showActionStatus(error.message || '成员保存失败', 'error')
  } finally {
    savingUser.value = false
  }
}

async function removeUser(item) {
  if (!userStore.isAdmin) {
    showActionStatus('只有管理员可以删除家庭成员', 'error')
    return
  }
  try {
    await showConfirmDialog({
      title: '删除成员',
      message: `确认删除「${item.nickname || item.username}」吗？`,
    })
    await deleteUser(item.id)
    showActionStatus('成员已删除')
    await loadUsers()
  } catch (error) {
    if (error?.message) showActionStatus(error.message, 'error')
  }
}

function roleText(role) {
  return role === 'admin' ? '管理员' : '普通用户'
}

function showActionStatus(message, type = 'success') {
  closeToast()
  actionStatus.value = message
  actionStatusType.value = type
  window.clearTimeout(actionStatusTimer)
  actionStatusTimer = window.setTimeout(() => {
    actionStatus.value = ''
  }, 2200)
}

if (userStore.isAdmin) {
  loadUsers()
}
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
        <span>{{ userStore.isLogin ? userStore.roleText : '登录后可收藏家里的菜单' }}</span>
      </div>
    </div>

    <div v-if="actionStatus" class="action-status" :class="{ error: actionStatusType === 'error' }">
      <van-icon :name="actionStatusType === 'success' ? 'success' : 'warning-o'" />
      <span>{{ actionStatus }}</span>
    </div>

    <div v-if="userStore.isAdmin" class="action-grid">
      <button type="button" @click="router.push('/recipe/create')">
        <van-icon name="plus" />
        添加菜谱
      </button>
      <button type="button" @click="router.push('/manage/base')">
        <van-icon name="apps-o" />
        分类食材
      </button>
    </div>

    <section class="form-card">
      <h2>账号</h2>
      <van-cell title="当前用户" :value="userStore.user?.nickname || userStore.user?.username" />
      <van-cell title="权限" :value="userStore.roleText" />
      <van-button plain round type="danger" block @click="logout">退出登录</van-button>
    </section>

    <section class="form-card">
      <h2>家庭成员</h2>
      <div v-if="userStore.isAdmin" class="member-form">
        <div v-if="!showMemberForm" class="member-toolbar">
          <van-button type="warning" round size="small" @click="beginAddUser">添加成员</van-button>
        </div>
        <div v-else>
          <van-field
            v-model="userForm.username"
            class="form-field"
            label="用户名"
            placeholder="用户名唯一"
            :disabled="editingBuiltinAdmin"
          />
          <p v-if="editingBuiltinAdmin" class="builtin-hint">内置管理员用户名固定为「王师傅」，如需改名请只修改昵称。</p>
          <van-field v-model="userForm.nickname" class="form-field" label="昵称" placeholder="显示名称" />
          <van-field
            v-model="userForm.password"
            class="form-field"
            label="密码"
            type="password"
            :placeholder="isEditingUser ? '不填则不修改密码' : '请输入密码'"
          />
          <label class="role-field">
            <span>权限</span>
            <select v-model="userForm.userRole" :disabled="editingBuiltinAdmin">
              <option value="user">普通用户（查看、收藏）</option>
              <option value="admin">管理员（完全控制）</option>
            </select>
          </label>
          <div class="form-actions">
            <van-button type="warning" round size="small" :loading="savingUser" @click="saveUser">
              {{ isEditingUser ? '保存成员' : '新增成员' }}
            </van-button>
            <van-button round size="small" @click="resetUserForm">取消</van-button>
          </div>
        </div>
      </div>
      <div v-else-if="userStore.isLogin" class="permission-note">
        普通用户可以查看菜谱和收藏菜谱，家庭成员管理由管理员操作。
      </div>
      <div v-else class="permission-note">
        登录后可查看自己的权限，管理员可管理家庭成员。
      </div>

      <van-empty v-if="userStore.isAdmin && users.length === 0" description="暂无用户数据" />
      <van-cell-group v-else-if="userStore.isAdmin">
        <van-cell
          v-for="u in users"
          :key="u.id"
          :title="u.nickname || u.username"
          :label="u.username"
          :value="roleText(u.userRole)"
        >
          <template #right-icon>
            <div class="member-actions">
              <van-button size="mini" plain type="warning" @click="editUser(u)">编辑</van-button>
              <van-button
                v-if="!isBuiltinAdminUser(u)"
                size="mini"
                plain
                type="danger"
                @click="removeUser(u)"
              >删除</van-button>
            </div>
          </template>
        </van-cell>
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
}

.action-status {
  padding: 10px 12px;
  border-radius: 14px;
  background: #ecfdf3;
  color: #15803d;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 700;
}

.action-status.error {
  background: #fff1f2;
  color: #be123c;
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

::deep(.form-field) {
  margin-bottom: 10px;
  border: 1px solid var(--app-border);
  border-radius: 12px;
  background: #fffaf2;
}

.role-field {
  display: block;
  margin-bottom: 10px;
}

.role-field span {
  display: block;
  margin-bottom: 6px;
  color: #7c5c46;
  font-size: 13px;
  font-weight: 700;
}

.role-field select {
  width: 100%;
  height: 42px;
  border: 1px solid var(--app-border);
  border-radius: 12px;
  padding: 0 12px;
  background: #fffaf2;
  color: var(--app-text);
}

.form-actions,
.member-actions {
  display: flex;
  gap: 8px;
}

.member-toolbar {
  margin-bottom: 10px;
}

.form-actions {
  margin-bottom: 8px;
}

.permission-note {
  padding: 12px;
  border-radius: 14px;
  background: #fff7ed;
  color: #7c5c46;
  line-height: 1.6;
  font-size: 14px;
}

.builtin-hint {
  margin: -4px 0 10px;
  color: #9a6b4f;
  font-size: 12px;
  line-height: 1.5;
}
</style>
