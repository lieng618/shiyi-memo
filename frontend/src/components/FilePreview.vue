<script setup lang="ts">
import { computed } from 'vue'
import { Document, Download } from '@element-plus/icons-vue'
import type { FileAsset } from '@/types'
import { fileKindLabel, formatFull, formatSize } from '@/utils/format'

const props = defineProps<{ modelValue: boolean; file: FileAsset | null }>()
const emit = defineEmits<{ 'update:modelValue': [boolean] }>()

const open = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const isPdf = computed(() => props.file?.mime === 'application/pdf')
</script>

<template>
  <el-dialog
    v-model="open"
    :title="file?.originalName"
    width="min(940px, 94vw)"
    align-center
    append-to-body
    class="preview-dialog"
  >
    <div v-if="file" class="preview-stage">
      <img
        v-if="file.kind === 'image'"
        :src="file.url"
        :alt="file.originalName"
        class="preview-media"
      />
      <video
        v-else-if="file.kind === 'video'"
        :src="file.url"
        class="preview-media"
        controls
        playsinline
        preload="metadata"
      />
      <audio v-else-if="file.kind === 'audio'" :src="file.url" controls />
      <iframe v-else-if="isPdf" :src="file.url" class="preview-frame" :title="file.originalName" />
      <div v-else class="empty">
        <el-icon :size="22"><Document /></el-icon>
        <p>此文件无法预览，请下载到本地打开</p>
        <el-button type="primary" tag="a" :href="`/api/files/${file.id}/download`">
          <el-icon><Download /></el-icon><span>下载文件</span>
        </el-button>
      </div>
    </div>

    <template #footer>
      <div v-if="file" class="preview-footer">
        <span class="meta-row muted">
          {{ fileKindLabel(file.kind) }} · {{ formatSize(file.size) }} ·
          {{ file.width && file.height ? `${file.width}×${file.height} · ` : '' }}{{ formatFull(file.createdAt) }}
        </span>
        <el-button tag="a" :href="`/api/files/${file.id}/download`">
          <el-icon><Download /></el-icon><span>下载</span>
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.preview-stage {
  display: grid;
  place-items: center;
  min-height: 220px;
  max-height: 68vh;
  background: var(--panel-2);
  border-radius: var(--radius);
  overflow: auto;
}

.preview-media {
  max-width: 100%;
  max-height: 68vh;
  display: block;
}

.preview-frame {
  width: 100%;
  height: 68vh;
  border: none;
}

.preview-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  flex-wrap: wrap;
}
</style>
