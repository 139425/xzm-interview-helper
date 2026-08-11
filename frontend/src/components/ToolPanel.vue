<template>
  <div class="tool-panel">
    <!-- FAB Trigger -->
    <div
      class="tool-fab"
      @mousedown.prevent="startFabDrag"
      :class="{ 'is-open': drawerOpen }"
      :style="{ top: fabTop + '%' }"
      title="工具箱"
    >
      <transition name="fab-icon" mode="out-in">
        <el-icon v-if="!drawerOpen" :size="20" key="open"><Operation /></el-icon>
        <el-icon v-else :size="18" key="close"><Close /></el-icon>
      </transition>
    </div>

    <!-- Backdrop -->
    <transition name="fade">
      <div v-if="drawerOpen" class="drawer-backdrop" @click="closeDrawer"></div>
    </transition>

    <!-- Drawer -->
    <transition name="slide-right">
      <div v-if="drawerOpen" class="tool-drawer">
        <div class="drawer-header">
          <span class="drawer-title">工具箱</span>
          <button class="drawer-close" @click="closeDrawer">
            <el-icon :size="14"><Close /></el-icon>
          </button>
        </div>
        <div class="drawer-body">
          <!-- Editor Tools -->
          <div class="tool-group">
            <div class="group-label">编辑工具</div>
            <div class="tool-card" @click="openTool('code')">
              <div class="card-icon code-icon">
                <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <polyline points="16 18 22 12 16 6" />
                  <polyline points="8 6 2 12 8 18" />
                </svg>
              </div>
              <div class="card-info">
                <div class="card-name">代码编辑器</div>
                <div class="card-desc">Python / C++ / Java 在线运行</div>
              </div>
              <el-icon class="card-arrow" :size="14"><ArrowRight /></el-icon>
            </div>
            <div class="tool-card" @click="openTool('doc')">
              <div class="card-icon doc-icon">
                <el-icon :size="18"><Edit /></el-icon>
              </div>
              <div class="card-info">
                <div class="card-name">文档编辑器</div>
                <div class="card-desc">Markdown 所见即所得笔记</div>
              </div>
              <el-icon class="card-arrow" :size="14"><ArrowRight /></el-icon>
            </div>
          </div>

          <!-- Contact -->
          <div class="tool-group">
            <div class="group-label">联系方式</div>
            <div class="tool-card" @click="showWechat = true">
              <div class="card-icon wechat-icon">
                <svg viewBox="0 0 1024 1024" width="18" height="18" fill="currentColor">
                  <path d="M690.1 377.4c5.9 0 11.8 0.2 17.6 0.5-24.4-128.7-158.3-227.1-310.9-227.1C209 150.8 56.4 282.6 56.4 446.6c0 93.3 52.8 169.1 137.6 228.3l-34.4 103.3 120.1-60.1c42.5 8.5 77.3 17.1 120.1 17.1 5.7 0 11.3-0.2 16.9-0.6-3.5-12.2-5.5-24.8-5.5-37.9 0-166.2 141.5-319.3 278.9-319.3z m-185.4-73.6c21.6 0 36 14.4 36 36s-14.4 36-36 36-43.2-14.4-43.2-36 21.6-36 43.2-36z m-194.4 72c-21.6 0-43.2-14.4-43.2-36s21.6-36 43.2-36 36 14.4 36 36-14.4 36-36 36z"/>
                  <path d="M927.8 696.9c0-137.7-131.6-249.5-278.9-249.5-156.3 0-279.1 111.8-279.1 249.5S492.6 946.4 648.9 946.4c36 0 72-7.2 108-21.6l98.4 50.4-28.8-86.4c72-57.6 101.3-122.4 101.3-191.9z m-374.4-36c-14.4 0-28.8-14.4-28.8-28.8s14.4-28.8 28.8-28.8 28.8 14.4 28.8 28.8-14.4 28.8-28.8 28.8z m187.2 0c-14.4 0-28.8-14.4-28.8-28.8s14.4-28.8 28.8-28.8 28.8 14.4 28.8 28.8-14.4 28.8-28.8 28.8z"/>
                </svg>
              </div>
              <div class="card-info">
                <div class="card-name">微信联系</div>
                <div class="card-desc">扫码添加好友</div>
              </div>
              <el-icon class="card-arrow" :size="14"><ArrowRight /></el-icon>
            </div>
          </div>

          <!-- Navigation -->
          <div class="tool-group" v-if="showScrollBtn">
            <div class="group-label">导航</div>
            <div class="tool-card" @click="$emit('scroll-to-bottom')">
              <div class="card-icon scroll-icon">
                <el-icon :size="18"><ArrowDown /></el-icon>
              </div>
              <div class="card-info">
                <div class="card-name">回到底部</div>
                <div class="card-desc">查看最新消息</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </transition>

    <!-- Iframe Overlay -->
    <transition name="overlay-fade">
      <div v-if="activeTool" class="tool-overlay">
        <div class="overlay-header">
          <div class="overlay-title">
            <component :is="activeTool === 'code' ? CodeIcon : DocIcon" class="overlay-title-icon" />
            {{ activeTool === 'code' ? '代码编辑器' : '文档编辑器' }}
          </div>
          <div class="overlay-actions">
            <button class="overlay-btn" @click="openInNewTab" title="在新标签页打开">
              <el-icon :size="16"><FullScreen /></el-icon>
            </button>
            <button class="overlay-btn close-overlay-btn" @click="closeTool">
              <el-icon :size="16"><Close /></el-icon>
            </button>
          </div>
        </div>
        <div class="overlay-loading" v-if="iframeLoading">
          <el-icon :size="24" class="spin"><Loading /></el-icon>
          <span>加载中...</span>
        </div>
        <iframe
          :src="toolUrl"
          class="tool-iframe"
          frameborder="0"
          @load="iframeLoading = false"
          :style="{ opacity: iframeLoading ? 0 : 1 }"
        ></iframe>
      </div>
    </transition>

    <!-- WeChat Modal -->
    <transition name="fade">
      <div v-if="showWechat" class="wechat-overlay" @click.self="showWechat = false">
        <div class="wechat-card">
          <div class="wechat-header">
            <h3>微信联系</h3>
            <button @click="showWechat = false" class="wechat-close">
              <el-icon :size="16"><Close /></el-icon>
            </button>
          </div>
          <div class="wechat-body">
            <img
              v-if="wechatQrUrl"
              :src="wechatQrUrl"
              alt="微信二维码"
              class="wechat-qr"
            />
            <p class="wechat-tip">{{ wechatQrUrl ? '扫码添加微信好友' : '暂未配置联系二维码' }}</p>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, h, onMounted } from 'vue'
import {
  Operation, Close, ArrowRight, Edit, ArrowDown,
  FullScreen, Loading
} from '@element-plus/icons-vue'

const props = defineProps({
  showScrollBtn: { type: Boolean, default: false }
})

defineEmits(['scroll-to-bottom'])

const drawerOpen = ref(false)
const activeTool = ref(null)
const showWechat = ref(false)
const wechatQrUrl = String(import.meta.env.VITE_WECHAT_QR_URL || '').trim()
const iframeLoading = ref(false)
const fabTop = ref(50)

const CodeIcon = {
  render() {
    return h('svg', {
      viewBox: '0 0 24 24', width: 18, height: 18,
      fill: 'none', stroke: 'currentColor', 'stroke-width': 2,
      'stroke-linecap': 'round', 'stroke-linejoin': 'round'
    }, [
      h('polyline', { points: '16 18 22 12 16 6' }),
      h('polyline', { points: '8 6 2 12 8 18' })
    ])
  }
}

const DocIcon = {
  render() {
    return h('svg', {
      viewBox: '0 0 24 24', width: 18, height: 18,
      fill: 'none', stroke: 'currentColor', 'stroke-width': 2,
      'stroke-linecap': 'round', 'stroke-linejoin': 'round'
    }, [
      h('path', { d: 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z' }),
      h('polyline', { points: '14 2 14 8 20 8' }),
      h('line', { x1: 16, y1: 13, x2: 8, y2: 13 }),
      h('line', { x1: 16, y1: 17, x2: 8, y2: 17 }),
      h('polyline', { points: '10 9 9 9 8 9' })
    ])
  }
}

const toolUrl = ref('')

const toggleDrawer = () => {
  if (fabDragging.value) return
  drawerOpen.value = !drawerOpen.value
}

const closeDrawer = () => {
  drawerOpen.value = false
}

const openTool = (type) => {
  activeTool.value = type
  iframeLoading.value = true
  toolUrl.value = type === 'code' ? '/code-editor.html' : '/markdown-editor.html'
  drawerOpen.value = false
}

const closeTool = () => {
  activeTool.value = null
  toolUrl.value = ''
}

const openInNewTab = () => {
  window.open(toolUrl.value, '_blank')
  closeTool()
}

// FAB drag
const fabDragging = ref(false)
let fabDragStartY = 0
let fabDragStartTop = 0

const startFabDrag = (event) => {
  fabDragStartY = event.clientY
  fabDragStartTop = fabTop.value
  fabDragging.value = false
  document.addEventListener('mousemove', onFabDrag)
  document.addEventListener('mouseup', stopFabDrag)
}

const onFabDrag = (event) => {
  const deltaY = Math.abs(event.clientY - fabDragStartY)
  if (deltaY > 5) fabDragging.value = true
  if (!fabDragging.value) return

  const windowHeight = window.innerHeight
  const delta = ((event.clientY - fabDragStartY) / windowHeight) * 100
  fabTop.value = Math.max(10, Math.min(85, fabDragStartTop + delta))
}

const stopFabDrag = () => {
  document.removeEventListener('mousemove', onFabDrag)
  document.removeEventListener('mouseup', stopFabDrag)
  if (fabDragging.value) {
    localStorage.setItem('toolFabTop', fabTop.value.toString())
    fabDragging.value = false
  } else {
    toggleDrawer()
  }
}

onMounted(() => {
  const saved = localStorage.getItem('toolFabTop')
  if (saved) fabTop.value = parseFloat(saved)
})
</script>

<style scoped>
.tool-panel {
  position: fixed;
  z-index: 1000;
  inset: 0;
  pointer-events: none;
}

.tool-panel > * {
  pointer-events: auto;
}

/* ===== FAB ===== */
.tool-fab {
  position: fixed;
  right: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--primary-color, #4f8cff) 0%, #6366f1 100%);
  color: #fff;
  border-radius: 10px 0 0 10px;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: -2px 0 12px rgba(79, 140, 255, 0.25);
  z-index: 1001;
}

.tool-fab:hover {
  width: 44px;
  box-shadow: -4px 0 20px rgba(79, 140, 255, 0.4);
}

.tool-fab.is-open {
  border-radius: 10px 0 0 10px;
  background: linear-gradient(135deg, #6366f1 0%, var(--primary-color, #4f8cff) 100%);
}

/* ===== Backdrop ===== */
.drawer-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.3);
  z-index: 1002;
}

/* ===== Drawer ===== */
.tool-drawer {
  position: fixed;
  top: 0;
  right: 0;
  width: 300px;
  height: 100vh;
  background: var(--bg-primary, #1a1a2e);
  border-left: 1px solid var(--border-color, #2c2f3a);
  box-shadow: -4px 0 24px rgba(0, 0, 0, 0.2);
  z-index: 1003;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 20px;
  border-bottom: 1px solid var(--border-color, #2c2f3a);
  flex-shrink: 0;
}

.drawer-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary, #e7e9f0);
  letter-spacing: 0.5px;
}

.drawer-close {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: var(--bg-secondary, #232340);
  color: var(--text-secondary, #8b91a3);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.drawer-close:hover {
  background: var(--border-color, #2c2f3a);
  color: var(--text-primary, #e7e9f0);
}

.drawer-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px 16px 24px;
}

/* ===== Tool Groups ===== */
.tool-group {
  margin-bottom: 20px;
}

.group-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-tertiary, #6b7180);
  text-transform: uppercase;
  letter-spacing: 1px;
  padding: 0 4px 8px;
}

/* ===== Tool Cards ===== */
.tool-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  margin-bottom: 6px;
  border: 1px solid transparent;
}

.tool-card:hover {
  background: var(--bg-secondary, #232340);
  border-color: var(--border-color, #2c2f3a);
  transform: translateX(-2px);
}

.tool-card:active {
  transform: scale(0.98);
}

.card-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: #fff;
  font-size: 18px;
}

.code-icon {
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
}

.doc-icon {
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
}

.wechat-icon {
  background: linear-gradient(135deg, #07c160 0%, #06ad56 100%);
}

.scroll-icon {
  background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);
}

.card-info {
  flex: 1;
  min-width: 0;
}

.card-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary, #e7e9f0);
  margin-bottom: 2px;
}

.card-desc {
  font-size: 12px;
  color: var(--text-tertiary, #6b7180);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.card-arrow {
  color: var(--text-tertiary, #6b7180);
  flex-shrink: 0;
  transition: transform 0.2s;
}

.tool-card:hover .card-arrow {
  transform: translateX(2px);
  color: var(--primary-color, #4f8cff);
}

/* ===== Overlay ===== */
.tool-overlay {
  position: fixed;
  inset: 0;
  background: var(--bg-primary, #1a1a2e);
  z-index: 2000;
  display: flex;
  flex-direction: column;
}

.overlay-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  height: 48px;
  border-bottom: 1px solid var(--border-color, #2c2f3a);
  background: var(--bg-secondary, #232340);
  flex-shrink: 0;
}

.overlay-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary, #e7e9f0);
}

.overlay-title-icon {
  color: var(--primary-color, #4f8cff);
}

.overlay-actions {
  display: flex;
  gap: 4px;
}

.overlay-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  color: var(--text-secondary, #8b91a3);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.overlay-btn:hover {
  background: var(--border-color, #2c2f3a);
  color: var(--text-primary, #e7e9f0);
}

.close-overlay-btn:hover {
  background: rgba(239, 68, 68, 0.15);
  color: #ef4444;
}

.overlay-loading {
  position: absolute;
  inset: 0;
  top: 48px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: var(--text-secondary, #8b91a3);
  font-size: 14px;
  z-index: 1;
}

.spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.tool-iframe {
  flex: 1;
  width: 100%;
  border: none;
  background: #fff;
  transition: opacity 0.3s;
}

/* ===== WeChat Modal ===== */
.wechat-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1500;
}

.wechat-card {
  background: var(--bg-primary, #1a1a2e);
  border-radius: 16px;
  width: 90%;
  max-width: 360px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  overflow: hidden;
}

.wechat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid var(--border-color, #2c2f3a);
}

.wechat-header h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary, #e7e9f0);
}

.wechat-close {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  color: var(--text-secondary, #8b91a3);
  cursor: pointer;
  border-radius: 6px;
  transition: all 0.2s;
}

.wechat-close:hover {
  background: var(--bg-secondary, #232340);
  color: var(--text-primary, #e7e9f0);
}

.wechat-body {
  padding: 24px;
  text-align: center;
}

.wechat-qr {
  max-width: 240px;
  width: 100%;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.wechat-tip {
  margin-top: 14px;
  font-size: 13px;
  color: var(--text-secondary, #8b91a3);
}

/* ===== Transitions ===== */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.25s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.slide-right-enter-active {
  transition: transform 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}
.slide-right-leave-active {
  transition: transform 0.2s cubic-bezier(0.4, 0, 1, 1);
}
.slide-right-enter-from,
.slide-right-leave-to {
  transform: translateX(100%);
}

.overlay-fade-enter-active {
  transition: opacity 0.25s ease;
}
.overlay-fade-leave-active {
  transition: opacity 0.15s ease;
}
.overlay-fade-enter-from,
.overlay-fade-leave-to {
  opacity: 0;
}

.fab-icon-enter-active,
.fab-icon-leave-active {
  transition: all 0.15s ease;
}
.fab-icon-enter-from {
  opacity: 0;
  transform: rotate(-90deg) scale(0.5);
}
.fab-icon-leave-to {
  opacity: 0;
  transform: rotate(90deg) scale(0.5);
}

/* ===== Mobile ===== */
@media (max-width: 768px) {
  .tool-drawer {
    width: 85vw;
    max-width: 300px;
  }

  .tool-fab {
    width: 36px;
    height: 36px;
  }
}
</style>
