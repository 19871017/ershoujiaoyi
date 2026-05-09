import { defineStore } from 'pinia'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: '',
    nickname: '',
    avatar: ''
  }),
  actions: {
    setToken(token: string) {
      this.token = token
    },
    clearSession() {
      this.token = ''
      this.nickname = ''
      this.avatar = ''
    }
  }
})