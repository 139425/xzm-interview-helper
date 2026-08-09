<template>
  <div class="not-found-container">
    <!-- 星空背景 -->
    <div class="stars-background">
      <div class="star" v-for="n in 50" :key="n" :style="getStarStyle()"></div>
    </div>
    
    <!-- 404内容 -->
    <div class="not-found-content">
      <div class="glass-panel error-panel">
        <div class="error-animation">
          <div class="error-number">4</div>
          <div class="error-planet">🪐</div>
          <div class="error-number">4</div>
        </div>
        
        <h1 class="error-title">页面走丢了</h1>
        <p class="error-message">
          抱歉，您访问的页面似乎在宇宙中迷失了方向...
        </p>
        
        <div class="error-actions">
          <el-button type="primary" size="large" @click="goHome">
            <i class="el-icon-house"></i>
            返回首页
          </el-button>
          
          <el-button size="large" @click="goBack">
            <i class="el-icon-back"></i>
            返回上页
          </el-button>
        </div>
        
        <div class="helpful-links">
          <h3>您可能想要访问：</h3>
          <div class="links-grid">
            <router-link to="/" class="link-item">
              <i class="el-icon-house"></i>
              首页
            </router-link>
            
            <router-link to="/chat" class="link-item">
              <i class="el-icon-chat-dot-round"></i>
              AI对话
            </router-link>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()



// 生成随机星星样式
const getStarStyle = () => {
  return {
    left: Math.random() * 100 + '%',
    top: Math.random() * 100 + '%',
    animationDelay: Math.random() * 3 + 's',
    animationDuration: (Math.random() * 2 + 1) + 's'
  }
}

// 导航方法
const goHome = () => {
  router.push('/')
}

const goBack = () => {
  if (window.history.length > 1) {
    router.go(-1)
  } else {
    router.push('/')
  }
}
</script>

<style scoped>
.not-found-container {
  position: relative;
  min-height: 100vh;
  width: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
  overflow: hidden;
}

/* 星空背景 */
.stars-background {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: -1;
}

.star {
  position: absolute;
  width: 2px;
  height: 2px;
  background: white;
  border-radius: 50%;
  animation: twinkle infinite;
}

/* 404内容 */
.not-found-content {
  position: relative;
  z-index: 1;
  padding: 20px;
  width: 100%;
  max-width: 600px;
}

.error-panel {
  padding: 60px 40px;
  text-align: center;
}

/* 错误动画 */
.error-animation {
  display: flex;
  justify-content: center;
  align-items: center;
  margin-bottom: 40px;
  font-size: 120px;
  font-weight: 700;
}

.error-number {
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  animation: bounce 2s infinite;
}

.error-planet {
  margin: 0 20px;
  animation: float 3s ease-in-out infinite;
  filter: drop-shadow(0 0 20px rgba(64, 158, 255, 0.5));
}

.error-title {
  font-size: 36px;
  font-weight: 600;
  margin-bottom: 16px;
  color: #ffffff;
}

.error-message {
  font-size: 18px;
  color: rgba(255, 255, 255, 0.8);
  margin-bottom: 40px;
  line-height: 1.6;
}

/* 操作按钮 */
.error-actions {
  display: flex;
  gap: 16px;
  justify-content: center;
  margin-bottom: 50px;
  flex-wrap: wrap;
}

.error-actions .el-button {
  padding: 16px 32px;
  font-size: 16px;
  font-weight: 600;
}

/* 有用链接 */
.helpful-links {
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  padding-top: 30px;
}

.helpful-links h3 {
  font-size: 18px;
  color: rgba(255, 255, 255, 0.9);
  margin-bottom: 20px;
}

.links-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 12px;
}

.link-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 16px 12px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  color: rgba(255, 255, 255, 0.8);
  text-decoration: none;
  transition: all 0.3s ease;
  font-size: 14px;
}

.link-item:hover {
  background: rgba(64, 158, 255, 0.1);
  border-color: rgba(64, 158, 255, 0.3);
  color: #409eff;
  transform: translateY(-2px);
}

.link-item i {
  font-size: 20px;
  margin-bottom: 8px;
}

/* 动画效果 */
@keyframes bounce {
  0%, 20%, 50%, 80%, 100% {
    transform: translateY(0);
  }
  40% {
    transform: translateY(-10px);
  }
  60% {
    transform: translateY(-5px);
  }
}

@keyframes float {
  0%, 100% {
    transform: translateY(0px) rotate(0deg);
  }
  50% {
    transform: translateY(-20px) rotate(180deg);
  }
}

/* 响应式设计 */
@media (max-width: 768px) {
  .error-panel {
    padding: 40px 20px;
  }
  
  .error-animation {
    font-size: 80px;
  }
  
  .error-title {
    font-size: 28px;
  }
  
  .error-message {
    font-size: 16px;
  }
  
  .error-actions {
    flex-direction: column;
    align-items: center;
  }
  
  .error-actions .el-button {
    width: 100%;
    max-width: 250px;
  }
  
  .links-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 480px) {
  .error-animation {
    font-size: 60px;
  }
  
  .error-planet {
    margin: 0 10px;
  }
  
  .error-title {
    font-size: 24px;
  }
  
  .error-message {
    font-size: 14px;
  }
  
  .links-grid {
    grid-template-columns: 1fr;
  }
}
</style>