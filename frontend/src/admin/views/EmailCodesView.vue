<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { CopyDocument, Search } from '@element-plus/icons-vue'
import { adminApi } from '../endpoints'
import type { AdminEmailCode } from '../types'
import { formatTime } from '@/utils/format'
import { useDebouncedValue } from '@/composables/useDebounce'
import { copyText } from '@/utils/clipboard'

const PAGE_SIZE = 20

const codes = ref<AdminEmailCode[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const statusFilter = ref<'valid' | 'all' | 'expired' | 'used' | 'blocked'>('valid')
const { input: emailInput, debounced: email } = useDebouncedValue('', 340)

/**
 * 剩余时间用服务端给的秒数做基准，再本地每秒递减。
 * 这样既不用信任浏览器时钟，页面开着不动也能看到验证码自己过期。
 */
const elapsed = ref(0)
let ticker: number | undefined

onMounted(() => {
  ticker = window.setInterval(() => {
    elapsed.value += 1
  }, 1000)
  void load(true)
})

onBeforeUnmount(() => {
  if (ticker) window.clearInterval(ticker)
})

watch(email, () => void load(true))

async function load(reset = false) {
  if (reset) {
    page.value = 1
    loading.value = true
  }
  try {
    const result = await adminApi.emailCodes({
      email: email.value.trim() || undefined,
      status: statusFilter.value,
      page: page.value,
      size: PAGE_SIZE,
    })
    codes.value = reset ? result.records : [...codes.value, ...result.records]
    total.value = result.total
    elapsed.value = 0
  } finally {
    loading.value = false
  }
}

function remainingOf(code: AdminEmailCode): number | null {
  if (code.remainingSeconds == null) return null
  return Math.max(0, code.remainingSeconds - elapsed.value)
}

/** el-table 的作用域插槽把 row 推断成宽松类型，这里收一下 */
function asCode(row: unknown): AdminEmailCode {
  return row as AdminEmailCode
}

/** 加载时还有效，但看着看着就过期了，状态跟着倒计时变 */
function statusOf(code: AdminEmailCode): AdminEmailCode['status'] {
  return code.status === 'VALID' && remainingOf(code) === 0 ? 'EXPIRED' : code.status
}

const statusMeta: Record<AdminEmailCode['status'], { label: string; type: 'success' | 'info' | 'warning' }> = {
  VALID: { label: '有效', type: 'success' },
  EXPIRED: { label: '已过期', type: 'info' },
  USED: { label: '已使用', type: 'info' },
  BLOCKED: { label: '错误过多', type: 'warning' },
}

function formatRemaining(seconds: number | null): string {
  if (seconds == null) return '-'
  if (seconds <= 0) return '已过期'
  const minutes = Math.floor(seconds / 60)
  const rest = seconds % 60
  return minutes > 0 ? `${minutes} 分 ${String(rest).padStart(2, '0')} 秒` : `${rest} 秒`
}

async function copyCode(code: string) {
  try {
    await copyText(code)
    ElMessage.success('验证码已复制')
  } catch {
    ElMessage.error('复制失败，请手动选中')
  }
}
</script>

<template>
  <div>
    <div class="adm-toolbar">
      <el-input
        v-model="emailInput"
        placeholder="按邮箱筛选"
        clearable
        aria-label="按邮箱筛选验证码"
        style="width: 240px"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-select
        v-model="statusFilter"
        aria-label="按状态筛选"
        style="width: 150px"
        @change="load(true)"
      >
        <el-option label="仅有效" value="valid" />
        <el-option label="全部" value="all" />
        <el-option label="已过期" value="expired" />
        <el-option label="已使用" value="used" />
        <el-option label="错误过多" value="blocked" />
      </el-select>
      <span class="muted tnum">共 {{ total }} 条</span>
      <el-button @click="load(true)">刷新</el-button>
    </div>

    <p class="muted hint">
      用于排查「收不到验证码」：这里显示的是明文验证码，有效期内可登录对应邮箱，用过即失效。本页只读，不会改动任何记录。
    </p>

    <div class="adm-card">
      <div class="table-wrap">
        <el-table v-loading="loading" :data="codes" stripe>
          <el-table-column prop="email" label="邮箱" min-width="200" />
          <el-table-column label="验证码" width="150">
            <template #default="{ row }">
              <button type="button" class="code-cell" :title="`复制 ${row.code}`" @click="copyCode(row.code)">
                <span class="code tnum">{{ row.code }}</span>
                <el-icon :size="13"><CopyDocument /></el-icon>
              </button>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="statusMeta[statusOf(asCode(row))].type" size="small" effect="plain">
                {{ statusMeta[statusOf(asCode(row))].label }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="剩余" width="130">
            <template #default="{ row }">
              <span class="tnum">{{ formatRemaining(remainingOf(asCode(row))) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="错误次数" width="100">
            <template #default="{ row }">
              <span class="tnum">{{ row.attempts ?? 0 }} / 5</span>
            </template>
          </el-table-column>
          <el-table-column label="请求 IP" width="150">
            <template #default="{ row }">{{ row.ip || '-' }}</template>
          </el-table-column>
          <el-table-column label="请求时间" width="150">
            <template #default="{ row }">
              <span class="tnum muted">{{ formatTime(row.createdAt) }}</span>
            </template>
          </el-table-column>

          <template #empty>
            <div class="empty">
              {{ statusFilter === 'valid' ? '当前没有有效的验证码。' : '没有符合条件的验证码记录。' }}
              <span v-if="statusFilter === 'valid'">把筛选切到「全部」可以看到历史记录。</span>
            </div>
          </template>
        </el-table>
      </div>

      <div class="adm-toolbar" style="justify-content: center; margin: var(--space-4) 0 0">
        <el-button v-if="codes.length < total" @click="page += 1; load()">加载更多</el-button>
        <span v-else class="muted">没有更多了</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.hint {
  margin: 0 0 var(--space-4);
  font-size: 12.5px;
  line-height: 1.6;
}

.code-cell {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: 2px 8px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--panel-2);
  color: var(--text);
  font-family: inherit;
  cursor: pointer;
  transition: background var(--dur) var(--ease-out), border-color var(--dur) var(--ease-out);
}

.code-cell:hover,
.code-cell:focus-visible {
  border-color: var(--brand-fill);
  background: var(--brand-soft);
}

.code {
  font-size: 14px;
  font-weight: 650;
  letter-spacing: 0.12em;
}

.empty {
  display: flex;
  flex-direction: column;
  gap: 4px;
  color: var(--text-3);
  font-size: 13px;
}
</style>
