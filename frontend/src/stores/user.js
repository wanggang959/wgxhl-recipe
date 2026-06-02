import { defineStore } from 'pinia'

const STORAGE_KEY = 'wgxhl_recipe_user'

export const useUserStore = defineStore('user', {
  state: () => ({
    user: null,
  }),
  getters: {
    userId: (state) => state.user?.id || '',
    isLogin: (state) => Boolean(state.user?.id && state.user?.token),
    isAdmin: (state) => Boolean(state.user?.id && state.user?.token && state.user?.userRole === 'admin'),
    isNormalUser: (state) => Boolean(state.user?.id && state.user?.token && state.user?.userRole === 'user'),
    roleText: (state) => (state.user?.userRole === 'admin' ? '管理员' : '普通用户'),
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
    logout() {
      this.setUser(null)
    },
  },
})
