<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { adminApi } from '../endpoints'
import type { AdminLevel, LevelPayload } from '../types'
import { bytesToGb, gbToBytes } from '@/utils/format'

const levels = ref<AdminLevel[]>([])
const loading = ref(false)
const dialogOpen = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const errorMessage = ref('')

const form = reactive({
  code: '',
  name: '',
  maxStorageGb: 2,
  description: '',
  sort: 100,
  isDefault: false,
})

onMounted(load)

async function load() {
  loading.value = true
  try {
    levels.value = await adminApi.levels()
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { code: '', name: '', maxStorageGb: 2, description: '', sort: 100, isDefault: false })
  errorMessage.value = ''
  dialogOpen.value = true
}

function asLevel(row: unknown): AdminLevel {
  return row as AdminLevel
}

function openEdit(level: AdminLevel) {
  editingId.value = level.id
  Object.assign(form, {
    code: level.code,
    name: level.name,
    maxStorageGb: bytesToGb(level.maxStorage),
    description: level.description ?? '',
    sort: level.sort ?? 100,
    isDefault: Boolean(level.isDefault),
  })
  errorMessage.value = ''
  dialogOpen.value = true
}

async function save() {
  errorMessage.value = ''
  if (!form.name.trim()) {
    errorMessage.value = '请填写等级名称'
    return
  }
  const payload: LevelPayload = {
    code: form.code.trim() || undefined,
    name: form.name.trim(),
    maxStorage: gbToBytes(form.maxStorageGb),
    description: form.description.trim() || null,
    sort: form.sort,
    isDefault: form.isDefault,
  }
  saving.value = true
  try {
    if (editingId.value === null) {
      await adminApi.createLevel(payload)
    } else {
      await adminApi.updateLevel(editingId.value, payload)
    }
    ElMessage.success('已保存')
    dialogOpen.value = false
    await load()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '保存失败'
  } finally {
    saving.value = false
  }
}

async function remove(level: AdminLevel) {
  try {
    await ElMessageBox.confirm(`删除等级「${level.name}」？还有用户属于它时需要先调整这些用户。`, '删除等级', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  await adminApi.deleteLevel(level.id)
  ElMessage.success('已删除')
  await load()
}
</script>

<template>
  <div>
    <div class="adm-toolbar">
      <div style="flex: 1" />
      <el-button type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon><span>新建等级</span>
      </el-button>
    </div>

    <div class="adm-card">
      <div class="table-wrap">
        <el-table v-loading="loading" :data="levels" stripe>
          <el-table-column label="名称 / 编码" min-width="200">
            <template #default="{ row }">
              <div style="font-weight: 600">{{ row.name }}</div>
              <div class="muted" style="font-size: 12px">{{ row.code }}</div>
            </template>
          </el-table-column>
          <el-table-column label="总空间" width="140">
            <template #default="{ row }">
              <span class="tnum">{{ bytesToGb(row.maxStorage) }} GB</span>
            </template>
          </el-table-column>
          <el-table-column prop="userCount" label="用户数" width="100" />
          <el-table-column label="默认等级" width="110">
            <template #default="{ row }">
              <el-tag v-if="row.isDefault" type="success" size="small" effect="plain">默认</el-tag>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column prop="description" label="说明" min-width="200" />
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="openEdit(asLevel(row))">编辑</el-button>
              <el-button size="small" type="danger" plain @click="remove(asLevel(row))">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <el-dialog v-model="dialogOpen" :title="editingId === null ? '新建等级' : '编辑等级'" width="min(480px, 92vw)" align-center>
      <div class="adm-form">
        <div class="field">
          <label for="level-name">名称</label>
          <el-input id="level-name" v-model="form.name" maxlength="64" placeholder="例如：标准版" />
        </div>
        <div class="field">
          <label for="level-code">编码</label>
          <el-input id="level-code" v-model="form.code" maxlength="32" placeholder="例如：standard" />
        </div>
        <div class="field">
          <label for="level-storage">总空间（GB）</label>
          <el-input-number id="level-storage" v-model="form.maxStorageGb" :min="1" :max="102400" controls-position="right" />
        </div>
        <div class="field">
          <label for="level-desc">说明</label>
          <el-input id="level-desc" v-model="form.description" maxlength="255" />
        </div>
        <div class="field">
          <label for="level-sort">排序</label>
          <el-input-number id="level-sort" v-model="form.sort" :min="0" :max="9999" controls-position="right" />
        </div>
        <div class="field">
          <label for="level-default">设为新用户默认等级</label>
          <el-switch id="level-default" v-model="form.isDefault" />
        </div>
        <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>
      </div>
      <template #footer>
        <el-button @click="dialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
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

.error {
  margin: 0;
  color: var(--danger);
  font-size: 13px;
}
</style>
