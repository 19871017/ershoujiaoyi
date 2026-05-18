import { createApp as createVueApp, createSSRApp, type App as VueApp } from 'vue'
import App from './App.vue'
import GlobalTicker from './components/GlobalTicker.vue'
import { pinia } from './store'

type UniAppBootstrap = {
  app: VueApp
}

let globalTickerMounted = false
let globalTickerInstallQueued = false

function installGlobalTicker(): void {
  if (globalTickerMounted || typeof document === 'undefined') return
  if (!document.body) {
    if (!globalTickerInstallQueued && typeof window !== 'undefined') {
      globalTickerInstallQueued = true
      window.addEventListener('DOMContentLoaded', () => {
        globalTickerInstallQueued = false
        installGlobalTicker()
      }, { once: true })
    }
    return
  }
  const container = document.createElement('div')
  container.id = 'global-ticker-root'
  document.body.appendChild(container)
  createVueApp(GlobalTicker).use(pinia).mount(container)
  globalTickerMounted = true
}

export function createApp(): UniAppBootstrap {
  const app = createSSRApp(App)
  app.use(pinia)
  installGlobalTicker()
  return {
    app
  }
}