import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { setAdminHeaderProvider } from './api/http'
import { useAuthStore } from './store/modules/auth'
import './styles/global.scss'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)
setAdminHeaderProvider(() => useAuthStore().headers)
app.use(router).mount('#app')