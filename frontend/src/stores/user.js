import { defineStore } from 'pinia'

const STORAGE_KEY = 'wgxhl_recipe_user'

export const useUserStore = defineStore('user', {
  state: () => ({
    user: null,
  }),
  getters: {
    userId: (state) => state.user?.id || '',
    isLogin: (state) => Boolean(state.user?.id),
  },
  actions: {
    restore() {
      const raw = localStorage.getItem(STORAGE_KEY)
      if (!raw) return
      try {
        this.user = JSON.parse(raw)
      } catch (e) {
        this.user = null
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
