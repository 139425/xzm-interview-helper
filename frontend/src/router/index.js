import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { readStoredAuth } from '@/utils/authSession'

// 路由组件
const Auth = () => import('@/views/Auth.vue')

// 路由配置
const routes = [
  {
    path: '/',
    redirect: '/chat'
  },
  {
    path: '/chat',
    name: 'Chat',
    component: () => import('@/views/ChatGemini.vue'),
    meta: {
      title: 'AI对话'
    }
  },
  {
    path: '/aiInterview',
    name: 'AiInterview',
    component: () => import('@/views/AiInterviewGemini.vue'),
    meta: {
      title: 'AI模拟面试'
    }
  },
  {
    path: '/interview-report',
    name: 'InterviewReport',
    component: () => import('@/views/InterviewReport.vue'),
    meta: {
      title: '面试报告'
    }
  },
  {
    path: '/algorithms',
    name: 'AlgorithmPractice',
    component: () => import('@/views/AlgorithmPractice.vue'),
    meta: {
      title: '算法训练'
    }
  },
  {
    path: '/recruitment',
    name: 'RecruitmentDirectory',
    component: () => import('@/views/RecruitmentDirectory.vue'),
    meta: {
      title: '求职信息 · 每日更新'
    }
  },
  {
    path: '/applications',
    name: 'ApplicationTracker',
    component: () => import('@/views/ApplicationTracker.vue'),
    meta: { title: '投递追踪' }
  },
  {
    path: '/knowledge',
    name: 'KnowledgeBase',
    component: () => import('@/views/KnowledgeBase.vue'),
    meta: { title: '个人资料' }
  },
  {
    path: '/login',
    name: 'Login',
    component: Auth,
    meta: {
      title: '用户登录',
      authMode: 'login'
    }
  },
  {
    path: '/register',
    name: 'Register',
    component: Auth,
    meta: {
      title: '用户注册',
      authMode: 'register'
    }
  },
  {
    path: '/admin/users',
    name: 'UserManagement',
    component: () => import('@/views/UserManagement.vue'),
    meta: {
      title: '用户管理',
      requiresAdmin: true
    }
  },
  {
    path: '/admin/server',
    name: 'ServerAgent',
    component: () => import('@/views/ServerAgent.vue'),
    meta: {
      title: '服务器 Agent',
      requiresAdmin: true
    }
  },
  // 404页面
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/NotFound.vue'),
    meta: {
      title: '页面未找到'
    }
  }
]

// 创建路由实例
const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    // 路由切换时的滚动行为
    if (savedPosition) {
      return savedPosition
    } else {
      return { top: 0 }
    }
  }
})

// 全局前置守卫
router.beforeEach((to, from, next) => {
  // 如果访问的是 .html 文件，直接让浏览器处理（跳转到静态文件）
  if (to.path.endsWith('.html')) {
    window.location.href = to.path
    return
  }
  
  // 设置页面标题
  if (to.meta.title) {
    document.title = to.meta.title
  }
  
  // 检查是否需要管理员权限
  if (to.meta.requiresAdmin) {
    const { token, userInfo } = readStoredAuth()
    if (!token || !userInfo) {
      next('/login')
      return
    }

    if (userInfo.userType !== '管理员') {
      ElMessage.error('权限不足：只有管理员才能访问此页面')
      next('/chat')
      return
    }

    next()
  } else {
    // 不需要特殊权限，正常导航
    next()
  }
})

// 全局后置钩子
router.afterEach((to, from) => {
  // 路由切换完成后的处理
  console.log(`路由从 ${from.path} 切换到 ${to.path}`)
})

// 路由错误处理
router.onError((error) => {
  console.error('路由错误:', error)
})

export default router
