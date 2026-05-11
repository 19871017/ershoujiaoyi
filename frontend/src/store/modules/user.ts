import { defineStore } from 'pinia'

const TOKEN_STORAGE_KEY = 'xiaoyuan_user_access_token'

function readStoredToken() {
  try {
    return uni.getStorageSync(TOKEN_STORAGE_KEY) || ''
  } catch {
    return ''
  }
}

function writeStoredToken(token: string) {
  try {
    if (token) {
      uni.setStorageSync(TOKEN_STORAGE_KEY, token)
    } else {
      uni.removeStorageSync(TOKEN_STORAGE_KEY)
    }
  } catch {
    // Storage is a convenience mirror only; in-memory state remains authoritative for this run.
  }
}

export const useUserStore = defineStore('user', {
  state: () => ({
    token: readStoredToken(),
    nickname: '',
    avatar: ''
  }),
  actions: {
    setToken(token: string) {
      this.token = token
      writeStoredToken(token)
    },
    clearSession() {
      this.token = ''
      writeStoredToken('')
      this.nickname = ''
      this.avatar = ''
    }
  }
})