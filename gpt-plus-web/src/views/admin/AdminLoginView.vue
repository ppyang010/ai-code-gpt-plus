<script setup lang="ts">
import { LockOnIcon, UserIcon } from 'tdesign-icons-vue-next'
import { MessagePlugin } from 'tdesign-vue-next'
import { computed, reactive } from 'vue'
import { useRouter } from 'vue-router'

import { useAdminAuthStore } from '@/stores/admin-auth'
import type { AdminLoginRequest } from '@/types/admin'

const router = useRouter()
const adminAuthStore = useAdminAuthStore()

/** 登录表单直接复用后台鉴权接口契约，避免前后端字段命名再分叉。 */
const loginForm = reactive<AdminLoginRequest>({
  username: '',
  password: '',
})
const isSubmitting = computed(() => adminAuthStore.isLoading)

/**
 * 登录时先做最小前端校验，再调用后台真实登录接口并跳转到后台首页。
 */
async function handleSubmit() {
  if (!loginForm.username.trim() || !loginForm.password.trim()) {
    MessagePlugin.warning('请输入管理员用户名和密码')
    return
  }

  try {
    await adminAuthStore.login({
      username: loginForm.username.trim(),
      password: loginForm.password,
    })
    MessagePlugin.success('登录成功')
    void router.push('/admin/dashboard')
  } catch (error) {
    MessagePlugin.error(error instanceof Error ? error.message : '后台登录失败，请稍后重试')
  }
}
</script>

<template>
  <div class="admin-login">
    <div class="admin-login__panel">
      <div class="admin-login__brand">GPT Plus Admin</div>
      <h1 class="admin-login__title">管理员登录</h1>
      <p class="admin-login__hint">请输入管理员账号密码进入后台</p>
      <t-form class="admin-login__form" :data="loginForm" @submit.prevent="handleSubmit">
        <t-form-item label="用户名" name="username">
          <t-input v-model="loginForm.username" clearable placeholder="请输入管理员用户名">
            <template #prefix-icon>
              <UserIcon />
            </template>
          </t-input>
        </t-form-item>
        <t-form-item label="密码" name="password">
          <t-input v-model="loginForm.password" type="password" clearable placeholder="请输入管理员密码">
            <template #prefix-icon>
              <LockOnIcon />
            </template>
          </t-input>
        </t-form-item>
        <t-button theme="primary" :loading="isSubmitting" block @click="handleSubmit">登录</t-button>
      </t-form>
    </div>
  </div>
</template>

<style scoped lang="scss">
.admin-login {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 32px 16px;
  background: #f3f5f7;
}

.admin-login__panel {
  width: min(100%, 420px);
  background: #ffffff;
  border: 1px solid #e7eaf0;
  border-radius: 8px;
  padding: 28px 24px;
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.08);
}

.admin-login__brand {
  font-size: 13px;
  color: #5f6b7a;
  margin-bottom: 12px;
}

.admin-login__title {
  margin: 0;
  font-size: 28px;
  color: #111827;
}

.admin-login__hint {
  margin: 8px 0 24px;
  font-size: 14px;
  color: #6b7280;
}

.admin-login__form {
  display: grid;
  gap: 4px;
}
</style>
