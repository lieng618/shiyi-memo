<script setup lang="ts">
  import { computed } from 'vue'
  import { useUpdater } from '@/composables/useUpdater'

  const { downloading, downloaded, version, percent } = useUpdater()

  /** 只有真的在下载/安装时才浮出来，检查更新不打扰用户 */
  const visible = computed(() => downloading.value || downloaded.value)

  const title = computed(() => (downloaded.value ? '正在安装更新' : '正在下载更新'))
  const hint = computed(() =>
    downloaded.value ? '安装完成后，拾忆 将重新启动。' : `新版本 ${version.value}，下载完成后会自动安装。`,
  )
</script>

<template>
  <Transition name="updater-fade">
    <section v-if="visible" class="updater" role="status" aria-live="polite">
      <h2>{{ title }}</h2>
      <p>{{ hint }}</p>
      <div class="updater-progress">
        <el-progress
          :percentage="downloaded ? 100 : percent"
          :show-text="false"
          :stroke-width="6"
          color="var(--brand-fill)"
        />
        <span class="updater-status tnum">{{ downloaded ? '正在准备…' : `${percent}%` }}</span>
      </div>
    </section>
  </Transition>
</template>

<style scoped>
.updater {
  position: fixed;
  right: var(--space-6);
  bottom: var(--space-6);
  z-index: 3000;
  width: min(340px, calc(100vw - var(--space-6) * 2));
  padding: var(--space-4) var(--space-5);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--panel);
  box-shadow: var(--shadow-lg);
}

.updater h2 {
  margin: 0 0 var(--space-1);
  font-size: 15px;
  font-weight: 650;
}

.updater p {
  margin: 0 0 var(--space-3);
  color: var(--text-2);
  font-size: 13px;
}

.updater-progress {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.updater-progress :deep(.el-progress) {
  flex: 1;
}

.updater-status {
  flex: none;
  color: var(--text-2);
  font-size: 12px;
}

.updater-fade-enter-active,
.updater-fade-leave-active {
  transition: opacity 0.2s ease;
}

.updater-fade-enter-from,
.updater-fade-leave-to {
  opacity: 0;
}
</style>
