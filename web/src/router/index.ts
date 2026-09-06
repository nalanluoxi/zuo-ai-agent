import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '../components/layout/MainLayout.vue'
import ChatLayout from '../components/layout/ChatLayout.vue'
import LoginPage from '../views/login/LoginPage.vue'

// RAG 模块页面
import ChatPage from '../views/chat/ChatPage.vue'
import KnowledgeListPage from '../views/knowledge/KnowledgeListPage.vue'
import DashboardPage from '../views/dashboard/DashboardPage.vue'
import IntentManagePage from '../views/intent/IntentManagePage.vue'

// 监控模块页面
import RedisMonitorPage from '../views/monitor/RedisMonitorPage.vue'
import DbMonitorPage from '../views/monitor/DbMonitorPage.vue'
import LogsPage from '../views/monitor/LogsPage.vue'

// 管理模块页面
import AdminDashboardPage from '../views/manage/AdminDashboardPage.vue'
import TenantPage from '../views/manage/TenantPage.vue'
import ApprovalsPage from '../views/manage/ApprovalsPage.vue'
import TraceDetailPage from '../views/manage/TraceDetailPage.vue'
import RagLabPage from '../views/manage/RagLabPage.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: LoginPage },
    { path: '/dashboard', redirect: '/chat/dashboard' },
    { path: '/knowledge', redirect: '/chat/knowledge' },
    { path: '/intent', redirect: '/chat/intent' },
    {
      path: '/',
      component: MainLayout,
      redirect: '/chat',
      children: [
        // RAG 模块（含 ChatLayout 和 Sidebar）
        {
          path: 'chat',
          component: ChatLayout,
          children: [
            {
              path: '',
              component: ChatPage,
              meta: {
                requiresLogin: true,
                title: '对话聊天',
                page: 'chat'
              }
            },
            {
              path: 'knowledge',
              component: KnowledgeListPage,
              meta: {
                requiresLogin: true,
                title: '知识库',
                page: 'knowledge'
              }
            },
            {
              path: 'knowledge/:id',
              component: () => import('../views/knowledge/KnowledgeDetailPage.vue'),
              meta: {
                requiresLogin: true,
                title: '知识库详情',
                page: 'knowledge'
              }
            },
            {
              path: 'intent',
              component: IntentManagePage,
              meta: {
                requiresLogin: true,
                title: '意图管理',
                page: 'intent'
              }
            },
            {
              path: 'dashboard',
              component: DashboardPage,
              meta: {
                requiresLogin: true,
                title: '个人看板',
                page: 'dashboard'
              }
            }
          ]
        },

        // 监控模块（无 Sidebar）- 需要 SUPER_ADMIN 权限
        {
          path: 'monitor/redis',
          component: RedisMonitorPage,
          meta: { 
            requiresLogin: true,
            title: 'Redis 监控',
            page: 'monitor:redis'
          }
        },
        {
          path: 'monitor/db',
          component: DbMonitorPage,
          meta: { 
            requiresLogin: true,
            title: 'DB 监控',
            page: 'monitor:db'
          }
        },
        {
          path: 'monitor/logs',
          component: LogsPage,
          meta: { 
            requiresLogin: true,
            title: '日志系统',
            page: 'monitor:logs'
          }
        },

        // 管理模块 - 部分需要 SUPER_ADMIN 权限
        {
          path: 'manage/dashboard',
          component: AdminDashboardPage,
          meta: {
            requiresLogin: true,
            title: '全局看板',
            page: 'manage:dashboard'
          }
        },
        {
          path: 'manage/trace/:traceId',
          component: TraceDetailPage,
          meta: {
            requiresLogin: true,
            title: '链路详情',
            page: 'manage:trace'
          }
        },
        {
          path: 'manage/tenants',
          component: TenantPage,
          meta: { 
            requiresLogin: true,
            title: '租户管理',
            page: 'manage:tenants'
          }
        },
        {
          path: 'manage/approvals',
          component: ApprovalsPage,
          meta: { 
            requiresLogin: true,
            title: '审批中心',
            page: 'manage:approvals'
          }
        },
        {
          path: 'manage/rag-lab',
          component: RagLabPage,
          meta: { 
            requiresLogin: true,
            title: 'RAG 实验室',
            page: 'rag:lab'
          }
        }
      ]
    }
  ]
})

router.beforeEach((to) => {
  const token = localStorage.getItem('satoken')
  if (to.path !== '/login' && !token) {
    return '/login'
  }
})

export default router