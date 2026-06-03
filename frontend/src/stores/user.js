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
    isAdmin: (state) => {
      const user = state.user
      if (!user?.id || !user?.token) return false
      return user.userRole === 'admin' || user.userRole === 'super_admin'
    },
    isNormalUser: (state) => Boolean(state.user?.id && state.user?.token && state.user?.userRole === 'user'),
    isGuest: (state) => state.user?.id === 'guest' || state.user?.username === 'guest',
    roleText: (state) => {
      if (state.user?.id === 'guest' || state.user?.username === 'guest') return '游客'
      if (state.user?.userRole === 'super_admin' || state.user?.username === '王师傅') return '超级管理员'
      return state.user?.userRole === 'admin' ? '管理员' : '普通用户'
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
