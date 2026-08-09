<template>
  <div class="thinking-test-container">
    <div class="test-header">
      <h2>思考框功能测试</h2>
      <p>这个页面用于测试推理模型的思考过程展示功能</p>
    </div>
    
    <div class="test-controls">
      <el-button @click="startThinkingDemo" :disabled="isDemo" type="primary">
        {{ isDemo ? '演示进行中...' : '开始思考演示' }}
      </el-button>
      <el-button @click="resetDemo" :disabled="isDemo">
        重置演示
      </el-button>
    </div>
    
    <div class="messages-container">
      <!-- 用户消息 -->
      <MessageItem
        role="user"
        content="请解释一下什么是机器学习？"
      />
      
      <!-- AI回复消息（带思考过程） -->
      <MessageItem
        v-if="demoMessage"
        role="assistant"
        :content="demoMessage"
        :is-streaming="isDemo"
      />
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import MessageItem from '../components/MessageItem.vue'

const isDemo = ref(false)
const demoMessage = ref('')

// 模拟的思考内容和回复内容
const fullDemoContent = `<think>让我思考一下如何最好地解释机器学习这个概念...

首先，我需要从基础概念开始，让用户理解什么是机器学习。

机器学习是人工智能的一个分支，它让计算机能够从数据中学习模式，而不需要明确编程。

我应该提到几个关键点：
1. 数据驱动的方法
2. 模式识别
3. 预测能力
4. 自动化决策

让我组织一个清晰的回答，包含定义、工作原理和实际应用例子。</think>

# 机器学习简介

机器学习（Machine Learning）是人工智能的一个重要分支，它使计算机系统能够通过经验自动改进性能，而无需进行明确的编程。

## 核心概念

**数据驱动学习**：机器学习算法通过分析大量数据来识别模式和规律，从而做出预测或决策。

## 主要类型

1. **监督学习**：使用标记数据训练模型
2. **无监督学习**：从未标记数据中发现隐藏模式
3. **强化学习**：通过与环境交互来学习最优策略

## 实际应用

- 🔍 **搜索引擎**：改进搜索结果排序
- 📧 **邮件过滤**：自动识别垃圾邮件
- 🛒 **推荐系统**：个性化商品推荐
- 🚗 **自动驾驶**：路径规划和障碍物识别

机器学习正在改变我们与技术交互的方式，为各行各业带来创新解决方案。`

// 开始思考演示
const startThinkingDemo = () => {
  isDemo.value = true
  demoMessage.value = ''
  
  // 模拟流式输出
  let currentIndex = 0
  const streamInterval = setInterval(() => {
    if (currentIndex < fullDemoContent.length) {
      demoMessage.value = fullDemoContent.substring(0, currentIndex + 1)
      currentIndex += Math.floor(Math.random() * 3) + 1 // 随机速度
    } else {
      clearInterval(streamInterval)
      isDemo.value = false
    }
  }, 50) // 每50ms更新一次
}

// 重置演示
const resetDemo = () => {
  isDemo.value = false
  demoMessage.value = ''
}
</script>

<style scoped>
.thinking-test-container {
  max-width: 800px;
  margin: 0 auto;
  padding: 2rem;
  background-color: #f8fafc;
  min-height: 100vh;
}

.test-header {
  text-align: center;
  margin-bottom: 2rem;
  padding: 1.5rem;
  background-color: white;
  border-radius: 1rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.test-header h2 {
  color: #1e293b;
  margin-bottom: 0.5rem;
}

.test-header p {
  color: #64748b;
  margin: 0;
}

.test-controls {
  display: flex;
  justify-content: center;
  gap: 1rem;
  margin-bottom: 2rem;
}

.messages-container {
  background-color: white;
  border-radius: 1rem;
  padding: 1.5rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.messages-container :deep(.message-item) {
  margin-bottom: 1.5rem;
}

.messages-container :deep(.message-item:last-child) {
  margin-bottom: 0;
}
</style>