<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { closeToast, showConfirmDialog, showLoadingToast } from 'vant'
import { createUser, deleteUser, pageUser, setUserStatus, updateUser } from '../api/user'
import MemberBirthdayCard from '../components/MemberBirthdayCard.vue'
import { DEFAULT_AVATARS } from '../constants/defaultAvatars'
import { useUserStore } from '../stores/user'
import { pickDefaultAvatar, userAvatarSrc } from '../utils/avatar'
import {
  disablePushNotification,
  enablePushNotification,
  loadPushState,
  teardownPushOnLogout,
} from '../utils/pushNotification'
import { DATA_SCOPE, markDataStale } from '../utils/dataRefresh'

const router = useRouter()
const userStore = useUserStore()
const savingUser = ref(false)
const users = ref([])
const actionStatus = ref('')
const actionStatusType = ref('success')
const showMemberForm = ref(false)
const memberPanelBodyRef = ref(null)
const pushLoading = ref(false)
const loggingOut = ref(false)
const pushState = ref({
  supported: false,
  permission: 'default',
  subscribed: false,
  configured: false,
  publicKey: '',
  needsStandalone: false,
})
let actionStatusTimer = null

const userForm = reactive({
  id: '',
  username: '',
  nickname: '',
  password: '',
  userRole: 'user',
  avatar: '',
  email: '',
  birthday: '',
  birthdayCalendar: 'SOLAR',
  lunarBirthdayYear: new Date().getFullYear(),
  lunarBirthdayMonth: 1,
  lunarBirthdayDay: 1,
  lunarBirthdayLeap: false,
  remark: '',
  notificationPreference: 'site',
})

const BUILTIN_ADMIN_ID = 'admin-wangshifu'
const BUILTIN_ADMIN_USERNAME = '王师傅'

const isEditingUser = computed(() => Boolean(userForm.id))
const editingBuiltinAdmin = ref(false)

const GUEST_ID = 'guest'

const canTogglePush = computed(() => (
  pushState.value.supported
    && pushState.value.configured
    && !pushState.value.needsStandalone
    && pushState.value.permission !== 'denied'
))

const pushStatusText = computed(() => {
  if (!pushState.value.supported) return '当前浏览器不支持'
  if (pushState.value.needsStandalone) return '添加到主屏幕后可开启'
  if (!pushState.value.configured) return '服务端未配置'
  if (pushState.value.permission === 'denied') return '系统权限已关闭'
  return pushState.value.subscribed ? '已开启' : '未开启'
})

function isBuiltinAdminUser(item) {
  return item?.id === BUILTIN_ADMIN_ID || item?.username === BUILTIN_ADMIN_USERNAME
}

function isGuestUser(item) {
  return item?.id === GUEST_ID || item?.username === 'guest'
}

function syncStoreFromUsersList() {
  if (!userStore.user) return
  const fromList = users.value.find((item) => item.id === userStore.userId)
  if (!fromList) return
  userStore.setUser({
    ...userStore.user,
    nickname: fromList.nickname,
    username: fromList.username,
    userRole: fromList.userRole,
    avatar: fromList.avatar,
  })
}

async function loadUsers() {
  if (!userStore.isAdmin) {
    users.value = []
    return
  }
  try {
    const res = await pageUser({ current: 1, size: 50 })
    users.value = res.data.records || []
    syncStoreFromUsersList()
  } catch (error) {
    users.value = []
  }
}

function goLoginPage() {
  const loginUrl = `${window.location.origin}${window.location.pathname}#/login`
  window.location.replace(loginUrl)
}

async function logout() {
  if (loggingOut.value) return
  loggingOut.value = true
  closeToast()
  showLoadingToast({ message: '正在退出...', forbidClick: true, duration: 0 })

  try {
    await Promise.race([
      teardownPushOnLogout(),
      new Promise((resolve) => {
        window.setTimeout(resolve, 1200)
      }),
    ])
  } catch (error) {
    // 推送清理失败也继续退出
  }

  userStore.logout()
  users.value = []
  resetUserForm()
  closeToast()
  goLoginPage()
}

async function refreshPushState() {
  if (!userStore.isLogin) return
  pushState.value = await loadPushState()
}

async function togglePushNotification() {
  if (!userStore.isLogin || pushLoading.value) return
  pushLoading.value = true
  try {
    if (pushState.value.subscribed) {
      pushState.value = await disablePushNotification()
      showActionStatus('已关闭手机通知')
    } else {
      pushState.value = await enablePushNotification()
      showActionStatus('已开启手机通知')
    }
  } catch (error) {
    await refreshPushState()
    showActionStatus(error.message || '通知设置失败', 'error')
  } finally {
    pushLoading.value = false
  }
}

function resetUserForm() {
  userForm.id = ''
  userForm.username = ''
  userForm.nickname = ''
  userForm.password = ''
  userForm.userRole = 'user'
  userForm.avatar = ''
  userForm.email = ''
  userForm.birthday = ''
  userForm.birthdayCalendar = 'SOLAR'
  userForm.lunarBirthdayYear = new Date().getFullYear()
  userForm.lunarBirthdayMonth = 1
  userForm.lunarBirthdayDay = 1
  userForm.lunarBirthdayLeap = false
  userForm.remark = ''
  userForm.notificationPreference = 'site'
  editingBuiltinAdmin.value = false
  showMemberForm.value = false
}

function scrollMemberPanelToTop() {
  nextTick(() => {
    memberPanelBodyRef.value?.scrollTo({ top: 0, behavior: 'smooth' })
  })
}

function beginAddUser() {
  userForm.id = ''
  userForm.username = ''
  userForm.nickname = ''
  userForm.password = ''
  userForm.userRole = 'user'
  userForm.avatar = DEFAULT_AVATARS[0]
  userForm.email = ''
  userForm.birthday = ''
  userForm.birthdayCalendar = 'SOLAR'
  userForm.lunarBirthdayYear = new Date().getFullYear()
  userForm.lunarBirthdayMonth = 1
  userForm.lunarBirthdayDay = 1
  userForm.lunarBirthdayLeap = false
  userForm.remark = ''
  userForm.notificationPreference = 'site'
  editingBuiltinAdmin.value = false
  showMemberForm.value = true
  scrollMemberPanelToTop()
}

function editUser(item) {
  userForm.id = item.id
  userForm.username = item.username
  userForm.nickname = item.nickname || ''
  userForm.password = ''
  userForm.userRole = item.userRole || 'user'
  userForm.avatar = item.avatar || pickDefaultAvatar(item.id)
  userForm.email = item.email || ''
  userForm.birthday = item.birthday || ''
  userForm.birthdayCalendar = item.birthdayCalendar || 'SOLAR'
  userForm.lunarBirthdayYear = item.lunarBirthdayYear || new Date().getFullYear()
  userForm.lunarBirthdayMonth = item.lunarBirthdayMonth || 1
  userForm.lunarBirthdayDay = item.lunarBirthdayDay || 1
  userForm.lunarBirthdayLeap = Boolean(item.lunarBirthdayLeap)
  userForm.remark = item.remark || ''
  userForm.notificationPreference = item.notificationPreference || 'site'
  editingBuiltinAdmin.value = isBuiltinAdminUser(item)
  showMemberForm.value = true
  scrollMemberPanelToTop()
}

function selectAvatar(path) {
  userForm.avatar = path
}

function syncCurrentUserAfterSave(payload) {
  if (!userStore.user || payload.id !== userStore.userId) return
  userStore.setUser({
    ...userStore.user,
    nickname: payload.nickname,
    username: payload.username,
    userRole: payload.userRole,
    avatar: payload.avatar,
  })
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
    const editingUser = users.value.find((item) => item.id === userForm.id)
    const avatar = userForm.avatar || pickDefaultAvatar(userForm.username.trim() || userForm.id)
    const payload = {
      ...userForm,
      username: userForm.username.trim(),
      nickname: userForm.nickname.trim() || userForm.username.trim(),
      email: userForm.email.trim(),
      birthday: userForm.birthdayCalendar === 'SOLAR' ? (userForm.birthday || null) : null,
      birthdayCalendar: userForm.birthdayCalendar,
      lunarBirthdayYear: userForm.birthdayCalendar === 'LUNAR' ? Number(userForm.lunarBirthdayYear) : null,
      lunarBirthdayMonth: userForm.birthdayCalendar === 'LUNAR' ? Number(userForm.lunarBirthdayMonth) : null,
      lunarBirthdayDay: userForm.birthdayCalendar === 'LUNAR' ? Number(userForm.lunarBirthdayDay) : null,
      lunarBirthdayLeap: userForm.birthdayCalendar === 'LUNAR' ? Boolean(userForm.lunarBirthdayLeap) : false,
      remark: userForm.remark.trim(),
      notificationPreference: userForm.notificationPreference,
      avatar,
      status: isEditingUser.value ? (editingUser?.status || 'normal') : 'normal',
    }
    if (!payload.password) {
      delete payload.password
    }
    if (isEditingUser.value) {
      await updateUser(payload)
      markDataStale([DATA_SCOPE.users, DATA_SCOPE.todos])
      showActionStatus('成员已更新')
    } else {
      await createUser(payload)
      markDataStale([DATA_SCOPE.users, DATA_SCOPE.todos])
      showActionStatus('成员已新增')
    }
    syncCurrentUserAfterSave(payload)
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
    users.value = users.value.filter((user) => user.id !== item.id)
    markDataStale([DATA_SCOPE.users, DATA_SCOPE.todos])
    showActionStatus('成员已删除')
  } catch (error) {
    if (error?.message) showActionStatus(error.message, 'error')
  }
}

function roleText(role) {
  if (role === 'super_admin') return '超级管理员'
  return role === 'admin' ? '管理员' : '普通用户'
}

function statusText(status) {
  return status === 'disabled' ? '已禁用' : '正常'
}

function canManageStatus(item) {
  return userStore.isSuperAdmin && !isBuiltinAdminUser(item)
}

async function toggleUserStatus(item) {
  if (!userStore.isSuperAdmin) {
    showActionStatus('仅超级管理员王师傅可以禁用或启用账户', 'error')
    return
  }
  const disabled = item.status === 'disabled'
  try {
    await showConfirmDialog({
      title: disabled ? '恢复登录' : '禁止登录',
      message: disabled
        ? `确认恢复「${item.nickname || item.username}」的登录权限吗？`
        : `确认禁止「${item.nickname || item.username}」登录吗？禁用后该账号将无法登录${isGuestUser(item) ? '（含游客入口）' : ''}。`,
    })
    await setUserStatus({
      id: item.id,
      status: disabled ? 'normal' : 'disabled',
    })
    markDataStale([DATA_SCOPE.users, DATA_SCOPE.todos])
    showActionStatus(disabled ? '已恢复登录权限' : '已禁止该用户登录')
    await loadUsers()
  } catch (error) {
    if (error?.message) showActionStatus(error.message, 'error')
  }
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

function roleIcon(role, item) {
  if (isBuiltinAdminUser(item) || role === 'super_admin') return 'award-o'
  if (role === 'admin') return 'manager-o'
  return 'friends-o'
}

function avatarTone(item) {
  if (isGuestUser(item)) return 'guest'
  if (isBuiltinAdminUser(item) || item.userRole === 'super_admin') return 'super'
  if (item.userRole === 'admin') return 'admin'
  return 'user'
}

function canEditUser(item) {
  return userStore.isAdmin && !isGuestUser(item)
}

function canDeleteUser(item) {
  return userStore.isAdmin && !isBuiltinAdminUser(item) && !isGuestUser(item)
}

const sortedUsers = computed(() => {
  const rank = (item) => {
    if (isGuestUser(item)) return 0
    if (item.userRole === 'super_admin' || isBuiltinAdminUser(item)) return 3
    if (item.userRole === 'admin') return 2
    return 1
  }
  return [...users.value].sort((a, b) => rank(a) - rank(b))
})

/** 顶部「我的」与成员列表共用：优先用服务端列表里的当前用户 */
const currentProfileUser = computed(() => {
  if (!userStore.user) return null
  const fromList = users.value.find((item) => item.id === userStore.userId)
  return fromList ? { ...userStore.user, ...fromList } : userStore.user
})

if (userStore.isAdmin) {
  loadUsers()
}

onMounted(() => {
  refreshPushState()
})
</script>

<template>
  <section class="profile-page" :class="{ 'profile-page--fill': userStore.isAdmin }">
    <div class="profile-page-top">
    <div class="profile-card">
      <div class="avatar">
        <img
          v-if="userStore.isLogin"
          :src="userAvatarSrc(currentProfileUser)"
          alt=""
          class="avatar-img"
        />
        <van-icon v-else name="contact-o" size="32" />
      </div>
      <div class="profile-card-main">
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
      <div v-if="userStore.isLogin && !userStore.isGuest" class="push-setting">
        <div>
          <strong>手机通知</strong>
          <span>{{ pushStatusText }}</span>
        </div>
        <van-button
          plain
          round
          size="small"
          type="warning"
          :loading="pushLoading"
          :disabled="!canTogglePush"
          @click="togglePushNotification"
        >
          {{ pushState.subscribed ? '关闭' : '开启' }}
        </van-button>
      </div>
      <van-button
        v-if="userStore.isLogin"
        plain
        round
        type="danger"
        block
        :loading="loggingOut"
        :disabled="loggingOut"
        @click="logout"
      >
        退出登录
      </van-button>
    </section>
    </div>

    <section v-if="userStore.isAdmin" class="member-panel">
      <header class="member-panel-head">
        <div class="member-panel-title">
          <div class="title-icon">
            <van-icon name="friends-o" size="22" />
          </div>
          <div>
            <h2>家庭成员</h2>
            <p>管理家人账号，设置不同的权限</p>
          </div>
        </div>
        <button v-if="!showMemberForm" type="button" class="add-member-btn" @click="beginAddUser">
          <van-icon name="plus" size="14" />
          添加成员
        </button>
      </header>

      <div ref="memberPanelBodyRef" class="member-panel-body">
        <div v-if="showMemberForm" class="member-form-card">
          <van-field
            v-model="userForm.username"
            class="form-field"
            label="用户名"
            placeholder="用户名唯一"
            :disabled="editingBuiltinAdmin"
          />
          <p v-if="editingBuiltinAdmin" class="builtin-hint">内置管理员用户名固定为「王师傅」，如需改名请只修改昵称。</p>
          <van-field v-model="userForm.nickname" class="form-field" label="昵称" placeholder="显示名称" />
          <van-field v-model="userForm.email" class="form-field" label="邮箱" placeholder="用于邮箱提醒" />
          <label class="role-field">
            <span>生日历法</span>
            <select v-model="userForm.birthdayCalendar">
              <option value="SOLAR">公历生日</option>
              <option value="LUNAR">农历生日</option>
            </select>
          </label>
          <van-field
            v-if="userForm.birthdayCalendar === 'SOLAR'"
            v-model="userForm.birthday"
            class="form-field"
            label="生日"
            type="date"
          />
          <div v-else class="lunar-birthday-fields">
            <label>
              <span>出生年</span>
              <input v-model.number="userForm.lunarBirthdayYear" type="number" min="1900" max="2100" />
            </label>
            <label>
              <span>农历月</span>
              <input v-model.number="userForm.lunarBirthdayMonth" type="number" min="1" max="12" />
            </label>
            <label>
              <span>农历日</span>
              <input v-model.number="userForm.lunarBirthdayDay" type="number" min="1" max="30" />
            </label>
            <label class="lunar-leap">
              <input v-model="userForm.lunarBirthdayLeap" type="checkbox" />
              闰月
            </label>
          </div>
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
          <label class="role-field">
            <span>通知偏好</span>
            <select v-model="userForm.notificationPreference">
              <option value="site">仅站内</option>
              <option value="email">仅邮箱</option>
              <option value="site,email">站内+邮箱</option>
              <option value="site,push">站内+App</option>
              <option value="site,email,push">站内+邮箱+App</option>
            </select>
          </label>
          <van-field v-model="userForm.remark" class="form-field" label="备注" placeholder="家庭称呼、提醒备注" />
          <div class="avatar-field">
            <span>头像</span>
            <div class="avatar-picker">
              <button
                v-for="path in DEFAULT_AVATARS"
                :key="path"
                type="button"
                class="avatar-option"
                :class="{ active: userForm.avatar === path }"
                @click="selectAvatar(path)"
              >
                <img :src="path" alt="" />
              </button>
            </div>
          </div>
          <div class="form-actions">
            <van-button type="warning" round size="small" :loading="savingUser" @click="saveUser">
              {{ isEditingUser ? '保存成员' : '新增成员' }}
            </van-button>
            <van-button round size="small" @click="resetUserForm">取消</van-button>
          </div>
        </div>

        <template v-if="!showMemberForm">
          <van-empty v-if="users.length === 0" description="暂无用户数据" />
          <div v-else class="member-list">
          <article
            v-for="u in sortedUsers"
            :key="u.id"
            class="member-card"
            :class="{ 'is-disabled': u.status === 'disabled' }"
          >
            <div class="member-card-body">
              <div class="member-avatar" :class="`tone-${avatarTone(u)}`">
                <img :src="userAvatarSrc(u)" alt="" class="member-avatar-img" />
              </div>
              <div class="member-main">
                <div class="member-top">
                  <div class="member-names">
                    <h3>{{ u.nickname || u.username }}</h3>
                    <p>用户名：{{ u.username }}</p>
                  </div>
                  <span class="role-badge" :class="`role-${u.userRole || 'user'}`">
                    <van-icon :name="roleIcon(u.userRole, u)" size="12" />
                    {{ roleText(u.userRole) }}
                  </span>
                </div>
                <div class="member-bottom">
                  <span class="member-status" :class="{ danger: u.status === 'disabled' }">
                    状态：{{ statusText(u.status) }}
                  </span>
                  <div class="member-extra">
                    <span>{{ u.email || '未填写邮箱' }}</span>
                    <MemberBirthdayCard
                      :birthday="u.birthday"
                      :calendar="u.birthdayCalendar"
                      :lunar-year="u.lunarBirthdayYear"
                      :lunar-month="u.lunarBirthdayMonth"
                      :lunar-day="u.lunarBirthdayDay"
                      :lunar-leap="u.lunarBirthdayLeap"
                    />
                    <span>近期待办：{{ u.recentTodoCount || 0 }}</span>
                  </div>
                  <div v-if="isGuestUser(u)" class="member-actions member-actions--guest">
                    <button
                      v-if="canManageStatus(u)"
                      type="button"
                      class="action-chip"
                      @click="toggleUserStatus(u)"
                    >
                      <van-icon :name="u.status === 'disabled' ? 'passed' : 'lock'" size="16" />
                      <span>{{ u.status === 'disabled' ? '启用' : '禁用' }}</span>
                    </button>
                    <button v-else type="button" class="action-chevron" aria-label="查看">
                      <van-icon name="arrow" size="18" />
                    </button>
                  </div>
                  <div v-else-if="isBuiltinAdminUser(u)" class="member-actions">
                    <button type="button" class="edit-pill" @click="editUser(u)">
                      <van-icon name="edit" size="14" />
                      编辑
                    </button>
                  </div>
                  <div v-else class="member-actions member-actions--icons">
                    <button v-if="canEditUser(u)" type="button" class="action-item" @click="editUser(u)">
                      <span class="action-icon edit"><van-icon name="edit" size="18" /></span>
                      <span>编辑</span>
                    </button>
                    <button
                      v-if="canManageStatus(u)"
                      type="button"
                      class="action-item"
                      @click="toggleUserStatus(u)"
                    >
                      <span class="action-icon lock"><van-icon :name="u.status === 'disabled' ? 'passed' : 'lock'" size="18" /></span>
                      <span>{{ u.status === 'disabled' ? '启用' : '禁用' }}</span>
                    </button>
                    <button v-if="canDeleteUser(u)" type="button" class="action-item" @click="removeUser(u)">
                      <span class="action-icon delete"><van-icon name="delete-o" size="18" /></span>
                      <span>删除</span>
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </article>
          </div>
        </template>

        <footer class="member-footer">
          <van-icon name="shield-o" size="16" />
          <span>家人账号用于记录菜谱、收藏和饮食偏好，数据仅在家人间共享</span>
        </footer>
      </div>
    </section>

    <section v-else class="form-card">
      <h2>家庭成员</h2>
      <div v-if="userStore.isLogin" class="permission-note">
        普通用户可以查看菜谱和收藏菜谱；管理员可管理成员，仅超级管理员王师傅可禁用账户。
      </div>
      <div v-else class="permission-note">
        登录后可查看自己的权限，管理员可管理家庭成员。
      </div>
    </section>
  </section>
</template>

<style scoped>
.profile-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.profile-page--fill {
  min-height: 100%;
}

.profile-page-top {
  flex-shrink: 0;
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
  grid-template-columns: 58px minmax(0, 1fr);
  align-items: center;
  gap: 12px;
}

.profile-card-main {
  min-width: 0;
}

.avatar {
  width: 58px;
  height: 58px;
  border-radius: 18px;
  background: var(--app-primary-soft);
  color: var(--app-primary);
  display: grid;
  place-items: center;
  overflow: hidden;
}

.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
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

.push-setting {
  margin: 8px 0 12px;
  padding: 12px;
  border-radius: 14px;
  background: #fff7ed;
  border: 1px solid var(--app-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.push-setting strong {
  display: block;
  color: var(--app-text);
  font-size: 14px;
}

.push-setting span {
  display: block;
  margin-top: 3px;
  color: var(--app-muted);
  font-size: 12px;
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

.form-actions {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.member-panel {
  flex: 0 1 auto;
  max-height: min(58vh, 520px);
  min-height: 220px;
  display: flex;
  flex-direction: column;
  padding: 18px 16px 16px;
  border-radius: 24px;
  background: #fffdf8;
  border: 1px solid #f3e5d8;
  box-shadow: 0 12px 28px rgba(154, 52, 18, 0.08);
  overflow: hidden;
}

.member-panel-head {
  flex-shrink: 0;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.member-panel-title {
  display: flex;
  gap: 10px;
  min-width: 0;
}

.title-icon {
  width: 42px;
  height: 42px;
  border-radius: 14px;
  background: linear-gradient(145deg, #fff4e8, #ffe8d2);
  color: var(--app-primary);
  display: grid;
  place-items: center;
  flex-shrink: 0;
}

.member-panel-title h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 800;
  color: var(--app-text);
}

.member-panel-title p {
  margin: 4px 0 0;
  font-size: 12px;
  color: #9a8b7f;
  line-height: 1.5;
}

.add-member-btn {
  flex-shrink: 0;
  height: 36px;
  padding: 0 14px;
  border: 0;
  border-radius: 999px;
  background: linear-gradient(135deg, #fb923c, #f97316);
  color: #fff;
  font-size: 13px;
  font-weight: 800;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  box-shadow: 0 8px 18px rgba(249, 115, 22, 0.28);
}

.member-panel-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  -webkit-overflow-scrolling: touch;
  padding-right: 2px;
  padding-bottom: 8px;
  overscroll-behavior: contain;
  touch-action: pan-y;
}

.member-panel-body::-webkit-scrollbar {
  width: 4px;
}

.member-panel-body::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: rgba(249, 115, 22, 0.35);
}

.member-form-card {
  margin-bottom: 14px;
  padding: 14px;
  padding-bottom: 18px;
  border-radius: 18px;
  background: #fff;
  border: 1px solid var(--app-border);
}

.member-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-bottom: 4px;
}

.member-card {
  padding: 14px;
  border-radius: 20px;
  background: #fff;
  border: 1px solid #f1e4d8;
  box-shadow: 0 8px 20px rgba(154, 52, 18, 0.05);
}

.member-card.is-disabled {
  background: #fafafa;
  border-color: #ececec;
}

.member-card-body {
  display: grid;
  grid-template-columns: 56px minmax(0, 1fr);
  gap: 12px;
  align-items: start;
}

.member-avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: #fff;
  overflow: hidden;
  border: 2px solid rgba(255, 255, 255, 0.85);
}

.member-avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-field {
  margin-bottom: 10px;
}

.avatar-field > span {
  display: block;
  margin-bottom: 8px;
  color: var(--app-muted);
  font-size: 13px;
  font-weight: 700;
}

.avatar-picker {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 8px;
}

.avatar-option {
  padding: 0;
  border: 2px solid transparent;
  border-radius: 14px;
  background: #fff7ed;
  overflow: hidden;
  aspect-ratio: 1;
}

.avatar-option.active {
  border-color: var(--app-primary);
  box-shadow: 0 0 0 2px rgba(234, 88, 12, 0.15);
}

.avatar-option img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.member-avatar.tone-guest {
  background: linear-gradient(145deg, #fdba74, #f97316);
}

.member-avatar.tone-user {
  background: linear-gradient(145deg, #fcd34d, #f59e0b);
  color: #7c4a03;
}

.member-avatar.tone-admin {
  background: linear-gradient(145deg, #86efac, #22c55e);
  color: #14532d;
}

.member-avatar.tone-super {
  background: linear-gradient(145deg, #fdba74, #ea580c);
}

.member-main {
  min-width: 0;
}

.member-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.member-names h3 {
  margin: 0;
  font-size: 17px;
  font-weight: 800;
  color: var(--app-text);
}

.member-names p {
  margin: 4px 0 0;
  font-size: 12px;
  color: #9a8b7f;
}

.role-badge {
  flex-shrink: 0;
  height: 26px;
  padding: 0 10px;
  border-radius: 999px;
  background: #fff4e8;
  color: #ea580c;
  font-size: 11px;
  font-weight: 800;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.role-badge.role-admin,
.role-badge.role-super_admin {
  background: #fff7ed;
  color: #c2410c;
}

.member-bottom {
  margin-top: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
}

.member-status {
  font-size: 13px;
  color: #6b7280;
  font-weight: 600;
}

.member-extra {
  min-width: 100%;
  display: flex;
  flex-wrap: wrap;
  gap: 6px 10px;
  color: #8a7462;
  font-size: 12px;
}

.lunar-birthday-fields {
  margin-bottom: 10px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 0.78fr) minmax(0, 0.78fr) auto;
  align-items: end;
  gap: 8px;
}

.lunar-birthday-fields label {
  display: grid;
  gap: 6px;
}

.lunar-birthday-fields span {
  color: var(--app-muted);
  font-size: 13px;
  font-weight: 700;
}

.lunar-birthday-fields input[type='number'] {
  width: 100%;
  height: 42px;
  border: 1px solid var(--app-border);
  border-radius: 12px;
  padding: 0 10px;
  background: #fffaf2;
  color: var(--app-text);
}

.lunar-leap {
  height: 42px;
  padding: 0 10px;
  border-radius: 12px;
  background: #fff7ed;
  color: #c2410c;
  display: inline-flex !important;
  align-items: center;
  gap: 5px !important;
  font-size: 13px;
  font-weight: 800;
}

.member-status.danger {
  color: #dc2626;
}

.member-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.member-actions--icons {
  gap: 14px;
}

.action-item {
  border: 0;
  background: transparent;
  padding: 0;
  min-width: 40px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  color: #6b7280;
  font-size: 11px;
  font-weight: 700;
}

.action-icon {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  display: grid;
  place-items: center;
}

.action-icon.edit {
  background: #fff4e8;
  color: #ea580c;
}

.action-icon.lock {
  background: #f3f4f6;
  color: #6b7280;
}

.action-icon.delete {
  background: #fff1f2;
  color: #dc2626;
}

.edit-pill {
  height: 32px;
  padding: 0 14px;
  border: 0;
  border-radius: 999px;
  background: #fff4e8;
  color: #ea580c;
  font-size: 13px;
  font-weight: 800;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.action-chip {
  height: 32px;
  padding: 0 12px;
  border: 0;
  border-radius: 999px;
  background: #f3f4f6;
  color: #4b5563;
  font-size: 12px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.action-chevron {
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: 50%;
  background: #f9fafb;
  color: #9ca3af;
  display: grid;
  place-items: center;
}

.member-footer {
  flex-shrink: 0;
  margin-top: 12px;
  padding: 12px 14px;
  border-radius: 16px;
  background: #fff4e8;
  color: #9a6b4f;
  font-size: 12px;
  line-height: 1.6;
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.member-card.is-disabled .member-names h3 {
  color: #9ca3af;
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
