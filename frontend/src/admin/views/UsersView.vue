<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { adminApi } from '../endpoints'
import type { AdminLevel, AdminUser } from '../types'
import { formatSize, formatTime } from '@/utils/format'
import { useDebouncedValue } from '@/composables/useDebounce'
import UserAvatar from '@/components/UserAvatar.vue'
import SendMessageDialog from '../components/SendMessageDialog.vue'

const PAGE_SIZE = 20

const users = ref<AdminUser[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const levels = ref<AdminLevel[]>([])
const { input: keywordInput, debounced: keyword } = useDebouncedValue('', 340)
const statusFilter = ref<number | undefined>(undefined)

const editOpen = ref(false)
const editing = ref<AdminUser | null>(null)
const saving = ref(false)
const form = reactive({ nickname: '', levelId: undefined as number | undefined, status: 1 })

const msgOpen = ref(false)
const msgUserId = ref<number | null>(null)
const msgLabel = ref('')

onMounted(async () => {
  levels.value = await adminApi.levels()
  await load(true)
})

watch(keyword, () => void load(true))

async function load(reset = false) {
  if (reset) {
    page.value = 1
    loading.value = true
  }
  try {
    const result = await adminApi.users({
      keyword: keyword.value.trim() || undefined,
      status: statusFilter.value,
      page: page.value,
      size: PAGE_SIZE,
    })
    users.value = reset ? result.records : [...users.value, ...result.records]
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function patchUser(updated: AdminUser) {
  const index = users.value.findIndex((user) => user.id === updated.id)
  if (index >= 0) {
    users.value[index] = { ...users.value[index], ...updated }
  }
}

/** el-table 的作用域插槽把 row 推断成宽松类型，这里收一下 */
function asUser(row: unknown): AdminUser {
  return row as AdminUser
}

function openEdit(user: AdminUser) {
  editing.value = user
  form.nickname = user.nickname
  form.levelId = user.levelId ?? undefined
  form.status = user.status ?? 1
  editOpen.value = true
}

async function saveEdit() {
  if (!editing.value) return
  if (!form.nickname.trim()) {
    ElMessage.warning('昵称不能为空')
    return
  }
  saving.value = true
  try {
    const updated = await adminApi.updateUser(editing.value.id, {
      nickname: form.nickname.trim(),
      levelId: form.levelId,
      status: form.status,
    })
    patchUser(updated)
    ElMessage.success('已保存')
    editOpen.value = false
  } finally {
    saving.value = false
  }
}

async function toggleStatus(user: AdminUser) {
  const next = user.status === 1 ? 0 : 1
  const updated = await adminApi.updateUser(user.id, { status: next })
  patchUser(updated)
  ElMessage.success(next === 1 ? '已启用' : '已禁用')
}

function openMessage(user: AdminUser) {
  msgUserId.value = user.id
  msgLabel.value = `${user.nickname}（${user.email}）`
  msgOpen.value = true
}
</script>

<template>
  <div>
    <div class="adm-toolbar">
      <el-input
        v-model="keywordInput"
        placeholder="搜索邮箱或昵称"
        clearable
        aria-label="搜索用户"
        style="width: 240px"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-select
        v-model="statusFilter"
        placeholder="全部状态"
        clearable
        aria-label="按状态筛选"
        style="width: 140px"
        @change="load(true)"
      >
        <el-option label="正常" :value="1" />
        <el-option label="已禁用" :value="0" />
      </el-select>
      <span class="muted tnum">共 {{ total }} 个用户</span>
      <el-button @click="load(true)">刷新</el-button>
    </div>

    <div class="adm-card">
      <div class="table-wrap">
        <el-table v-loading="loading" :data="users" stripe>
          <el-table-column label="用户" min-width="220">
            <template #default="{ row }">
              <div class="user-cell">
                <UserAvatar :user="row" :size="34" />
                <div class="who">
                  <span class="name">{{ row.nickname }}</span>
                  <span class="mail">{{ row.email }}</span>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small" effect="plain">
                {{ row.status === 1 ? '正常' : '已禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="levelName" label="等级" width="110" />
          <el-table-column label="已用空间" width="160">
            <template #default="{ row }">
              <span class="tnum">{{ formatSize(row.storageUsed) }} / {{ formatSize(row.maxStorage) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="memoCount" label="笔记" width="80" />
          <el-table-column label="文件" width="100">
            <template #default="{ row }">
              <span class="tnum">{{ row.fileCount }}</span>
              <el-tag v-if="row.blockedFileCount" type="danger" size="small" effect="plain" class="blocked-tag">
                屏蔽 {{ row.blockedFileCount }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="注册 / 最后登录" width="200">
            <template #default="{ row }">
              <div class="tnum muted">{{ formatTime(row.createdAt) }}</div>
              <div class="tnum muted">上次 {{ formatTime(row.lastLoginAt) }}</div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="openEdit(asUser(row))">编辑</el-button>
              <el-button size="small" @click="openMessage(asUser(row))">发信</el-button>
              <el-button
                size="small"
                :type="row.status === 1 ? 'danger' : 'success'"
                plain
                @click="toggleStatus(asUser(row))"
              >
                {{ row.status === 1 ? '禁用' : '启用' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="adm-toolbar" style="justify-content: center; margin: var(--space-4) 0 0">
        <el-button v-if="users.length < total" @click="page += 1; load()">加载更多</el-button>
        <span v-else class="muted">没有更多了</span>
      </div>
    </div>

    <el-dialog v-model="editOpen" title="编辑用户" width="min(460px, 92vw)" align-center>
      <div class="adm-form">
        <div class="field">
          <label for="edit-nickname">昵称</label>
          <el-input id="edit-nickname" v-model="form.nickname" maxlength="64" />
        </div>
        <div class="field">
          <label for="edit-level">等级（决定总空间）</label>
          <el-select id="edit-level" v-model="form.levelId" placeholder="选择等级" style="width: 100%">
            <el-option v-for="level in levels" :key="level.id" :label="`${level.name} · ${formatSize(level.maxStorage)}`" :value="level.id" />
          </el-select>
        </div>
        <div class="field">
          <label for="edit-status">状态</label>
          <el-radio-group id="edit-status" v-model="form.status">
            <el-radio-button :value="1">正常</el-radio-button>
            <el-radio-button :value="0">禁用</el-radio-button>
          </el-radio-group>
        </div>
        <p class="muted" style="font-size: 12.5px">邮箱：{{ editing?.email }}</p>
      </div>
      <template #footer>
        <el-button @click="editOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>

    <SendMessageDialog v-model="msgOpen" :user-id="msgUserId" :user-label="msgLabel" />
  </div>
</template>

<style scoped>
.adm-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.field {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.field label {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--text-2);
}
</style>
