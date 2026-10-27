import { defineStore } from 'pinia'

const STORAGE_KEY = 'wgxhl_recipe_user'

export const useUserStore = defineStore('user', {
  state: () => ({
    user: null,
  }),
  getters: {
    userId: (state) => state.user?.id || '',
    isLogin: (state) => Boolean(state.user?.id && state.user?.token),
    isSuperAdmin: (state) => {
      const user = state.user
      return Boolean(
        user?.id
          && user?.token
          && (user.userRole === 'super_admin' || user.username === '王师傅'),
      )
    },
    isGuest: (state) => state.user?.id === 'guest' || state.user?.username === 'guest',
    isHouseholdOwner: (state) => state.user?.memberRole === 'owner',
    isHouseholdAdmin: (state) => ['owner', 'admin'].includes(state.user?.memberRole || ''),
    canManageMembers: (state) => {
      const user = state.user
      if (!user?.id || !user?.token) return false
      if (user.id === 'guest' || user.username === 'guest') return false
      return ['owner', 'admin'].includes(user.memberRole || '') || user.userRole === 'super_admin'
    },
    canViewMembers: (state) => {
      const user = state.user
      if (!user?.id || !user?.token) return false
      return user.id !== 'guest' && user.username !== 'guest'
    },
    canMutate: (state) => {
      const user = state.user
      if (!user?.id || !user?.token) return false
      return user.id !== 'guest' && user.username !== 'guest'
    },
    roleText: (state) => {
      const user = state.user
      if (!user) return ''
      if (user.id === 'guest' || user.username === 'guest') return '游客'
      if (user.userRole === 'super_admin' || user.username === '王师傅') return '超级管理员'
      if (user.memberRole === 'owner') return '家庭所有者'
      if (user.memberRole === 'admin') return '家庭管理员'
      return '家人'
    },
  },
  actions: {
    restore() {
      const raw = localStorage.getItem(STORAGE_KEY)
      if (!raw) return
      try {
        const parsed = JSON.parse(raw)
        if (parsed?.id && parsed?.token && parsed?.userRole) {
          this.user = parsed
        } else {
          this.user = null
          localStorage.removeItem(STORAGE_KEY)
        }
      } catch (e) {
        this.user = null
        localStorage.removeItem(STORAGE_KEY)
      }
    },
    setUser(user) {
      this.user = user || null
      if (this.user) {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(this.user))
      } else {
        localStorage.removeItem(STORAGE_KEY)
      }
    },
    patchUser(fields) {
      if (!this.user || !fields) return
      this.setUser({ ...this.user, ...fields })
    },
    logout() {
      this.setUser(null)
    },
  },
})
