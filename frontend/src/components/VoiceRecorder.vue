<template>
  <div class="voice-recorder">
    <button 
      class="voice-btn"
      :class="{ 'recording': isRecording }"
      @click="toggleRecording"
      :title="isRecording ? '点击停止录音' : '点击开始录音'"
      :aria-pressed="isRecording"
    >
      <el-icon :size="16">
        <Microphone v-if="!isRecording" />
        <VideoPause v-else />
      </el-icon>
    </button>
    
    <!-- 录音状态提示 -->
    <div v-if="isRecording" class="recording-indicator">
      <span class="recording-text">正在听…</span>
      <span class="recording-time">{{ formatTime(recordingTime) }}</span>
      <span v-if="interimText" class="interim-text">{{ interimText }}</span>
    </div>
  </div>
</template>

<script setup>
import { ref, onUnmounted } from 'vue'
import { Microphone, VideoPause } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const emit = defineEmits(['voice-result'])

const isRecording = ref(false)
const recordingTime = ref(0)
const interimText = ref('')
let recognition = null
let recordingTimer = null

// 格式化时间显示
const formatTime = (seconds) => {
  const mins = Math.floor(seconds / 60)
  const secs = seconds % 60
  return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
}

// 开始录音
const startRecording = async () => {
  try {
    // 检查是否为安全上下文（HTTPS或localhost）
    if (!window.isSecureContext) {
      throw new Error('语音功能需要HTTPS环境，请确保网站使用HTTPS访问')
    }
    
    const Recognition = window.SpeechRecognition || window.webkitSpeechRecognition
    if (!Recognition) throw new Error('当前浏览器不支持实时语音转文字，请使用最新版 Chrome 或 Edge')

    recognition = new Recognition()
    recognition.lang = 'zh-CN'
    recognition.continuous = true
    recognition.interimResults = true
    recognition.maxAlternatives = 1
    recordingTime.value = 0
    interimText.value = ''
    let finalText = ''
    recognition.onresult = (event) => {
      let interim = ''
      for (let index = event.resultIndex; index < event.results.length; index += 1) {
        const transcript = event.results[index][0]?.transcript || ''
        if (event.results[index].isFinal) finalText += transcript
        else interim += transcript
      }
      interimText.value = interim
      if (finalText.trim()) {
        emit('voice-result', finalText.trim())
        finalText = ''
      }
    }
    recognition.onerror = (event) => {
      const messages = {
        'not-allowed': '麦克风权限被拒绝，请在浏览器地址栏中允许麦克风',
        'no-speech': '没有听到清晰语音，请靠近麦克风重试',
        network: '语音识别网络暂时不可用',
      }
      if (event.error !== 'aborted') ElMessage.error(messages[event.error] || `语音识别失败：${event.error}`)
      stopRecording()
    }
    recognition.onend = () => {
      if (isRecording.value) stopRecording()
    }

    recognition.start()
    isRecording.value = true
    
    // 开始计时
    recordingTimer = setInterval(() => {
      recordingTime.value++
      if (recordingTime.value >= 60) stopRecording()
    }, 1000)
    
    ElMessage.success('开始录音')
  } catch (error) {
    console.error('启动录音失败:', error)
    
    // 根据错误类型提供具体的解决方案
    if (error.name === 'NotAllowedError') {
      ElMessage.error('麦克风权限被拒绝，请在浏览器设置中允许麦克风访问')
    } else if (error.message.includes('HTTPS')) {
      ElMessage.error('语音功能需要HTTPS环境，请联系管理员配置SSL证书')
    } else {
      ElMessage.error(`无法访问麦克风：${error.message}`)
    }
  }
}

// 停止录音
const stopRecording = () => {
  try { recognition?.stop() } catch { /* noop */ }
  recognition = null
  
  if (recordingTimer) {
    clearInterval(recordingTimer)
    recordingTimer = null
  }
  
  isRecording.value = false
  interimText.value = ''
}

// 切换录音状态
const toggleRecording = () => {
  if (isRecording.value) {
    stopRecording()
  } else {
    startRecording()
  }
}

// 组件卸载时清理资源
onUnmounted(() => {
  if (isRecording.value) {
    stopRecording()
  }
  if (recordingTimer) {
    clearInterval(recordingTimer)
  }
})
</script>

<style scoped>
.voice-recorder {
  position: relative;
  display: inline-block;
}

.voice-btn {
  width: 44px;
  height: 44px;
  border: none;
  border-radius: 50%;
  background: var(--bg-tertiary);
  color: var(--text-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  flex-shrink: 0;
}

.voice-btn:hover {
  background: var(--bg-quaternary);
  color: var(--text-primary);
  transform: scale(1.05);
}

.voice-btn:active {
  transform: scale(0.95);
}

.voice-btn.recording {
  background: #ff4757;
  color: white;
  animation: pulse 1.5s infinite;
}

.voice-btn:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

@keyframes pulse {
  0% {
    transform: scale(1);
    box-shadow: 0 0 0 0 rgba(255, 71, 87, 0.7);
  }
  70% {
    transform: scale(1.05);
    box-shadow: 0 0 0 10px rgba(255, 71, 87, 0);
  }
  100% {
    transform: scale(1);
    box-shadow: 0 0 0 0 rgba(255, 71, 87, 0);
  }
}

.recording-indicator {
  position: absolute;
  top: -35px;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(0, 0, 0, 0.8);
  color: white;
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 12px;
  white-space: nowrap;
  z-index: 1000;
}

.interim-text { display:block;max-width:240px;margin-top:3px;overflow:hidden;color:#dce7ff;text-overflow:ellipsis;white-space:nowrap; }

.recording-indicator::after {
  content: '';
  position: absolute;
  top: 100%;
  left: 50%;
  transform: translateX(-50%);
  border: 4px solid transparent;
  border-top-color: rgba(0, 0, 0, 0.8);
}

.recording-text {
  margin-right: 8px;
}

.recording-time {
  font-weight: bold;
  color: #ff4757;
}

</style>
