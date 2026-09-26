<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { adminApi } from '../endpoints'
import type { AdminFile } from '../types'
import { fileKindLabel, formatFull, formatSize, formatTime } from '@/utils/format'
import { fileIcon } from '@/utils/fileIcons'
import { useDebouncedValue } from '@/composables/useDebounce'
import SendMessageDialog from '../components/SendMessageDialog.vue'

const PAGE_SIZE = 20

const files = ref<AdminFile[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const { input: keywordInput, debounced: keyword } = useDebouncedValue('', 340)
const blockedFilter = ref<boolean | undefined>(undefined)

const previewOpen = ref(false)
const previewFile = ref<AdminFile | null>(null)
const previewUrl = computed(() => (previewFile.value ? `/api/admin/files/${previewFile.value.id}/raw` : ''))

const msgOpen = ref(false)
const msgUserId = ref<number | null>(null)
const msgLabel = ref('')

onMounted(() => void load(true))
watch(keyword, () => void load(true))

async function load(reset = false) {
  if (reset) {
    page.value = 1
    loading.value = true
  }
  try {
    const result = await adminApi.files({
      keyword: keyword.value.trim() || undefined,
      blocked: blockedFilter.value,
      page: page.value,
      size: PAGE_SIZE,
    })
    files.value = reset ? result.records : [...files.value, ...result.records]
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function patchFile(updated: AdminFile) {
  const index = files.value.findIndex((file) => file.id === updated.id)
  if (index >= 0) {
    files.value[index] = { ...files.value[index], ...updated }
  }
}

function asFile(row: unknown): AdminFile {
  return row as AdminFile
}

function openPreview(file: AdminFile) {
  previewFile.value = file
  previewOpen.value = true
}

/** 屏蔽：填原因 → 自动给文件所有者发站内信 */
async function blockFile(file: AdminFile) {
  let reason = ''
  try {
    const result = await ElMessageBox.prompt(
      `屏蔽「${file.originalName}」？将同时通知上传者。`,
      '屏蔽文件',
      {
        confirmButtonText: '屏蔽',
        cancelButtonText: '取消',
        inputPlaceholder: '屏蔽原因（会写进站内信）',
        inputValue: '',
        type: 'warning',
      },
    )
    reason = result.value ?? ''
  } catch {
    return
  }
  const updated = await adminApi.blockFile(file.id, reason || undefined)
  patchFile(updated)
  ElMessage.success('已屏蔽，并已通知用户')
}

async function unblockFile(file: AdminFile) {
  const updated = await adminApi.unblockFile(file.id)
  patchFile(updated)
  ElMessage.success('已取消屏蔽')
}

function openMessage(file: AdminFile) {
  msgUserId.value = file.userId
  msgLabel.value = file.userEmail ?? ''
  msgOpen.value = true
}
</script>

<template>
  <div>
    <div class="adm-toolbar">
      <el-input v-model="keywordInput" placeholder="搜索文件名" clearable aria-label="搜索文件" style="width: 240px">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-select
        v-model="blockedFilter"
        placeholder="全部状态"
        clearable
        aria-label="按屏蔽状态筛选"
        style="width: 150px"
        @change="load(true)"
      >
        <el-option label="正常" :value="false" />
        <el-option label="已屏蔽" :value="true" />
      </el-select>
      <span class="muted tnum">共 {{ total }} 个文件</span>
      <el-button @click="load(true)">刷新</el-button>
    </div>

    <div class="adm-card">
      <div class="table-wrap">
        <el-table v-loading="loading" :data="files" stripe>
          <el-table-column label="文件" min-width="260">
            <template #default="{ row }">
              <div class="user-cell">
                <el-icon :size="18" class="muted"><component :is="fileIcon(row.kind)" /></el-icon>
                <div class="who">
                  <span class="name">{{ row.originalName }}</span>
                  <span class="mail">{{ fileKindLabel(row.kind) }} · {{ formatSize(row.size) }}</span>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="上传者" min-width="180">
            <template #default="{ row }">
              <span>{{ row.userEmail }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="140">
            <template #default="{ row }">
              <el-tag v-if="row.blocked" type="danger" size="small" effect="plain">已屏蔽</el-tag>
              <el-tag v-else type="success" size="small" effect="plain">正常</el-tag>
              <div v-if="row.blockedReason" class="muted" style="font-size: 12px">{{ row.blockedReason }}</div>
            </template>
          </el-table-column>
          <el-table-column label="上传时间" width="160">
            <template #default="{ row }">
              <span class="tnum">{{ formatTime(row.createdAt) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="230" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="openPreview(asFile(row))">预览</el-button>
              <el-button v-if="!row.blocked" size="small" type="danger" plain @click="blockFile(asFile(row))">屏蔽</el-button>
              <el-button v-else size="small" type="success" plain @click="unblockFile(asFile(row))">取消屏蔽</el-button>
              <el-button size="small" text @click="openMessage(asFile(row))">发信</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="adm-toolbar" style="justify-content: center; margin: var(--space-4) 0 0">
        <el-button v-if="files.length < total" @click="page += 1; load()">加载更多</el-button>
        <span v-else class="muted">没有更多了</span>
      </div>
    </div>

    <el-dialog v-model="previewOpen" :title="previewFile?.originalName" width="min(820px, 94vw)" align-center top="6vh">
      <div v-if="previewFile" class="preview-stage">
        <img v-if="previewFile.kind === 'image'" :src="previewUrl" :alt="previewFile.originalName" class="preview-media" />
        <video v-else-if="previewFile.kind === 'video'" :src="previewUrl" class="preview-media" controls playsinline preload="metadata" />
        <audio v-else-if="previewFile.kind === 'audio'" :src="previewUrl" controls />
        <div v-else class="empty">
          <p>这个格式没法直接预览，可以下载后用本地软件打开。</p>
          <el-button tag="a" :href="previewUrl" type="primary">下载文件</el-button>
        </div>
      </div>
      <template #footer>
        <span v-if="previewFile" class="muted tnum" style="margin-right: auto">
          上传者 {{ previewFile.userEmail }} · {{ formatSize(previewFile.size) }} ·
          {{ formatFull(previewFile.createdAt) }}
        </span>
      </template>
    </el-dialog>

    <SendMessageDialog v-model="msgOpen" :user-id="msgUserId" :user-label="msgLabel" />
  </div>
</template>

<style scoped>
.preview-stage {
  display: grid;
  place-items: center;
  min-height: 200px;
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
</style>
