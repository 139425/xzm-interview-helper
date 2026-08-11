<template>
  <div class="xzm-toolbar-root">
    <!-- Desktop: FAB + Drawer -->
    <template v-if="!isMobile">
      <!-- FAB Trigger (button for accessibility) -->
      <button
        type="button"
        class="xzm-toolbar-fab"
        @mousedown.prevent="onFabDown"
        @keydown.enter="drawerOpen = !drawerOpen"
        @keydown.space.prevent="drawerOpen = !drawerOpen"
        :class="{ 'is-open': drawerOpen }"
        aria-label="工具箱"
        aria-expanded="drawerOpen"
      >
        <transition name="fab-icon" mode="out-in">
          <svg v-if="!drawerOpen" key="open" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <rect x="3" y="3" width="7" height="7" /><rect x="14" y="3" width="7" height="7" /><rect x="3" y="14" width="7" height="7" /><rect x="14" y="14" width="7" height="7" />
          </svg>
          <svg v-else key="close" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
          </svg>
        </transition>
      </button>

      <!-- Backdrop -->
      <transition name="fade">
        <div v-if="drawerOpen" class="xzm-toolbar-backdrop" @click="drawerOpen = false"></div>
      </transition>

      <!-- Drawer -->
      <transition name="slide-right">
        <div v-if="drawerOpen" class="xzm-toolbar-drawer" role="dialog" aria-label="工具箱">
          <div class="xzm-toolbar-drawer__header">
            <span class="xzm-toolbar-drawer__title">工具箱</span>
            <button class="xzm-toolbar-drawer__close" @click="drawerOpen = false" aria-label="关闭工具箱">
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" aria-hidden="true"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
            </button>
          </div>
          <div class="xzm-toolbar-drawer__body">
            <template v-for="group in toolGroups" :key="group.label">
              <div class="xzm-toolbar-group" role="group" :aria-label="group.label">
                <div class="xzm-toolbar-group__label">{{ group.label }}</div>
                <button
                  v-for="item in group.items"
                  :key="item.id"
                  type="button"
                  class="xzm-toolbar-card"
                  @click="handleAction(item.id)"
                >
                  <div class="xzm-toolbar-card__icon" :class="'xzm-toolbar-card__icon--' + item.color" aria-hidden="true">
                    <component :is="item.icon" />
                  </div>
                  <div class="xzm-toolbar-card__info">
                    <div class="xzm-toolbar-card__name">{{ item.label }}</div>
                    <div class="xzm-toolbar-card__desc">{{ item.desc }}</div>
                  </div>
                  <svg class="xzm-toolbar-card__arrow" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><polyline points="9 18 15 12 9 6"/></svg>
                </button>
              </div>
            </template>
          </div>
        </div>
      </transition>
    </template>

    <!-- Mobile: Top bar popover -->
    <el-popover
      v-else
      v-model:visible="mobileVisible"
      placement="bottom-end"
      :width="260"
      trigger="click"
      popper-class="xzm-toolbar-mobile"
    >
      <template #reference>
        <button type="button" class="xzm-btn xzm-btn--ghost xzm-btn--icon" aria-label="工具菜单">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
            <rect x="3" y="3" width="7" height="7" /><rect x="14" y="3" width="7" height="7" /><rect x="3" y="14" width="7" height="7" /><rect x="14" y="14" width="7" height="7" />
          </svg>
        </button>
      </template>
      <div class="xzm-toolbar-mobile__body">
        <template v-for="group in toolGroups" :key="group.label">
          <div class="xzm-toolbar-mobile__group-label">{{ group.label }}</div>
          <button
            v-for="item in group.items"
            :key="item.id"
            type="button"
            class="xzm-toolbar-mobile__item"
            @click="handleAction(item.id)"
          >
            <div class="xzm-toolbar-mobile__dot" :class="'xzm-toolbar-mobile__dot--' + item.color" aria-hidden="true"></div>
            <span>{{ item.label }}</span>
          </button>
        </template>
      </div>
    </el-popover>
  </div>
</template>

<script setup>
import { ref, computed, h, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElPopover } from 'element-plus'

const props = defineProps({
  isMobile: { type: Boolean, default: false },
})
const emit = defineEmits([
  'open-md-editor',
  'open-code-editor',
  'open-resume-editor',
  'open-wechat-modal',
  'scroll-to-bottom',
  'toggle-question-nav',
  'open-network-review',
  'open-html-showcase',
])

const drawerOpen = ref(false)
const mobileVisible = ref(false)
const router = useRouter()

// Reset drawer state when switching between mobile/desktop
watch(() => props.isMobile, () => {
  drawerOpen.value = false
  mobileVisible.value = false
})

function makeIcon(d) {
  return () =>
    h('svg', { viewBox: '0 0 24 24', width: 18, height: 18, fill: 'none', stroke: 'currentColor', 'stroke-width': 2, 'stroke-linecap': 'round', 'stroke-linejoin': 'round', 'aria-hidden': 'true' },
      d.split('|').map((p) => h('path', { d: p }))
    )
}

const allItems = [
  { id: 'recruitment', label: '求职信息', desc: '每日更新校招、实习与官网岗位', color: 'career', icon: makeIcon('M4 7h16v13H4z|M8 3v4|M16 3v4|M4 11h16') },
  { id: 'code', label: '代码编辑器', desc: 'Python / C++ / Java 在线运行', color: 'purple', icon: makeIcon('m16 18 6-6-6-6|m8 6-6 6 6 6'), url: '/code-editor.html' },
  { id: 'md', label: '文档编辑器', desc: 'Markdown 所见即所得笔记', color: 'green', icon: makeIcon('M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z|M14 2v6h6|M16 13H8|M16 17H8|M10 9H8'), url: '/markdown-editor.html' },
  { id: 'resume', label: '简历编辑器', desc: '在线简历排版与导出', color: 'blue', icon: makeIcon('M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z|M14 2v6h6|M12 18v-6|M9 15h6'), url: '/resume_editor.html' },
  { id: 'html', label: 'HTML 在线展示', desc: '实时预览 HTML 代码效果', color: 'pink', icon: makeIcon('M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z|M14 2v6h6|M10 13l2 2 4-4'), url: '/拓展实验实验报告.html' },
  { id: 'wechat', label: '微信联系', desc: '扫码添加好友', color: 'wechat', icon: makeIcon('M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z') },
  { id: 'scroll', label: '回到底部', desc: '查看最新消息', color: 'yellow', icon: makeIcon('M12 5v14|m19 12-7 7-7-7') },
  { id: 'nav', label: '题目导航', desc: '快速定位对话题目', color: 'gray', icon: makeIcon('M8 6h13|M8 12h13|M8 18h13|M3 6h.01|M3 12h.01|M3 18h.01') },
]

const toolGroups = computed(() => [
  {
    label: '求职',
    items: allItems.filter(i => ['recruitment', 'resume'].includes(i.id)),
  },
  {
    label: '编辑工具',
    items: allItems.filter(i => ['code', 'md'].includes(i.id)),
  },
  {
    label: '学习资料',
    items: allItems.filter(i => ['network', 'html'].includes(i.id)),
  },
  {
    label: '其他',
    items: allItems.filter(i => ['wechat', 'scroll', 'nav'].includes(i.id)),
  },
])

function handleAction(id) {
  drawerOpen.value = false
  mobileVisible.value = false

  const item = allItems.find(i => i.id === id)
  if (!item) return

  if (item.url) {
    window.open(item.url, '_blank', 'noopener')
    return
  }

  switch (id) {
    case 'recruitment': router.push('/recruitment'); break
    case 'wechat': emit('open-wechat-modal'); break
    case 'scroll': emit('scroll-to-bottom'); break
    case 'nav':    emit('toggle-question-nav'); break
  }
}

// FAB drag support
let fabDragStartY = 0
let fabDragged = false

function onFabDown(event) {
  fabDragStartY = event.clientY
  fabDragged = false
  document.addEventListener('mousemove', onFabMove)
  document.addEventListener('mouseup', onFabUp)
}

function onFabMove(event) {
  if (Math.abs(event.clientY - fabDragStartY) > 5) fabDragged = true
}

function onFabUp() {
  document.removeEventListener('mousemove', onFabMove)
  document.removeEventListener('mouseup', onFabUp)
  if (!fabDragged) drawerOpen.value = !drawerOpen.value
}
</script>

<style scoped>
.xzm-toolbar-root {
  position: fixed;
  inset: 0;
  z-index: 9999;
  pointer-events: none;
}
.xzm-toolbar-root > * {
  pointer-events: auto;
}

/* ===== FAB ===== */
.xzm-toolbar-fab {
  position: fixed;
  right: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--xzm-brand-500, #6366f1) 0%, var(--xzm-brand-400, #818cf8) 100%);
  color: #fff;
  border: none;
  border-radius: 12px 0 0 12px;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: -2px 0 12px rgba(99, 102, 241, 0.25);
  z-index: 1001;
}
.xzm-toolbar-fab:hover {
  width: 48px;
  box-shadow: -4px 0 20px rgba(99, 102, 241, 0.4);
}
.xzm-toolbar-fab:focus-visible {
  outline: 2px solid var(--xzm-brand-300, #a5b4fc);
  outline-offset: 2px;
}
.xzm-toolbar-fab.is-open {
  background: linear-gradient(135deg, var(--xzm-brand-600, #4f46e5) 0%, var(--xzm-brand-500, #6366f1) 100%);
}

/* ===== Backdrop ===== */
.xzm-toolbar-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.3);
  z-index: 1002;
}

/* ===== Drawer ===== */
.xzm-toolbar-drawer {
  position: fixed;
  top: 0;
  right: 0;
  width: 300px;
  height: 100vh;
  background: var(--xzm-surface-0, #1a1a2e);
  border-left: 1px solid var(--xzm-border, #2c2f3a);
  box-shadow: -4px 0 24px rgba(0, 0, 0, 0.2);
  z-index: 1003;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.xzm-toolbar-drawer__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 20px;
  border-bottom: 1px solid var(--xzm-border, #2c2f3a);
  flex-shrink: 0;
}
.xzm-toolbar-drawer__title {
  font-size: 16px;
  font-weight: 600;
  color: var(--xzm-text-primary, #e7e9f0);
  letter-spacing: 0.5px;
}
.xzm-toolbar-drawer__close {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: var(--xzm-surface-1, #232340);
  color: var(--xzm-text-tertiary, #8b91a3);
  border-radius: var(--xzm-radius-sm, 8px);
  cursor: pointer;
  transition: all 0.2s;
}
.xzm-toolbar-drawer__close:hover {
  background: var(--xzm-surface-2, #2c2f3a);
  color: var(--xzm-text-primary, #e7e9f0);
}
.xzm-toolbar-drawer__close:focus-visible {
  outline: 2px solid var(--xzm-brand-300, #a5b4fc);
  outline-offset: 2px;
}
.xzm-toolbar-drawer__body {
  flex: 1;
  overflow-y: auto;
  padding: 12px 16px 24px;
}

/* ===== Tool Groups ===== */
.xzm-toolbar-group {
  margin-bottom: 20px;
}
.xzm-toolbar-group__label {
  font-size: 11px;
  font-weight: 600;
  color: var(--xzm-text-tertiary, #6b7180);
  text-transform: uppercase;
  letter-spacing: 1px;
  padding: 0 4px 8px;
}

/* ===== Tool Cards ===== */
.xzm-toolbar-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  border-radius: var(--xzm-radius-md, 10px);
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  margin-bottom: 4px;
  border: 1px solid transparent;
  background: none;
  width: 100%;
  text-align: left;
  font-family: inherit;
  color: inherit;
}
.xzm-toolbar-card:hover {
  background: var(--xzm-surface-1, #232340);
  border-color: var(--xzm-border, #2c2f3a);
  transform: translateX(-2px);
}
.xzm-toolbar-card:active {
  transform: scale(0.98);
}
.xzm-toolbar-card:focus-visible {
  outline: 2px solid var(--xzm-brand-300, #a5b4fc);
  outline-offset: 2px;
}
.xzm-toolbar-card__icon {
  width: 40px;
  height: 40px;
  border-radius: var(--xzm-radius-md, 10px);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: #fff;
  pointer-events: none;
}
.xzm-toolbar-card__icon--purple  { background: linear-gradient(135deg, #6366f1, #8b5cf6); }
.xzm-toolbar-card__icon--green   { background: linear-gradient(135deg, #10b981, #059669); }
.xzm-toolbar-card__icon--blue    { background: linear-gradient(135deg, #3b82f6, #2563eb); }
.xzm-toolbar-card__icon--orange  { background: linear-gradient(135deg, #f59e0b, #d97706); }
.xzm-toolbar-card__icon--pink    { background: linear-gradient(135deg, #ec4899, #db2777); }
.xzm-toolbar-card__icon--wechat  { background: linear-gradient(135deg, #07c160, #06ad56); }
.xzm-toolbar-card__icon--yellow  { background: linear-gradient(135deg, #eab308, #ca8a04); }
.xzm-toolbar-card__icon--gray    { background: linear-gradient(135deg, #6b7280, #4b5563); }
.xzm-toolbar-card__icon--career  { background: #1769e0; }
.xzm-toolbar-card__info {
  flex: 1;
  min-width: 0;
  pointer-events: none;
}
.xzm-toolbar-card__name {
  font-size: 14px;
  font-weight: 600;
  color: var(--xzm-text-primary, #e7e9f0);
  margin-bottom: 2px;
}
.xzm-toolbar-card__desc {
  font-size: 12px;
  color: var(--xzm-text-tertiary, #6b7180);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.xzm-toolbar-card__arrow {
  color: var(--xzm-text-tertiary, #6b7180);
  flex-shrink: 0;
  transition: transform 0.2s;
  pointer-events: none;
}
.xzm-toolbar-card:hover .xzm-toolbar-card__arrow {
  transform: translateX(2px);
  color: var(--xzm-brand-400, #818cf8);
}

/* ===== Mobile Popover ===== */
.xzm-toolbar-mobile__body {
  padding: 4px 0;
  max-height: 60vh;
  overflow-y: auto;
}
.xzm-toolbar-mobile__group-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--xzm-text-tertiary, #6b7180);
  text-transform: uppercase;
  letter-spacing: 1px;
  padding: 8px 12px 4px;
}
.xzm-toolbar-mobile__item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 12px 16px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--xzm-text-primary, #e7e9f0);
  font-family: inherit;
  font-size: 13px;
  text-align: left;
  cursor: pointer;
  transition: background-color 0.15s;
  min-height: 44px;
}
.xzm-toolbar-mobile__item:hover {
  background: var(--xzm-hover-bg, rgba(255,255,255,0.06));
}
.xzm-toolbar-mobile__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.xzm-toolbar-mobile__dot--purple  { background: #6366f1; }
.xzm-toolbar-mobile__dot--green   { background: #10b981; }
.xzm-toolbar-mobile__dot--blue    { background: #3b82f6; }
.xzm-toolbar-mobile__dot--orange  { background: #f59e0b; }
.xzm-toolbar-mobile__dot--pink    { background: #ec4899; }
.xzm-toolbar-mobile__dot--wechat  { background: #07c160; }
.xzm-toolbar-mobile__dot--yellow  { background: #eab308; }
.xzm-toolbar-mobile__dot--gray    { background: #6b7280; }
.xzm-toolbar-mobile__dot--career  { background: #1769e0; }

/* ===== Transitions ===== */
.fade-enter-active, .fade-leave-active { transition: opacity 0.25s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }

.slide-right-enter-active { transition: transform 0.3s cubic-bezier(0.16, 1, 0.3, 1); }
.slide-right-leave-active { transition: transform 0.2s cubic-bezier(0.4, 0, 1, 1); }
.slide-right-enter-from, .slide-right-leave-to { transform: translateX(100%); }

.fab-icon-enter-active, .fab-icon-leave-active { transition: all 0.15s ease; }
.fab-icon-enter-from { opacity: 0; transform: rotate(-90deg) scale(0.5); }
.fab-icon-leave-to   { opacity: 0; transform: rotate(90deg) scale(0.5); }

@media (max-width: 768px) {
  .xzm-toolbar-drawer { width: 85vw; max-width: 300px; }
}

/* Reduced motion */
@media (prefers-reduced-motion: reduce) {
  .xzm-toolbar-fab,
  .xzm-toolbar-card,
  .xzm-toolbar-card__arrow,
  .xzm-toolbar-drawer__close,
  .xzm-toolbar-mobile__item {
    transition: none !important;
  }
}
</style>
