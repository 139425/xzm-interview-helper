<template>
  <div class="report-page" :class="{ 'mobile': isMobile }">
    <!-- 顶部导航栏 -->
    <header class="report-header">
      <button class="back-btn" @click="goBack">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M19 12H5M12 19l-7-7 7-7"/>
        </svg>
        <span>返回</span>
      </button>
      <div class="header-title">
        <span class="title-label">面试报告</span>
        <span class="title-date">{{ formatDate(reportData?.generatedAt) }}</span>
      </div>
      <button class="download-btn" @click="downloadReport">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
          <polyline points="7,10 12,15 17,10"/>
          <line x1="12" y1="15" x2="12" y2="3"/>
        </svg>
        <span>下载报告</span>
      </button>
    </header>

    <!-- 主内容区域 -->
    <main class="report-main">
      <!-- 报告封面区域 -->
      <section class="report-cover">
        <div class="cover-content">
          <div class="cover-badge">AI 模拟面试</div>
          <h1 class="cover-title">面试评估报告</h1>
          <p class="cover-subtitle">Interview Assessment Report</p>
          
          <!-- 总评分展示 -->
          <div class="total-score-display" v-if="averageScore > 0">
            <div class="score-ring">
              <svg viewBox="0 0 120 120">
                <circle class="score-bg" cx="60" cy="60" r="54" />
                <circle 
                  class="score-progress" 
                  cx="60" cy="60" r="54"
                  :style="{ strokeDashoffset: scoreOffset }"
                />
              </svg>
              <div class="score-inner">
                <span class="score-number">{{ averageScore.toFixed(1) }}</span>
                <span class="score-unit">分</span>
              </div>
            </div>
            <div class="score-info">
              <span class="score-label">综合评分</span>
              <span class="score-detail">{{ validScoreCount }} 轮有效评分</span>
            </div>
          </div>
          
          <div class="cover-meta">
            <div class="meta-item">
              <span class="meta-label">面试轮次</span>
              <span class="meta-value">{{ reportData?.rounds?.length || 0 }} 轮</span>
            </div>
            <div class="meta-divider"></div>
            <div class="meta-item">
              <span class="meta-label">生成时间</span>
              <span class="meta-value">{{ formatDateTime(reportData?.generatedAt) }}</span>
            </div>
          </div>
        </div>
        <div class="cover-decoration">
          <div class="deco-circle deco-1"></div>
          <div class="deco-circle deco-2"></div>
          <div class="deco-line"></div>
        </div>
      </section>

      <!-- AI 总结区域 -->
      <section class="summary-section" v-if="reportData?.summary">
        <div class="section-header">
          <div class="section-icon">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <path d="M12 16v-4M12 8h.01"/>
            </svg>
          </div>
          <h2 class="section-title">AI 综合评价</h2>
        </div>
        <div class="summary-card">
          <StaticMarkdown :content="reportData.summary" />
        </div>
      </section>

      <!-- 面试详情区域 -->
      <section class="rounds-section" v-if="reportData?.rounds?.length">
        <div class="section-header">
          <div class="section-icon">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
            </svg>
          </div>
          <h2 class="section-title">面试详情</h2>
          <button class="expand-all-btn" @click="toggleAllRounds">
            {{ allExpanded ? '全部收起' : '全部展开' }}
          </button>
        </div>

        <!-- 面试轮次卡片 -->
        <div 
          v-for="(round, index) in reportData.rounds" 
          :key="index" 
          class="round-card"
          :class="{ 'collapsed': !expandedRounds[index] }"
          :style="{ '--delay': `${index * 0.1}s` }"
        >
          <!-- 轮次标题（可点击折叠） -->
          <div class="round-header" @click="toggleRound(index)">
            <div class="round-number">
              <span class="number-text">{{ String(index + 1).padStart(2, '0') }}</span>
            </div>
            <div class="round-info">
              <span class="round-label">第 {{ index + 1 }} 轮</span>
              <span class="round-knowledge" v-if="round.knowledge">{{ round.knowledge }}</span>
            </div>
            <div class="round-score" v-if="round.score">
              <span class="score-value">{{ round.score }}</span>
              <span class="score-label">分</span>
            </div>
            <div class="expand-icon" :class="{ 'expanded': expandedRounds[index] }">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="6,9 12,15 18,9"/>
              </svg>
            </div>
          </div>

          <!-- 可折叠内容 -->
          <div class="round-content" v-show="expandedRounds[index]">
            <!-- 问题 -->
            <div class="qa-block question-block">
              <div class="qa-label">
                <span class="label-icon">Q</span>
                <span class="label-text">面试问题</span>
              </div>
              <div class="qa-content">{{ round.question }}</div>
            </div>

            <!-- 回答 -->
              <div class="qa-block answer-block">
                <div class="qa-label">
                  <span class="label-icon">A</span>
                  <span class="label-text">我的回答</span>
                </div>
                <div class="qa-content">{{ round.answer }}</div>
              </div>

            <!-- AI 评价 -->
            <div class="evaluation-block" v-if="round.evaluation">
              <div class="eval-header">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polygon points="12,2 15.09,8.26 22,9.27 17,14.14 18.18,21.02 12,17.77 5.82,21.02 7,14.14 2,9.27 8.91,8.26"/>
                </svg>
                <span>AI 评价</span>
              </div>
              <div class="eval-content">
                <div v-for="(section, sIndex) in parseEvaluation(round.evaluation)" :key="sIndex" class="eval-section" :class="section.type">
                  <div class="eval-section-header" :class="section.type">
                    <span class="eval-section-icon">{{ section.icon }}</span>
                    <span class="eval-section-title">{{ section.title }}</span>
                  </div>
                  <div class="eval-section-content">
                    <StaticMarkdown :content="section.content" />
                  </div>
                </div>
              </div>
            </div>

            <!-- 参考方向 -->
            <div class="reference-block" v-if="round.reference">
              <div class="ref-header">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z"/>
                  <path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z"/>
                </svg>
                <span>参考回答方向</span>
              </div>
              <div class="ref-content">
                <StaticMarkdown :content="normalizeNewlines(round.reference)" />
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- 底部操作区 -->
      <section class="actions-section">
        <button class="action-btn secondary" @click="goBack">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M19 12H5M12 19l-7-7 7-7"/>
          </svg>
          <span>返回面试</span>
        </button>
        <button class="action-btn primary" @click="downloadReport">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
            <polyline points="7,10 12,15 17,10"/>
            <line x1="12" y1="15" x2="12" y2="3"/>
          </svg>
          <span>下载报告</span>
        </button>
      </section>
    </main>

  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import StaticMarkdown from '../components/StaticMarkdown.vue'

const router = useRouter()
const reportData = ref(null)
const isMobile = ref(false)
const expandedRounds = reactive({})


// 检测移动端
const checkMobile = () => {
  isMobile.value = window.innerWidth <= 768
}

// 计算平均分
const averageScore = computed(() => {
  if (!reportData.value?.rounds?.length) return 0
  const scores = reportData.value.rounds
    .map(r => parseFloat(r.score))
    .filter(s => !isNaN(s) && s > 0)
  if (scores.length === 0) return 0
  return scores.reduce((a, b) => a + b, 0) / scores.length
})

// 有效评分数量
const validScoreCount = computed(() => {
  if (!reportData.value?.rounds?.length) return 0
  return reportData.value.rounds
    .map(r => parseFloat(r.score))
    .filter(s => !isNaN(s) && s > 0).length
})

// 计算环形进度条偏移量
const scoreOffset = computed(() => {
  const circumference = 2 * Math.PI * 54
  const progress = Math.min(averageScore.value / 10, 1)
  return circumference * (1 - progress)
})

// 是否全部展开
const allExpanded = computed(() => {
  if (!reportData.value?.rounds?.length) return false
  return reportData.value.rounds.every((_, i) => expandedRounds[i])
})

// 切换单个轮次
const toggleRound = (index) => {
  expandedRounds[index] = !expandedRounds[index]
}

// 切换全部
const toggleAllRounds = () => {
  const newState = !allExpanded.value
  reportData.value?.rounds?.forEach((_, i) => {
    expandedRounds[i] = newState
  })
}

// 初始化展开状态
const initExpandedState = () => {
  reportData.value?.rounds?.forEach((_, i) => {
    expandedRounds[i] = true
  })
}

// 格式化日期
const formatDate = (timestamp) => {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric'
  })
}

// 格式化日期时间
const formatDateTime = (timestamp) => {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const normalizeNewlines = (text) => {
  if (text === null || text === undefined) return ''
  const value = String(text)
  return value.replace(/\\r\\n/g, '\n').replace(/\\n/g, '\n').replace(/\r\n/g, '\n')
}

// 解析评价内容，分为优点、缺点、建议三部分
const parseEvaluation = (evaluation) => {
  if (!evaluation) return []
  
  const sections = []
  const text = evaluation.toString()
  
  const patterns = [
    { key: '优点', type: 'strength', icon: '✓', title: '优点' },
    { key: '缺点', type: 'weakness', icon: '✗', title: '缺点' },
    { key: '核心缺失点', type: 'core-missing', icon: '!', title: '核心缺失点' },
    { key: '建议', type: 'suggestion', icon: '→', title: '建议' }
  ]
  
  let hasKeywords = false
  patterns.forEach(pattern => {
    if (text.includes(pattern.key)) {
      hasKeywords = true
    }
  })
  
  if (hasKeywords) {
    const regex = /(优点|缺点|核心缺失点|建议)[：:]/g
    const parts = text.split(regex).filter(p => p.trim())
    
    let currentType = null
    for (let i = 0; i < parts.length; i++) {
      const part = parts[i].trim()
      const pattern = patterns.find(p => p.key === part)
      
      if (pattern) {
        currentType = pattern
      } else if (currentType && part) {
        sections.push({
          type: currentType.type,
          icon: currentType.icon,
          title: currentType.title,
          content: part.trim()
        })
      }
    }
  }
  
  if (sections.length === 0) {
    sections.push({
      type: 'general',
      icon: '•',
      title: '评价',
      content: text
    })
  }
  
  return sections
}

// 返回上一页
const goBack = () => {
  router.push('/aiInterview')
}

// 下载报告
const downloadReport = () => {
  if (!reportData.value) {
    ElMessage.warning('暂无报告数据')
    return
  }
  
  try {
    let markdown = `# 面试评估报告\n\n`
    markdown += `> 生成时间：${formatDateTime(reportData.value.generatedAt)}\n\n`
    
    if (averageScore.value > 0) {
      markdown += `## 综合评分：${averageScore.value.toFixed(1)} 分\n\n`
    }
    
    if (reportData.value.summary) {
      markdown += `## AI 综合评价\n\n${reportData.value.summary}\n\n`
    }
    
    markdown += `## 面试详情\n\n`
    reportData.value.rounds?.forEach((round, index) => {
      markdown += `### 第 ${index + 1} 轮`
      if (round.knowledge) markdown += ` - ${round.knowledge}`
      if (round.score) markdown += ` (${round.score}分)`
      markdown += `\n\n`
      
      markdown += `**问题：**\n${round.question}\n\n`
      markdown += `**回答：**\n${round.answer}\n\n`
      
      if (round.evaluation) {
        markdown += `**AI评价：**\n${round.evaluation}\n\n`
      }
      
      if (round.reference) {
        markdown += `**参考方向：**\n${round.reference}\n\n`
      }
      
      markdown += `---\n\n`
    })
    
    const blob = new Blob([markdown], { type: 'text/markdown;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `面试报告_${new Date().toISOString().split('T')[0]}.md`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
    
    ElMessage.success('报告下载成功')
  } catch (error) {
    console.error('下载报告失败:', error)
    ElMessage.error('下载失败，请重试')
  }
}

onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
  
  const storedData = sessionStorage.getItem('interviewReport')
  if (storedData) {
    try {
      reportData.value = JSON.parse(storedData)
      initExpandedState()
    } catch (error) {
      console.error('解析报告数据失败:', error)
      ElMessage.error('加载报告数据失败')
      router.push('/aiInterview')
    }
  } else {
    ElMessage.warning('未找到报告数据')
    router.push('/aiInterview')
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
})
</script>


<style scoped>
/* 字体导入 - 使用思源宋体和思源黑体，高质量且易读 */
@import url('https://fonts.googleapis.com/css2?family=Noto+Serif+SC:wght@400;500;600;700&family=Noto+Sans+SC:wght@300;400;500;600&display=swap');

/* 页面容器 */
.report-page {
  min-height: 100vh;
  background-color: var(--gemini-bg-primary);
  color: var(--gemini-text-primary);
  font-family: 'Noto Sans SC', -apple-system, BlinkMacSystemFont, sans-serif;
  line-height: 1.7;
  -webkit-font-smoothing: antialiased;
}

/* 顶部导航栏 */
.report-header {
  position: sticky;
  top: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 32px;
  background: var(--gemini-bg-primary);
  border-bottom: 1px solid var(--gemini-border-color);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
}

.back-btn,
.download-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 18px;
  border: 1px solid var(--gemini-border-color);
  border-radius: 24px;
  background: transparent;
  color: var(--gemini-text-primary);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.back-btn:hover,
.download-btn:hover {
  background: var(--gemini-bg-hover);
  border-color: var(--gemini-border-hover);
  transform: translateY(-1px);
}

.download-btn {
  background: var(--gemini-accent-blue);
  border-color: var(--gemini-accent-blue);
  color: #fff;
}

.download-btn:hover {
  background: var(--primary-hover);
  border-color: var(--primary-hover);
}

.header-title {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.title-label {
  font-family: 'Noto Serif SC', serif;
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 0.5px;
}

.title-date {
  font-size: 12px;
  color: var(--gemini-text-tertiary);
}

/* 主内容区域 */
.report-main {
  max-width: 860px;
  margin: 0 auto;
  padding: 40px 24px 80px;
}

/* 报告封面区域 */
.report-cover {
  position: relative;
  padding: 64px 48px;
  margin-bottom: 48px;
  background: linear-gradient(135deg, 
    var(--gemini-bg-secondary) 0%, 
    var(--gemini-bg-tertiary) 100%);
  border-radius: 24px;
  overflow: hidden;
  border: 1px solid var(--gemini-border-color);
}

.cover-content {
  position: relative;
  z-index: 2;
  text-align: center;
}

.cover-badge {
  display: inline-block;
  padding: 6px 16px;
  margin-bottom: 20px;
  background: var(--gemini-accent-blue);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 1px;
  border-radius: 20px;
  text-transform: uppercase;
}

.cover-title {
  font-family: 'Noto Serif SC', serif;
  font-size: 42px;
  font-weight: 700;
  margin-bottom: 8px;
  letter-spacing: 2px;
  background: linear-gradient(135deg, var(--gemini-text-primary) 0%, var(--gemini-text-secondary) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.cover-subtitle {
  font-size: 14px;
  color: var(--gemini-text-tertiary);
  letter-spacing: 3px;
  text-transform: uppercase;
  margin-bottom: 32px;
}

/* 总评分展示 */
.total-score-display {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 24px;
  margin-bottom: 32px;
  padding: 24px;
  background: var(--gemini-bg-primary);
  border-radius: 16px;
  border: 1px solid var(--gemini-border-color);
}

.score-ring {
  position: relative;
  width: 100px;
  height: 100px;
}

.score-ring svg {
  width: 100%;
  height: 100%;
  transform: rotate(-90deg);
}

.score-ring .score-bg {
  fill: none;
  stroke: var(--gemini-border-color);
  stroke-width: 8;
}

.score-ring .score-progress {
  fill: none;
  stroke: var(--gemini-accent-blue);
  stroke-width: 8;
  stroke-linecap: round;
  stroke-dasharray: 339.292;
  transition: stroke-dashoffset 1s cubic-bezier(0.4, 0, 0.2, 1);
}

.score-inner {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  display: flex;
  align-items: baseline;
  gap: 2px;
}

.score-number {
  font-family: 'Noto Serif SC', serif;
  font-size: 32px;
  font-weight: 700;
  color: var(--gemini-accent-blue);
}

.score-unit {
  font-size: 14px;
  color: var(--gemini-text-secondary);
}

.score-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.score-info .score-label {
  font-family: 'Noto Serif SC', serif;
  font-size: 18px;
  font-weight: 600;
  color: var(--gemini-text-primary);
}

.score-info .score-detail {
  font-size: 13px;
  color: var(--gemini-text-tertiary);
}

.cover-meta {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 32px;
}

.meta-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.meta-label {
  font-size: 12px;
  color: var(--gemini-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 1px;
}

.meta-value {
  font-family: 'Noto Serif SC', serif;
  font-size: 18px;
  font-weight: 600;
  color: var(--gemini-text-primary);
}

.meta-divider {
  width: 1px;
  height: 40px;
  background: var(--gemini-border-color);
}

/* 装饰元素 */
.cover-decoration {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  pointer-events: none;
  overflow: hidden;
}

.deco-circle {
  position: absolute;
  border-radius: 50%;
  opacity: 0.08;
}

.deco-1 {
  width: 300px;
  height: 300px;
  top: -100px;
  right: -80px;
  background: var(--gemini-accent-blue);
}

.deco-2 {
  width: 200px;
  height: 200px;
  bottom: -60px;
  left: -40px;
  background: var(--gemini-accent-green);
}

.deco-line {
  position: absolute;
  top: 50%;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(90deg, 
    transparent 0%, 
    var(--gemini-border-color) 20%, 
    var(--gemini-border-color) 80%, 
    transparent 100%);
  opacity: 0.5;
}

/* 区块标题 */
.section-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 2px solid var(--gemini-border-color);
}

.section-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  background: var(--gemini-bg-tertiary);
  border-radius: 10px;
  color: var(--gemini-accent-blue);
}

.section-title {
  font-family: 'Noto Serif SC', serif;
  font-size: 22px;
  font-weight: 600;
  color: var(--gemini-text-primary);
  flex: 1;
}

/* 展开/收起全部按钮 */
.expand-all-btn {
  padding: 8px 16px;
  border: 1px solid var(--gemini-border-color);
  border-radius: 20px;
  background: transparent;
  color: var(--gemini-text-secondary);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.expand-all-btn:hover {
  background: var(--gemini-bg-hover);
  color: var(--gemini-text-primary);
}

/* AI 总结卡片 */
.summary-section {
  margin-bottom: 48px;
}

.summary-card {
  padding: 32px;
  background: var(--gemini-bg-secondary);
  border-radius: 16px;
  border: 1px solid var(--gemini-border-color);
  font-size: 15px;
  line-height: 1.8;
}

.summary-card :deep(p) {
  margin-bottom: 16px;
}

.summary-card :deep(p:last-child) {
  margin-bottom: 0;
}


/* 面试轮次区域 */
.rounds-section {
  margin-bottom: 48px;
}

/* 轮次卡片 */
.round-card {
  margin-bottom: 20px;
  background: var(--gemini-bg-secondary);
  border-radius: 16px;
  border: 1px solid var(--gemini-border-color);
  overflow: hidden;
  animation: slideUp 0.5s cubic-bezier(0.4, 0, 0.2, 1) forwards;
  animation-delay: var(--delay, 0s);
  opacity: 0;
  transform: translateY(20px);
  transition: box-shadow 0.3s ease, border-color 0.3s ease;
}

.round-card:hover {
  border-color: var(--gemini-border-hover);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
}

@keyframes slideUp {
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 轮次标题 */
.round-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px 24px;
  cursor: pointer;
  transition: background 0.2s ease;
}

.round-header:hover {
  background: var(--gemini-bg-hover);
}

.round-number {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  background: linear-gradient(135deg, var(--gemini-accent-blue) 0%, #6366f1 100%);
  border-radius: 12px;
  flex-shrink: 0;
}

.number-text {
  font-family: 'Noto Serif SC', serif;
  font-size: 18px;
  font-weight: 700;
  color: #fff;
}

.round-info {
  flex: 1;
  min-width: 0;
}

.round-label {
  display: block;
  font-family: 'Noto Serif SC', serif;
  font-size: 16px;
  font-weight: 600;
  color: var(--gemini-text-primary);
  margin-bottom: 2px;
}

.round-knowledge {
  display: block;
  font-size: 13px;
  color: var(--gemini-text-tertiary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.round-score {
  display: flex;
  align-items: baseline;
  gap: 2px;
  padding: 8px 16px;
  background: var(--gemini-bg-tertiary);
  border-radius: 20px;
}

.round-score .score-value {
  font-family: 'Noto Serif SC', serif;
  font-size: 20px;
  font-weight: 700;
  color: var(--gemini-accent-blue);
}

.round-score .score-label {
  font-size: 12px;
  color: var(--gemini-text-tertiary);
}

/* 展开/收起图标 */
.expand-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  color: var(--gemini-text-tertiary);
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.expand-icon.expanded {
  transform: rotate(180deg);
}

/* 可折叠内容 */
.round-content {
  padding: 0 24px 24px;
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

/* 问答区块 */
.qa-block {
  margin-bottom: 20px;
  padding: 20px;
  background: var(--gemini-bg-primary);
  border-radius: 12px;
  border: 1px solid var(--gemini-border-color);
}

.qa-label {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.label-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  font-family: 'Noto Serif SC', serif;
  font-size: 14px;
  font-weight: 700;
  border-radius: 8px;
}

.question-block .label-icon {
  background: rgba(99, 102, 241, 0.15);
  color: #6366f1;
}

.answer-block .label-icon {
  background: rgba(34, 197, 94, 0.15);
  color: #22c55e;
}

.label-text {
  font-size: 13px;
  font-weight: 600;
  color: var(--gemini-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.qa-content {
  font-size: 15px;
  line-height: 1.8;
  color: var(--gemini-text-primary);
  white-space: pre-wrap;
  word-break: break-word;
}

/* AI 评价区块 */
.evaluation-block {
  margin-bottom: 20px;
  padding: 20px;
  background: var(--gemini-bg-primary);
  border-radius: 12px;
  border: 1px solid var(--gemini-border-color);
}

.eval-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--gemini-border-color);
  font-size: 14px;
  font-weight: 600;
  color: var(--gemini-accent-yellow);
}

.eval-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.eval-section {
  padding: 16px;
  background: var(--gemini-bg-secondary);
  border-radius: 10px;
  border-left: 3px solid var(--gemini-border-color);
}

.eval-section.strength {
  border-left-color: #22c55e;
}

.eval-section.weakness {
  border-left-color: #ef4444;
}

.eval-section.suggestion {
  border-left-color: #3b82f6;
}

.eval-section.core-missing {
  border-left-color: #f97316;
}

.eval-section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.eval-section-icon {
  font-size: 14px;
  font-weight: 700;
}

.eval-section-header.strength .eval-section-icon {
  color: #22c55e;
}

.eval-section-header.weakness .eval-section-icon {
  color: #ef4444;
}

.eval-section-header.suggestion .eval-section-icon {
  color: #3b82f6;
}

.eval-section-header.core-missing .eval-section-icon {
  color: #f97316;
}

.eval-section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--gemini-text-primary);
}

.eval-section-content {
  font-size: 14px;
  line-height: 1.7;
  color: var(--gemini-text-secondary);
}

.eval-section-content :deep(p) {
  margin-bottom: 8px;
}

.eval-section-content :deep(p:last-child) {
  margin-bottom: 0;
}

/* 参考方向区块 */
.reference-block {
  padding: 20px;
  background: linear-gradient(135deg, 
    rgba(59, 130, 246, 0.08) 0%, 
    rgba(99, 102, 241, 0.08) 100%);
  border-radius: 12px;
  border: 1px solid rgba(59, 130, 246, 0.2);
}

.ref-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  font-size: 13px;
  font-weight: 600;
  color: var(--gemini-accent-blue);
}

.ref-content {
  font-size: 14px;
  line-height: 1.7;
  color: var(--gemini-text-secondary);
}

.ref-content :deep(p) {
  margin-bottom: 8px;
}

.ref-content :deep(p:last-child) {
  margin-bottom: 0;
}


/* 底部操作区 */
.actions-section {
  display: flex;
  justify-content: center;
  gap: 16px;
  padding: 32px 0;
  border-top: 1px solid var(--gemini-border-color);
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 28px;
  border-radius: 28px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.action-btn.secondary {
  background: transparent;
  border: 1px solid var(--gemini-border-color);
  color: var(--gemini-text-primary);
}

.action-btn.secondary:hover {
  background: var(--gemini-bg-hover);
  border-color: var(--gemini-border-hover);
  transform: translateY(-2px);
}

.action-btn.primary {
  background: var(--gemini-accent-blue);
  border: 1px solid var(--gemini-accent-blue);
  color: #fff;
}

.action-btn.primary:hover {
  background: var(--primary-hover);
  border-color: var(--primary-hover);
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(59, 130, 246, 0.3);
}

/* ========================================
   移动端适配
   ======================================== */
@media (max-width: 768px) {
  .report-header {
    padding: 12px 16px;
  }

  .back-btn span,
  .download-btn span {
    display: none;
  }

  .back-btn,
  .download-btn {
    padding: 10px;
    border-radius: 50%;
  }

  .header-title {
    flex: 1;
  }

  .title-label {
    font-size: 16px;
  }

  .report-main {
    padding: 24px 16px 60px;
  }

  .report-cover {
    padding: 40px 24px;
    margin-bottom: 32px;
    border-radius: 16px;
  }

  .cover-title {
    font-size: 28px;
    letter-spacing: 1px;
  }

  .cover-subtitle {
    font-size: 12px;
    letter-spacing: 2px;
    margin-bottom: 24px;
  }

  .total-score-display {
    flex-direction: column;
    gap: 16px;
    padding: 20px;
  }

  .score-ring {
    width: 90px;
    height: 90px;
  }

  .score-number {
    font-size: 28px;
  }

  .score-info {
    align-items: center;
    text-align: center;
  }

  .cover-meta {
    flex-direction: column;
    gap: 16px;
  }

  .meta-divider {
    width: 60px;
    height: 1px;
  }

  .section-header {
    flex-wrap: wrap;
    gap: 10px;
  }

  .section-icon {
    width: 36px;
    height: 36px;
  }

  .section-title {
    font-size: 18px;
    flex: 1;
    min-width: 150px;
  }

  .expand-all-btn {
    width: 100%;
    margin-top: 8px;
    order: 3;
  }

  .summary-card {
    padding: 20px;
    border-radius: 12px;
  }

  .round-header {
    padding: 16px;
    gap: 12px;
  }

  .round-number {
    width: 40px;
    height: 40px;
    border-radius: 10px;
  }

  .number-text {
    font-size: 16px;
  }

  .round-label {
    font-size: 15px;
  }

  .round-score {
    padding: 6px 12px;
  }

  .round-score .score-value {
    font-size: 18px;
  }

  .round-content {
    padding: 0 16px 16px;
  }

  .qa-block {
    padding: 16px;
    margin-bottom: 16px;
  }

  .qa-content {
    font-size: 14px;
    line-height: 1.7;
  }

  .evaluation-block {
    padding: 16px;
  }

  .eval-section {
    padding: 14px;
  }

  .reference-block {
    padding: 16px;
  }

  .actions-section {
    flex-direction: column;
    padding: 24px 0;
  }

  .action-btn {
    width: 100%;
    justify-content: center;
    padding: 14px 24px;
  }
}

/* 超小屏幕适配 */
@media (max-width: 480px) {
  .report-main {
    padding: 16px 12px 48px;
  }

  .report-cover {
    padding: 32px 16px;
  }

  .cover-title {
    font-size: 24px;
  }

  .cover-badge {
    font-size: 11px;
    padding: 5px 12px;
  }

  .score-ring {
    width: 80px;
    height: 80px;
  }

  .score-number {
    font-size: 24px;
  }

  .meta-value {
    font-size: 16px;
  }

  .round-header {
    padding: 14px 12px;
  }

  .round-number {
    width: 36px;
    height: 36px;
  }

  .number-text {
    font-size: 14px;
  }

  .round-content {
    padding: 0 12px 12px;
  }

  .qa-block,
  .evaluation-block,
  .reference-block {
    padding: 14px;
    border-radius: 10px;
  }

  .qa-content,
  .eval-section-content,
  .ref-content {
    font-size: 14px;
  }
}

/* ========================================
   亮色主题适配
   ======================================== */
[data-theme="light"] .report-page {
  background-color: var(--xzm-surface-0);
}

[data-theme="light"] .report-header {
  background: color-mix(in srgb, var(--xzm-surface-elevated) 95%, transparent);
  border-bottom-color: var(--xzm-border-color);
}

[data-theme="light"] .back-btn,
[data-theme="light"] .action-btn.secondary {
  border-color: #e2e8f0;
  color: #1e293b;
}

[data-theme="light"] .back-btn:hover,
[data-theme="light"] .action-btn.secondary:hover {
  background: #f1f5f9;
  border-color: #cbd5e1;
}

[data-theme="light"] .report-cover {
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  border-color: #e2e8f0;
}

[data-theme="light"] .cover-title {
  background: linear-gradient(135deg, #1e293b 0%, #475569 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

[data-theme="light"] .total-score-display {
  background: var(--xzm-surface-elevated);
  border-color: var(--xzm-border-color);
}

[data-theme="light"] .score-ring .score-bg {
  stroke: #e2e8f0;
}

[data-theme="light"] .summary-card,
[data-theme="light"] .round-card {
  background: var(--xzm-surface-elevated);
  border-color: var(--xzm-border-color);
}

.report-page :deep(.markdown-content) {
  background-color: transparent !important;
}

.report-page :deep(.markdown-content pre),
.report-page :deep(.markdown-content pre code),
.report-page :deep(.markdown-content .hljs) {
  background-color: transparent !important;
}

.report-page :deep(.markdown-content pre) {
  border-color: var(--gemini-border-color) !important;
}

.report-page :deep(.markdown-renderer-root.markdown-content code:not(pre code)) {
  background-color: transparent !important;
  color: inherit !important;
  border: none !important;
  padding: 0 !important;
  border-radius: 0 !important;
  font-size: inherit !important;
}

.report-page :deep(.markdown-content blockquote),
.report-page :deep(.markdown-content table),
.report-page :deep(.markdown-content th),
.report-page :deep(.markdown-content td) {
  background-color: transparent !important;
}

.report-page :deep(.markdown-content .code-copy-btn) {
  background: var(--gemini-bg-secondary) !important;
  border-color: var(--gemini-border-color) !important;
  color: var(--gemini-text-secondary) !important;
}

[data-theme="light"] .round-card:hover {
  border-color: #cbd5e1;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.06);
}

[data-theme="light"] .round-header:hover {
  background: #f1f5f9;
}

[data-theme="light"] .round-score {
  background: #e2e8f0;
}

[data-theme="light"] .qa-block,
[data-theme="light"] .evaluation-block {
  background: var(--xzm-surface-elevated);
  border-color: var(--xzm-border-color);
}

[data-theme="light"] .eval-section {
  background: var(--xzm-surface-inset);
}

[data-theme="light"] .reference-block {
  background: linear-gradient(135deg, 
    rgba(59, 130, 246, 0.06) 0%, 
    rgba(99, 102, 241, 0.06) 100%);
  border-color: rgba(59, 130, 246, 0.15);
}

[data-theme="light"] .section-header {
  border-bottom-color: #e2e8f0;
}

[data-theme="light"] .section-icon {
  background: #f1f5f9;
}

[data-theme="light"] .expand-all-btn {
  border-color: #e2e8f0;
  color: #64748b;
}

[data-theme="light"] .expand-all-btn:hover {
  background: #f1f5f9;
  color: #1e293b;
}

[data-theme="light"] .actions-section {
  border-top-color: #e2e8f0;
}

[data-theme="light"] .eval-header {
  border-bottom-color: #e2e8f0;
}

[data-theme="light"] .meta-divider {
  background: #e2e8f0;
}

[data-theme="light"] .deco-line {
  background: linear-gradient(90deg, 
    transparent 0%, 
    #e2e8f0 20%, 
    #e2e8f0 80%, 
    transparent 100%);
}
</style>
