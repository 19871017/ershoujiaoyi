<template>
  <main class="login-page">
    <section class="login-card">
      <div class="login-kicker">小原圈 Admin</div>
      <h1>独立管理后台登录</h1>
      <p>后台登录仅服务独立 admin 应用，用户端不再暴露管理入口。</p>
      <form @submit.prevent="submit">
        <label>管理员账号</label>
        <input v-model.trim="username" autocomplete="username" placeholder="请输入管理员账号" />
        <label>访问口令</label>
        <input v-model.trim="accessKey" autocomplete="current-password" type="password" placeholder="请输入后台访问口令" />
        <button class="primary-btn" :disabled="!canSubmit">进入后台</button>
        <div v-if="error" class="form-error">{{ error }}</div>
      </form>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../../store/modules/auth'

const router = useRouter()
const auth = useAuthStore()
const username = ref('')
const accessKey = ref('')
const error = ref('')
const canSubmit = computed(() => username.value.length >= 2 && accessKey.value.length >= 6)

function submit() {
  error.value = ''
  if (!canSubmit.value) {
    error.value = '请输入有效管理员账号和访问口令'
    return
  }
  try {
    auth.login(username.value, accessKey.value)
  } catch {
    error.value = '登录失败：未生成有效管理员会话，请检查账号、口令和后端接入。'
    return
  }
  router.replace('/dashboard')
}
</script>
