import { ChatDotRound, Document, Cpu, Briefcase, TrendCharts, Calendar, Collection, Monitor, User } from '@element-plus/icons-vue'

export const workspaces = [
  { id: 'chat', label: 'AI 对话', shortLabel: '对话', description: '提问、整理与复盘', icon: ChatDotRound, route: '/chat', group: 'practice' },
  { id: 'interview', label: '模拟面试', shortLabel: '面试', description: '围绕简历进行实战演练', icon: Document, route: '/aiInterview', group: 'practice' },
  { id: 'algorithm', label: '算法训练', shortLabel: '算法', description: '题库、编码与评测', icon: Cpu, route: '/algorithms', group: 'practice' },
  { id: 'schedule', label: '笔面测待办', shortLabel: '待办', description: '下一场笔试、面试与测评', icon: Calendar, route: '/applications/schedule', group: 'career' },
  { id: 'applications', label: '投递追踪', shortLabel: '投递', description: '记录进度，推进下一步', icon: TrendCharts, route: '/applications', group: 'career' },
  { id: 'recruitment', label: '秋招信息', shortLabel: '秋招', description: '发现岗位与招聘机会', icon: Briefcase, route: '/recruitment', group: 'career' },
  { id: 'knowledge', label: '个人资料', description: '简历、项目与岗位资料', icon: Collection, route: '/knowledge' },
  { id: 'serverAgent', label: '服务器 Agent', description: '服务器工具与运行记录', icon: Monitor, route: '/admin/server', adminOnly: true },
  { id: 'users', label: '用户管理', description: '账号与权限', icon: User, route: '/admin/users', adminOnly: true },
]

const careerLoaders = {
  schedule: () => import('../views/AssessmentSchedule.vue'),
  applications: () => import('../views/ApplicationTracker.vue'),
  recruitment: () => import('../views/RecruitmentDirectory.vue'),
}
const pending = new Map()
// Fetch code only. Personal data continues to load in the owning page.
export function prefetchWorkspace(id) {
  if (!careerLoaders[id] || navigator.connection?.saveData) return
  if (!pending.has(id)) pending.set(id, careerLoaders[id]().catch(() => pending.delete(id)))
  return pending.get(id)
}
