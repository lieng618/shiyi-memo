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
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-height: 60px;
  padding: var(--space-2) var(--space-4);
  background: var(--panel);
  border-bottom: 1px solid var(--border);
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
