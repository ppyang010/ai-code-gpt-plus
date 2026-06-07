<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useAdminAuthStore } from '@/stores/admin-auth'

interface AdminNavItem {
  /** 导航唯一键，后续接权限控制时也可复用。 */
  key: string
  /** 导航展示名称。 */
  label: string
  /** 导航对应路由。 */
  to: string
}

const route = useRoute()
const router = useRouter()
const adminAuthStore = useAdminAuthStore()
const navItems: AdminNavItem[] = [
  { key: 'dashboard', label: '工作台', to: '/admin/dashboard' },
  { key: 'model-providers', label: '模型供应商', to: '/admin/model/providers' },
  { key: 'model-configs', label: '模型配置', to: '/admin/model/configs' },
  { key: 'prompts', label: '提示词模板', to: '/admin/prompts' },
  { key: 'admin-users', label: '管理用户', to: '/admin/users/admins' },
  { key: 'app-users', label: '普通用户', to: '/admin/users/apps' },
  { key: 'api-call-logs', label: '调用日志', to: '/admin/logs/api-calls' },
  { key: 'usage', label: '用量统计', to: '/admin/usage' },
]

/** 后台顶部标题直接复用路由元信息，后续接真实页面时不用重复维护一套标题映射。 */
const currentPageTitle = computed(() => String(route.meta.title || '管理后台'))
/** 顶部管理员信息优先展示昵称，退化时回落到用户名。 */
const currentAdminDisplayName = computed(() => adminAuthStore.displayName)

/** 侧边导航按前缀匹配高亮，兼容后续列表页下再挂详情页的场景。 */
function isActiveNav(targetPath: string) {
  return route.path === targetPath || route.path.startsWith(`${targetPath}/`)
}

/** 当前阶段只需要固定路由骨架，因此点击导航时直接切路由，不附带额外业务判断。 */
function handleNavigate(targetPath: string) {
  if (route.path === targetPath) {
    return
  }
  void router.push(targetPath)
}

/**
 * 进入后台壳子时主动刷新一次当前管理员信息；若 token 过期，则清理本地状态并回到登录页。
 */
async function restoreCurrentAdmin() {
  adminAuthStore.restoreStoredAuth()
  if (!adminAuthStore.isAuthenticated) {
    return
  }

  try {
    await adminAuthStore.fetchCurrentAdmin()
  } catch {
    void router.replace('/admin/login')
  }
}

/**
 * 退出登录后统一清理本地后台登录态，并回到后台登录页。
 */
async function handleLogout() {
  await adminAuthStore.logout()
  void router.replace('/admin/login')
}

onMounted(() => {
  void restoreCurrentAdmin()
})
</script>

<template>
  <div class="admin-shell">
    <aside class="admin-shell__sidebar">
      <div class="admin-shell__brand">
        <div class="admin-shell__brand-title">GPT Plus Admin</div>
        <div class="admin-shell__brand-subtitle">运营配置后台</div>
      </div>
      <nav class="admin-shell__nav">
        <button
          v-for="item in navItems"
          :key="item.key"
          class="admin-shell__nav-item"
          :class="{ 'admin-shell__nav-item--active': isActiveNav(item.to) }"
          type="button"
          @click="handleNavigate(item.to)"
        >
          {{ item.label }}
        </button>
      </nav>
    </aside>
    <main class="admin-shell__main">
      <header class="admin-shell__header">
        <h1 class="admin-shell__title">{{ currentPageTitle }}</h1>
        <div class="admin-shell__header-actions">
          <div class="admin-shell__status">{{ currentAdminDisplayName }}</div>
          <t-button variant="outline" theme="default" @click="handleLogout">退出登录</t-button>
        </div>
      </header>
      <section class="admin-shell__content">
        <RouterView />
      </section>
    </main>
  </div>
</template>

<style scoped lang="scss">
.admin-shell {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  background: #f5f7fa;
}

.admin-shell__sidebar {
  display: flex;
  flex-direction: column;
  gap: 24px;
  padding: 24px 18px;
  background: #111827;
  color: #f9fafb;
}

.admin-shell__brand-title {
  font-size: 18px;
  font-weight: 600;
}

.admin-shell__brand-subtitle {
  margin-top: 6px;
  font-size: 13px;
  color: rgba(249, 250, 251, 0.7);
}

.admin-shell__nav {
  display: grid;
  gap: 8px;
}

.admin-shell__nav-item {
  width: 100%;
  min-height: 40px;
  padding: 0 12px;
  border: 0;
  border-radius: 8px;
  text-align: left;
  font-size: 14px;
  color: rgba(249, 250, 251, 0.82);
  background: transparent;
  cursor: pointer;
}

.admin-shell__nav-item--active {
  color: #ffffff;
  background: rgba(255, 255, 255, 0.12);
}

.admin-shell__main {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.admin-shell__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24px 28px 16px;
}

.admin-shell__header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.admin-shell__title {
  margin: 0;
  font-size: 28px;
  color: #111827;
}

.admin-shell__status {
  font-size: 13px;
  color: #6b7280;
}

.admin-shell__content {
  flex: 1;
  padding: 0 28px 28px;
}

@media (max-width: 960px) {
  .admin-shell {
    grid-template-columns: 1fr;
  }

  .admin-shell__sidebar {
    gap: 16px;
  }

  .admin-shell__nav {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
