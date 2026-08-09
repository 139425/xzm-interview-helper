<template>
  <div class="voice-recorder">
    <button 
      class="voice-btn"
      :class="{ 'recording': isRecording, 'processing': isProcessing }"
      @click="toggleRecording"
      :disabled="isProcessing"
      :title="isRecording ? '点击停止录音' : '点击开始录音'"
    >
      <el-icon :size="16">
        <Microphone v-if="!isRecording && !isProcessing" />
        <Loading v-else-if="isProcessing" />
        <VideoPlay v-else />
      </el-icon>
    </button>
    
    <!-- 录音状态提示 -->
    <div v-if="isRecording" class="recording-indicator">
      <span class="recording-text">录音中...</span>
      <span class="recording-time">{{ formatTime(recordingTime) }}</span>
    </div>
    
    <!-- 处理状态提示 -->
    <div v-if="isProcessing" class="processing-indicator">
      <span>正在识别语音...</span>
    </div>
  </div>
</template>

<script setup>
import { ref, onUnmounted } from 'vue'
import { Microphone, VideoPlay, Loading } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { chatApi } from '../api/chat'

const emit = defineEmits(['voice-result'])

const isRecording = ref(false)
const isProcessing = ref(false)
const recordingTime = ref(0)
let mediaRecorder = null
let audioChunks = []
let recordingTimer = null
let stream = null

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
    
    // 检查浏览器是否支持getUserMedia
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
      throw new Error('当前浏览器不支持语音录制功能')
    }
    
    // 请求麦克风权限
    stream = await navigator.mediaDevices.getUserMedia({ 
      audio: {
        sampleRate: 16000,
        channelCount: 1,
        echoCancellation: true,
        noiseSuppression: true
      } 
    })
    
    // 创建MediaRecorder实例
    mediaRecorder = new MediaRecorder(stream, {
      mimeType: 'audio/webm;codecs=opus'
    })
    
    audioChunks = []
    recordingTime.value = 0
    
    // 监听数据可用事件
    mediaRecorder.ondataavailable = (event) => {
      if (event.data.size > 0) {
        audioChunks.push(event.data)
      }
    }
    
    // 监听录音停止事件
    mediaRecorder.onstop = async () => {
      await processAudio()
    }
    
    // 开始录音
    mediaRecorder.start()
    isRecording.value = true
    
    // 开始计时
    recordingTimer = setInterval(() => {
      recordingTime.value++
      // 60秒自动停止
      if (recordingTime.value >= 60) {
        stopRecording()
      }
    }, 1000)
    
    ElMessage.success('开始录音')
  } catch (error) {
    console.error('启动录音失败:', error)
    
    // 根据错误类型提供具体的解决方案
    if (error.name === 'NotAllowedError') {
      ElMessage.error('麦克风权限被拒绝，请在浏览器设置中允许麦克风访问')
    } else if (error.name === 'NotFoundError') {
      ElMessage.error('未找到麦克风设备，请检查设备连接')
    } else if (error.name === 'NotSupportedError') {
      ElMessage.error('当前浏览器不支持语音录制功能')
    } else if (error.message.includes('HTTPS')) {
      ElMessage.error('语音功能需要HTTPS环境，请联系管理员配置SSL证书')
    } else {
      ElMessage.error(`无法访问麦克风：${error.message}`)
    }
  }
}

// 停止录音
const stopRecording = () => {
  if (mediaRecorder && mediaRecorder.state === 'recording') {
    mediaRecorder.stop()
  }
  
  if (stream) {
    stream.getTracks().forEach(track => track.stop())
    stream = null
  }
  
  if (recordingTimer) {
    clearInterval(recordingTimer)
    recordingTimer = null
  }
  
  isRecording.value = false
}

// 处理语音识别结果字符串
const parseVoiceResult = (rawResult) => {
  if (!rawResult || typeof rawResult !== 'string') {
    return ''
  }
  
  // 使用正则表达式匹配 [时间戳] 后面的文本内容
  // 匹配模式：[数字:数字.数字,数字:数字.数字] 后面的文本
  const regex = /\[\d+:\d+\.\d+,\d+:\d+\.\d+\]\s*([^\[]*)/g
  const matches = []
  let match
  
  while ((match = regex.exec(rawResult)) !== null) {
    const text = match[1].trim()
    if (text) {
      matches.push(text)
    }
  }
  
  // 将所有提取的文本用空格连接
  return matches.join(' ').trim()
}

// 处理音频数据
const processAudio = async () => {
  if (audioChunks.length === 0) {
    ElMessage.warning('录音数据为空')
    return
  }
  
  isProcessing.value = true
  
  try {
    // 创建音频Blob
    const audioBlob = new Blob(audioChunks, { type: 'audio/webm;codecs=opus' })
    
    // 转换为WAV格式（更适合语音识别）
    const wavBlob = await convertToWav(audioBlob)
    
    // 转换为Base64
    const base64Data = await blobToBase64(wavBlob)
    
    // 计算原始数据长度
    const dataLen = wavBlob.size
    
    // 调用语音识别API
    const rawResult = await chatApi.voiceRecognition(base64Data, dataLen)
    
    // 处理识别结果，提取纯文本
    const cleanText = parseVoiceResult(rawResult)
    
    if (cleanText) {
      // 发送处理后的识别结果
      emit('voice-result', cleanText)
      ElMessage.success('语音识别成功')
    } else {
      ElMessage.warning('未识别到有效内容')
    }
  } catch (error) {
    console.error('语音处理失败:', error)
    ElMessage.error('语音识别失败，请重试')
  } finally {
    isProcessing.value = false
    audioChunks = []
  }
}

// 将Blob转换为Base64
const blobToBase64 = (blob) => {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => {
      // 移除data:audio/wav;base64,前缀
      const base64 = reader.result.split(',')[1]
      resolve(base64)
    }
    reader.onerror = reject
    reader.readAsDataURL(blob)
  })
}

// 转换为WAV格式（简化版本，实际项目中可能需要更复杂的转换）
const convertToWav = async (webmBlob) => {
  // 这里简化处理，直接返回原始blob
  // 在实际项目中，可能需要使用Web Audio API进行格式转换
  return webmBlob
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

.voice-btn.processing {
  background: #3742fa;
  color: white;
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

.processing-indicator {
  position: absolute;
  top: -30px;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(55, 66, 250, 0.9);
  color: white;
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 12px;
  white-space: nowrap;
  z-index: 1000;
}

.processing-indicator::after {
  content: '';
  position: absolute;
  top: 100%;
  left: 50%;
  transform: translateX(-50%);
  border: 4px solid transparent;
  border-top-color: rgba(55, 66, 250, 0.9);
}
</style>