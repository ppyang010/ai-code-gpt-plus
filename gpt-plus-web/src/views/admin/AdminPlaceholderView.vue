<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

/** 复用路由元信息展示页面标题，避免每个占位页都单独维护一份静态文案。 */
const pageTitle = computed(() => String(route.meta.title || '后台页面'))

/** 页面说明同样挂在路由元信息上，后续真实页面替换时只需要删除这一层占位即可。 */
const pageDescription = computed(() => String(route.meta.description || '当前页面契约已预留，具体逻辑待后续接入。'))
</script>

<template>
  <section class="admin-placeholder">
    <h2 class="admin-placeholder__title">{{ pageTitle }}</h2>
    <p class="admin-placeholder__description">{{ pageDescription }}</p>
  </section>
</template>

<style scoped lang="scss">
.admin-placeholder {
  min-height: 280px;
  padding: 28px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
}

.admin-placeholder__title {
  margin: 0;
  font-size: 20px;
  color: #111827;
}

.admin-placeholder__description {
  margin: 10px 0 0;
  font-size: 14px;
  line-height: 1.7;
  color: #6b7280;
}
</style>
