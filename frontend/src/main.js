import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { useUserStore } from './stores/user'
import { useUIStore } from './stores/ui'
import { initTheme } from './composables/useTheme'
import { AUTH_EXPIRED_EVENT } from './utils/authSession'
import { initializeAlgorithmWorkspaceMigration } from './utils/algorithmWorkspaceStorage'

// 旧 CSS（先加载，作为 baseline，后续 token/theme/glass 会按需覆盖）
import './assets/main.css'
import 'katex/dist/katex.min.css'
// Programmatic MessageBox is not rendered by a template component, so its
// theme stylesheet must be imported explicitly.
import 'element-plus/theme-chalk/el-message-box.css'

// 新设计令牌与主题（晚于旧 CSS 加载，确保新 token 桥接生效）
import './assets/tokens.css'
import './assets/theme.css'
import './assets/surface.css'

// 立即应用主题（避免首屏闪白/闪暗）
initTheme()
// 在任何新登录发生前给旧版算法草稿绑定可证明的原账号；无法归属时安全隔离。
initializeAlgorithmWorkspaceMigration()

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)

const userStore = useUserStore()
const uiStore = useUIStore()
uiStore.initialize()
userStore.restoreUserState()
window.addEventListener(AUTH_EXPIRED_EVENT, () => userStore.logout())

app.mount('#app')
