<!--
  阅读态点链接时浮出的快捷条：只搬编辑模式里的「复制链接」和「打开链接」两条，
  图标与文案和那边保持一致（见 RichEditor 的 shiyiCopyLink 菜单与内置 viewLink）。
-->
<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { copyText } from '@/utils/clipboard'

const props = defineProps<{ url: string; top: number; left: number }>()
const emit = defineEmits<{ close: [] }>()

const rootRef = ref<HTMLElement | null>(null)
const firstButtonRef = ref<HTMLButtonElement | null>(null)

const COPY_ICON =
  '<svg viewBox="0 0 1024 1024"><path d="M768 832a128 128 0 0 1-128 128H192A128 128 0 0 1 64 832V384a128 128 0 0 1 128-128v64a64 64 0 0 0-64 64v448a64 64 0 0 0 64 64h448a64 64 0 0 0 64-64z"></path><path d="M384 128a64 64 0 0 0-64 64v448a64 64 0 0 0 64 64h448a64 64 0 0 0 64-64V192a64 64 0 0 0-64-64zm0-64h448a128 128 0 0 1 128 128v448a128 128 0 0 1-128 128H384a128 128 0 0 1-128-128V192A128 128 0 0 1 384 64z"></path></svg>'
const OPEN_ICON =
  '<svg viewBox="0 0 1024 1024"><path d="M924.402464 1023.068211H0.679665V99.345412h461.861399v98.909208H99.596867v725.896389h725.896389V561.206811h98.909208z"></path><path d="M930.805104 22.977336l69.965436 69.965436-453.492405 453.492404-69.965435-69.901489z"></path><path d="M1022.464381 304.030081h-98.917201V99.345412H709.230573V0.428211h313.233808z"></path></svg>'

onMounted(async () => {
  document.addEventListener('pointerdown', onDocumentPointerDown, true)
  window.addEventListener('keydown', onKeydown)
  // 位置是按点链接那一刻算的，一滚动 / 改窗口大小就过期：直接收起，别让它浮在别处
  window.addEventListener('scroll', close, true)
  window.addEventListener('resize', close)
  await nextTick()
  // 弹出即聚焦第一条，键盘可以直接接着用（Esc 收起，焦点由调用方还给链接）
  firstButtonRef.value?.focus({ preventScroll: true })
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onDocumentPointerDown, true)
  window.removeEventListener('keydown', onKeydown)
  window.removeEventListener('scroll', close, true)
  window.removeEventListener('resize', close)
})

/** 点到条子外面就收起来；点条子本身不算 */
function onDocumentPointerDown(event: PointerEvent) {
  if (rootRef.value?.contains(event.target as Node)) return
  emit('close')
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    emit('close')
  }
}

function close() {
  emit('close')
}

async function copyLink() {
  try {
    await copyText(props.url)
    ElMessage.success('已复制链接')
  } catch {
    ElMessage.error('复制失败，请手动复制')
  }
  emit('close')
}

function openLink() {
  // 跟编辑模式的 viewLink 一致：网页里新开标签页，桌面端由外壳交给系统浏览器
  window.open(props.url, '_blank', 'noopener')
  emit('close')
}
</script>

<template>
  <Teleport to="body">
    <div
      ref="rootRef"
      class="link-action-bar"
      role="group"
      aria-label="链接操作"
      :style="{ top: `${top}px`, left: `${left}px` }"
    >
      <button
        ref="firstButtonRef"
        type="button"
        class="link-action-item"
        title="复制链接"
        @click="copyLink"
      >
        <span class="link-action-icon" aria-hidden="true" v-html="COPY_ICON" />
        <span>复制链接</span>
      </button>
      <button type="button" class="link-action-item" title="打开链接" @click="openLink">
        <span class="link-action-icon" aria-hidden="true" v-html="OPEN_ICON" />
        <span>打开链接</span>
      </button>
    </div>
  </Teleport>
</template>

<style scoped>
.link-action-bar {
  position: fixed;
  z-index: 2000;
  display: flex;
  gap: 2px;
  /* 定位时按这个宽度预留（MarkdownView 里的 BAR_WIDTH 要跟着改） */
  width: 196px;
  padding: 4px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--panel);
  box-shadow: var(--shadow-lg);
}

.link-action-item {
  display: inline-flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  gap: 5px;
  min-height: 30px;
  padding: 0 6px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--text-2);
  font-family: inherit;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background var(--dur) var(--ease-out), color var(--dur) var(--ease-out);
}

.link-action-item:hover,
.link-action-item:focus-visible {
  background: var(--brand-soft);
  color: var(--brand-hover);
}

.link-action-icon {
  display: inline-flex;
}

.link-action-icon :deep(svg) {
  width: 15px;
  height: 15px;
  fill: currentColor;
}
</style>
