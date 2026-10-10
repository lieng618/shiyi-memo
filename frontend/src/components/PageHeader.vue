<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'

withDefaults(defineProps<{ title: string; backTo?: string; backLabel?: string }>(), {
  backTo: '/notes',
  backLabel: '返回',
})

const router = useRouter()
</script>

<template>
  <header class="page-header">
    <button type="button" class="back-btn" :aria-label="backLabel" :title="backLabel" @click="router.push(backTo)">
      <el-icon :size="18"><ArrowLeft /></el-icon>
    </button>
    <h1>{{ title }}</h1>
    <div class="header-actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<style scoped>
.page-header {
  /* 页面整体滚动时（有些页面没有自己的滚动容器），顶栏要始终停在窗口顶部，
     否则往下滑就看不到返回按钮，用户得先滑回顶部才能退出 */
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-height: 60px;
  padding: var(--space-2) var(--space-4);
  /* 和 AppShell 的顶栏保持一致：与页面同色，不留分隔线 */
  background: var(--bg);
  /* 桌面端无边框窗口：右侧给系统按钮让位，顶栏兼作拖动区 */
  padding-right: calc(var(--space-4) + var(--titlebar-inset));
  -webkit-app-region: drag;
}

.page-header :is(button, a, input) {
  -webkit-app-region: no-drag;
}

.page-header h1 {
  margin: 0;
  font-size: 16px;
  font-weight: 650;
}

.back-btn {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-2);
  cursor: pointer;
  transition: background var(--dur) var(--ease-out), color var(--dur) var(--ease-out);
}

.back-btn:hover {
  background: var(--panel-hover);
  color: var(--text);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-left: auto;
}
</style>
