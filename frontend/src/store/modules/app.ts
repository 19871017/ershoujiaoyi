import { defineStore } from 'pinia'

export const useAppStore = defineStore('app', {
  state: () => ({
    loading: false,
    theme: 'light' as 'light' | 'dark'
  }),
  actions: {
    setLoading(loading: boolean) {
      this.loading = loading
    },
    setTheme(theme: 'light' | 'dark') {
      this.theme = theme
    }
  }
})