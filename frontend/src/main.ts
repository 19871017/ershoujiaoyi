import { createApp as createVueApp, createSSRApp, type App as VueApp, type Component } from 'vue'
import App from './App.vue'
import GlobalBottomNav from './components/GlobalBottomNav.vue'
import GlobalTicker from './components/GlobalTicker.vue'
import { pinia } from './store'

type UniAppBootstrap = {
  app: VueApp
}

type GlobalMountTarget = {
  component: Component
  rootId: string
  mounted: boolean
  installQueued: boolean
}

const globalTickerTarget: GlobalMountTarget = {
  component: GlobalTicker,
  rootId: 'global-ticker-root',
  mounted: false,
  installQueued: false
}

const globalBottomNavTarget: GlobalMountTarget = {
  component: GlobalBottomNav,
  rootId: 'global-bottom-nav-root',
  mounted: false,
  installQueued: false
}

function installGlobalComponent(target: GlobalMountTarget): void {
  if (target.mounted || typeof document === 'undefined') return
  if (!document.body) {
    if (!target.installQueued && typeof window !== 'undefined') {
      target.installQueued = true
      window.addEventListener('DOMContentLoaded', function installAfterBodyReady(): void {
        target.installQueued = false
        installGlobalComponent(target)
      }, { once: true })
    }
    return
  }
  const container = document.createElement('div')
  container.id = target.rootId
  document.body.appendChild(container)
  createVueApp(target.component).use(pinia).mount(container)
  target.mounted = true
}

function installGlobalTicker(): void {
  installGlobalComponent(globalTickerTarget)
}

function installGlobalBottomNav(): void {
  installGlobalComponent(globalBottomNavTarget)
}

export function createApp(): UniAppBootstrap {
  const app = createSSRApp(App)
  app.use(pinia)
  installGlobalTicker()
  installGlobalBottomNav()
  return {
    app
  }
}