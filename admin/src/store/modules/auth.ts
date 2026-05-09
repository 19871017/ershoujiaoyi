import { defineStore } from 'pinia'

export const useAuthStore = defineStore('admin-auth', {
  state: () => ({
    token: '',
    username: ''
  }),
  actions: {
    setToken(token: string) {
      this.token = token
    },
    clear() {
      this.token = ''
      this.username = ''
    }
  }
})