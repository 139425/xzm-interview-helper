<template>
  <nav class="mobile-workspace-nav" aria-label="常用页面" :inert="uiStore.sidebarExpanded" :aria-hidden="uiStore.sidebarExpanded ? 'true' : undefined">
    <RouterLink v-for="item in items" :key="item.id" :to="item.route"
      :class="{ 'is-current': activeId === item.id }" :aria-label="item.label"
      :aria-current="activeId === item.id ? 'page' : undefined"
      @pointerenter="prefetchWorkspace(item.id)" @focus="prefetchWorkspace(item.id)">
      <el-icon :size="20" aria-hidden="true"><component :is="item.icon" /></el-icon>
      <span>{{ item.label }}</span>
    </RouterLink>
  </nav>
</template>
<script setup>
import { computed } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import { useUIStore } from '../stores/ui'
import { workspaces, prefetchWorkspace } from '../utils/workspaceNavigation'
const route = useRoute(), uiStore = useUIStore()
const items = workspaces.filter(item => ['chat', 'schedule', 'applications', 'recruitment'].includes(item.id))
const activeId = computed(() => workspaces.filter(item => route.path === item.route || (item.id === 'chat' && route.path.startsWith('/chat/'))).at(-1)?.id)
</script>
<style scoped>
.mobile-workspace-nav { display: none; }
@media (max-width: 768px) {
  .mobile-workspace-nav { position: fixed; display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); z-index: 80; left: 10px; right: 10px; bottom: max(8px, env(safe-area-inset-bottom)); height: 62px; padding: 5px; border: 1px solid var(--xzm-border-color); border-radius: 22px; background: var(--xzm-nav-material); box-shadow: 0 4px 24px rgba(25, 27, 46, .10), inset 0 1px rgba(255,255,255,.25); backdrop-filter: blur(16px); }
  .mobile-workspace-nav a { display: flex; flex-direction: column; justify-content: center; align-items: center; gap: 4px; border-radius: 16px; color: var(--xzm-text-secondary); text-decoration: none; font-size: 10px; font-weight: 500; white-space: nowrap; transition: background-color 140ms, color 140ms, transform 140ms; }
  .mobile-workspace-nav a.is-current { color: var(--xzm-brand); background: var(--xzm-brand-soft); }
  .mobile-workspace-nav a:active { transform: scale(.96); }
}
</style>
