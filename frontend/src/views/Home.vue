<template>
  <div class="home-container">

    
    <!-- 主要内容 -->
    <div class="main-content">
      <!-- 导航栏 -->
      <nav class="navbar">
        <div class="nav-brand">
          <h1 class="gradient-text">面试助手</h1>
        </div>
        <div class="nav-actions">
          <el-button type="primary" @click="goToChat">
            <i class="el-icon-chat-dot-round"></i>
            立即开始
          </el-button>
        </div>
      </nav>
      
      <!-- 欢迎区域 -->
      <div class="welcome-section">
        <div class="glass-panel welcome-panel">
          <div class="welcome-content">
            <h2 class="welcome-title">欢迎使用AI面试助手</h2>
            <p class="welcome-subtitle">智能AI助力您拿下offer</p>
            
            <div class="features-grid">
              <div class="feature-card glass-panel">
                <div class="feature-icon">🤖</div>
                <h3>AI智能对话</h3>
                <p>与AI进行模拟面试，面试前快速复习，查漏补缺</p>
              </div>
              
              <div class="feature-card glass-panel">
                <div class="feature-icon">📚</div>
                <h3>知识库丰富</h3>
                <p>涵盖Java面试常见问题和答案</p>
              </div>
              
              <div class="feature-card glass-panel">
                <div class="feature-icon">📊</div>
                <h3>回复迅速</h3>
                <p>在确保质量的基础上回复速度极快</p>
              </div>
              
              <div class="feature-card glass-panel">
                <div class="feature-icon">🎯</div>
                <h3>实时反馈</h3>
                <p>即时获得面试表现评估和指导</p>
              </div>
            </div>
            
            <div class="action-buttons">
              <el-button type="primary" size="large" @click="goToChat">
                立即开始面试练习
              </el-button>
            </div>
          </div>
        </div>
      </div>
      
      <!-- 页脚 -->
      <footer class="footer">
        <div class="footer-content">
          <p>&copy; 2025 xzm面试助手系统. 助力您的offer之路.</p>
        </div>
      </footer>
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
const goToChat = () => {
  router.push('/chat')
}
</script>

<style scoped>
.home-container {
  position: relative;
  min-height: 100vh;
  width: 100%;
  overflow-x: hidden;
}



/* 主要内容 */
.main-content {
  position: relative;
  z-index: 1;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

/* 导航栏 */
.navbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 40px;
  backdrop-filter: blur(10px);
  background: rgba(255, 255, 255, 0.9);
  border-bottom: 1px solid rgba(0, 0, 0, 0.1);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
}

.nav-brand h1 {
  font-size: 28px;
  font-weight: 700;
  margin: 0;
}

.nav-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

/* 欢迎区域 */
.welcome-section {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 40px 20px;
}

.welcome-panel {
  max-width: 1200px;
  width: 100%;
  padding: 60px 40px;
  text-align: center;
}

.welcome-content {
  max-width: 800px;
  margin: 0 auto;
}

.welcome-title {
  font-size: 48px;
  font-weight: 700;
  margin-bottom: 16px;
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.welcome-subtitle {
  font-size: 20px;
  color: rgba(51, 51, 51, 0.8);
  margin-bottom: 60px;
  line-height: 1.6;
}

/* 功能特性网格 */
.features-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24px;
  margin-bottom: 60px;
}

@media (max-width: 1200px) {
  .features-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {

}

.feature-card {
  padding: 32px 24px;
  text-align: center;
  transition: all 0.3s ease;
  cursor: pointer;
}

.feature-card:hover {
  transform: translateY(-8px);
  box-shadow: 0 12px 40px rgba(64, 158, 255, 0.2);
}

.feature-icon {
  font-size: 48px;
  margin-bottom: 16px;
  display: block;
}

.feature-card h3 {
  font-size: 20px;
  font-weight: 600;
  margin-bottom: 12px;
  color: #333333;
}

.feature-card p {
  font-size: 14px;
  color: rgba(51, 51, 51, 0.7);
  line-height: 1.6;
}

/* 操作按钮 */
.action-buttons {
  display: flex;
  gap: 16px;
  justify-content: center;
  flex-wrap: wrap;
}

.action-buttons .el-button {
  padding: 16px 32px;
  font-size: 16px;
  font-weight: 600;
}

/* 页脚 */
.footer {
  padding: 30px 40px;
  text-align: center;
  border-top: 1px solid rgba(0, 0, 0, 0.1);
  backdrop-filter: blur(10px);
  background: rgba(255, 255, 255, 0.9);
}

.footer-content p {
  color: rgba(51, 51, 51, 0.6);
  font-size: 14px;
  margin: 0;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .navbar {
    padding: 15px 20px;
    flex-direction: column;
    gap: 16px;
  }
  
  .nav-brand h1 {
    font-size: 24px;
  }
  
  .nav-actions {
    flex-wrap: wrap;
    justify-content: center;
  }
  
  .welcome-panel {
    padding: 40px 20px;
  }
  
  .welcome-title {
    font-size: 36px;
  }
  
  .welcome-subtitle {
    font-size: 18px;
  }
  
  .features-grid {
    grid-template-columns: 1fr;
    gap: 16px;
  }
  
  .action-buttons {
    flex-direction: column;
    align-items: center;
  }
  
  .action-buttons .el-button {
    width: 100%;
    max-width: 300px;
  }
}

@media (max-width: 480px) {
  .welcome-title {
    font-size: 28px;
  }
  
  .welcome-subtitle {
    font-size: 16px;
  }
  
  .feature-card {
    padding: 24px 16px;
  }
}
</style>