<!--
  阅读态点链接时浮出的快捷条：只搬编辑模式里的「复制链接」和「打开链接」两条。
  图标与观感都对齐编辑模式的超链接悬浮条（见 RichEditor 的 shiyiCopyLink 菜单与内置
  viewLink）：同样是纯图标按钮、悬停出提示。尺寸取自 wangEditor 的 .w-e-hover-bar /
  .w-e-bar-item / .w-e-menu-tooltip-v5，以及 rich-editor.scss 里那几处覆写。
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
      <div class="link-action-item">
        <button
          ref="firstButtonRef"
          type="button"
          class="link-action-btn"
          data-tooltip="复制链接"
          aria-label="复制链接"
          @click="copyLink"
        >
          <span class="link-action-icon" aria-hidden="true" v-html="COPY_ICON" />
        </button>
      </div>
      <div class="link-action-item">
        <button
          type="button"
          class="link-action-btn"
          data-tooltip="打开链接"
          aria-label="打开链接"
          @click="openLink"
        >
          <span class="link-action-icon" aria-hidden="true" v-html="OPEN_ICON" />
        </button>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.link-action-bar {
  position: fixed;
  z-index: 2000;
  display: flex;
  /* 两个 38px 的按钮位 + 左右 5px 内边距 + 2px 边框 = 88px；
     定位时按这个宽度预留（MarkdownView 里的 BAR_WIDTH 要跟着改） */
  width: 88px;
  padding: 0 5px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--panel);
  box-shadow: 0 2px 5px rgb(0 0 0 / 12%);
  /* 提示文字用 0.75em，跟着这个走 */
  font-size: 14px;
}

.link-action-item {
  /* 提示的定位上下文：按钮不设 position，伪元素才会挂在这一层上 */
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 40px;
  padding: 4px;
}

.link-action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 32px;
  padding: 0 8px;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-2);
  cursor: pointer;
  transition: background var(--dur) var(--ease-out);
}

.link-action-btn:hover,
.link-action-btn:focus-visible {
  background: var(--panel-hover);
}

.link-action-icon {
  display: inline-flex;
}

.link-action-icon :deep(svg) {
  width: 14px;
  height: 14px;
  fill: var(--text-2);
}

/* 悬停提示：文案、箭头、位置和渐显节奏都照编辑模式那条来 */
.link-action-btn::before {
  content: attr(data-tooltip);
  position: absolute;
  top: 40px;
  padding: 5px 10px;
  border-radius: 5px;
  background: var(--brand);
  color: var(--panel);
  font-size: 0.75em;
  text-align: center;
  white-space: pre;
  opacity: 0;
  visibility: hidden;
  transition: opacity 0.6s;
  z-index: 1;
}

.link-action-btn::after {
  content: '';
  position: absolute;
  top: 30px;
  border: 5px solid transparent;
  border-bottom-color: var(--brand);
  opacity: 0;
  visibility: hidden;
  transition: opacity 0.6s;
}

.link-action-btn:hover::before,
.link-action-btn:hover::after,
.link-action-btn:focus-visible::before,
.link-action-btn:focus-visible::after {
  opacity: 1;
  visibility: visible;
}
</style>
