<template>
  <div class="loading-overlay" :class="{ 'fade-out': !show }">
    <div class="loading-container">
      <!-- 主要动画：旋转的圆环 -->
      <div class="spinner-container">
        <div class="spinner-ring"></div>
        <div class="spinner-ring delay-1"></div>
        <div class="spinner-ring delay-2"></div>
      </div>
      
      <!-- Logo或文字 -->
      <div class="loading-text">
        <h2 class="app-name">AI 面试助手</h2>
        <p class="loading-message">{{ message }}</p>
      </div>
      
      <!-- 进度点 -->
      <div class="loading-dots">
        <span class="dot"></span>
        <span class="dot"></span>
        <span class="dot"></span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'

const props = defineProps({
  show: {
    type: Boolean,
    default: true
  }
})

const message = ref('正在加载')

// 循环显示不同的加载消息
const messages = [
  '正在加载',
  '初始化应用',
  '准备就绪'
]

let messageIndex = 0
let messageInterval = null

onMounted(() => {
  messageInterval = setInterval(() => {
    messageIndex = (messageIndex + 1) % messages.length
    message.value = messages[messageIndex]
  }, 1500)
})

// 清理定时器
const cleanup = () => {
  if (messageInterval) {
    clearInterval(messageInterval)
  }
}

// 组件卸载时清理
import { onUnmounted } from 'vue'
onUnmounted(() => {
  cleanup()
})
</script>

<style scoped>
.loading-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
  animation: fadeIn 0.3s ease-in;
}

.loading-overlay.fade-out {
  animation: fadeOut 0.5s ease-out forwards;
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

@keyframes fadeOut {
  from {
    opacity: 1;
  }
  to {
    opacity: 0;
  }
}

.loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 32px;
}

/* 旋转圆环动画 */
.spinner-container {
  position: relative;
  width: 120px;
  height: 120px;
}

.spinner-ring {
  position: absolute;
  width: 100%;
  height: 100%;
  border: 4px solid transparent;
  border-top-color: #8ab4f8;
  border-radius: 50%;
  animation: spin 1.5s cubic-bezier(0.68, -0.55, 0.265, 1.55) infinite;
}

.spinner-ring.delay-1 {
  border-top-color: #81c995;
  animation-delay: 0.2s;
  width: 90%;
  height: 90%;
  top: 5%;
  left: 5%;
}

.spinner-ring.delay-2 {
  border-top-color: #fdd663;
  animation-delay: 0.4s;
  width: 80%;
  height: 80%;
  top: 10%;
  left: 10%;
}

@keyframes spin {
  0% {
    transform: rotate(0deg);
  }
  100% {
    transform: rotate(360deg);
  }
}

/* 文字区域 */
.loading-text {
  text-align: center;
}

.app-name {
  font-size: 2rem;
  font-weight: 700;
  background: linear-gradient(135deg, #8ab4f8 0%, #81c995 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin-bottom: 12px;
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.7;
  }
}

.loading-message {
  font-size: 1rem;
  color: #b0b0b0;
  animation: fadeInOut 1.5s ease-in-out infinite;
}

@keyframes fadeInOut {
  0%, 100% {
    opacity: 0.5;
  }
  50% {
    opacity: 1;
  }
}

/* 加载点动画 */
.loading-dots {
  display: flex;
  gap: 8px;
}

.dot {
  width: 10px;
  height: 10px;
  background: #8ab4f8;
  border-radius: 50%;
  animation: bounce 1.4s ease-in-out infinite;
}

.dot:nth-child(2) {
  background: #81c995;
  animation-delay: 0.2s;
}

.dot:nth-child(3) {
  background: #fdd663;
  animation-delay: 0.4s;
}

@keyframes bounce {
  0%, 80%, 100% {
    transform: scale(0.8);
    opacity: 0.5;
  }
  40% {
    transform: scale(1.2);
    opacity: 1;
  }
}

/* 响应式调整 */
@media (max-width: 768px) {
  .spinner-container {
    width: 80px;
    height: 80px;
  }
  
  .app-name {
    font-size: 1.5rem;
  }
  
  .loading-message {
    font-size: 0.875rem;
  }
}
</style>
