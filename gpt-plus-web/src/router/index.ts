import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

import { loadStoredAdminAuth } from '@/config/admin-auth'
import AdminLayoutView from '@/views/admin/AdminLayoutView.vue'
import AdminLoginView from '@/views/admin/AdminLoginView.vue'
import AdminPlaceholderView from '@/views/admin/AdminPlaceholderView.vue'
import ChatView from '@/views/ChatView.vue'

const adminRoutes: RouteRecordRaw[] = [
  {
    path: '/admin/login',
    name: 'admin-login',
    component: AdminLoginView,
    meta: {
      title: '管理员登录',
      description: '后台登录页契约骨架',
    },
  },
  {
    path: '/admin',
    component: AdminLayoutView,
    children: [
      {
        path: '',
        redirect: '/admin/dashboard',
      },
      {
        path: 'dashboard',
        name: 'admin-dashboard',
        component: AdminPlaceholderView,
        meta: {
          title: '工作台',
          description: '后台首页与运营概览将在后续逻辑接入阶段补齐。',
        },
      },
      {
        path: 'model/providers',
        name: 'admin-model-providers',
        component: AdminPlaceholderView,
        meta: {
          title: '模型供应商',
          description: '模型供应商分页、详情、新增、编辑和启停契约将在后续阶段接入。',
        },
      },
      {
        path: 'model/configs',
        name: 'admin-model-configs',
        component: AdminPlaceholderView,
        meta: {
          title: '模型配置',
          description: '模型配置分页、详情、新增、编辑和启停契约将在后续阶段接入。',
        },
      },
      {
        path: 'prompts',
        name: 'admin-prompts',
        component: AdminPlaceholderView,
        meta: {
          title: '提示词模板',
          description: '通用附加提示词模板管理页面将在后续阶段接入。',
        },
      },
      {
        path: 'users/admins',
        name: 'admin-users-admins',
        component: AdminPlaceholderView,
        meta: {
          title: '管理用户',
          description: '后台管理员列表、详情和状态维护页面将在后续阶段接入。',
        },
      },
      {
        path: 'users/apps',
        name: 'admin-users-apps',
        component: AdminPlaceholderView,
        meta: {
          title: '普通用户',
          description: '普通用户分页、详情和状态维护页面将在后续阶段接入。',
        },
      },
      {
        path: 'logs/api-calls',
        name: 'admin-logs-api-calls',
        component: AdminPlaceholderView,
        meta: {
          title: '调用日志',
          description: '模型调用日志分页和详情页面将在后续阶段接入。',
        },
      },
      {
        path: 'usage',
        name: 'admin-usage',
        component: AdminPlaceholderView,
        meta: {
          title: '用量统计',
          description: '按日期、用户和模型维度的用量统计页面将在后续阶段接入。',
        },
      },
    ],
  },
]

const routes: RouteRecordRaw[] = [
  // 用户侧聊天主入口保持不变，避免管理后台骨架影响现有聊天路由。
  {
    path: '/',
    redirect: '/chat',
  },
  {
    path: '/chat',
    name: 'chat',
    component: ChatView,
  },
  // 后台管理入口单独挂在 /admin/**，与用户侧聊天页面彻底隔离。
  ...adminRoutes,
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  // /admin/** 和用户侧聊天页面分离处理：后台优先看本地 Bearer token，聊天页仍沿用原有逻辑。
  if (!to.path.startsWith('/admin')) {
    return true
  }

  const storedAdminAuth = loadStoredAdminAuth()
  const hasAdminToken = Boolean(storedAdminAuth?.token)

  if (to.path === '/admin/login') {
    return hasAdminToken ? '/admin/dashboard' : true
  }

  return hasAdminToken ? true : '/admin/login'
})

export default router
