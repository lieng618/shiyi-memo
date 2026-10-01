<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Link } from '@element-plus/icons-vue'

const visible = defineModel<boolean>({ default: false })
const router = useRouter()

const input = ref('')
const inputRef = ref<{ focus: () => void } | null>(null)

watch(visible, (open) => {
  if (!open) return
  input.value = ''
  void nextTick(() => inputRef.value?.focus())
  void prefillFromClipboard()
})

/**
 * 打开弹窗时尽力用剪贴板预填：客户端用户通常刚在聊天里复制过链接。
 * 拿不到（网页 http 源、剪贴板权限被拒）就静默放弃，让用户自己粘贴。
 */
async function prefillFromClipboard() {
  try {
    const text = await navigator.clipboard?.readText()
    if (text && extractToken(text)) {
      input.value = text.trim()
    }
  } catch {
    /* 没有剪贴板可读就算了 */
  }
}

/** 粘贴内容可以是完整链接（任意域名，含未来的客户端深链）或纯令牌 */
function extractToken(raw: string): string {
  const value = raw.trim()
  if (!value) return ''
  const match = value.match(/share\/([A-Za-z0-9]+)/i)
  if (match) return match[1]
  return /^[A-Za-z0-9]{16,64}$/.test(value) ? value : ''
}

async function confirm() {
  const token = extractToken(input.value)
  if (!token) {
    ElMessage.warning('没有认出分享链接，请粘贴完整链接后重试')
    return
  }
  visible.value = false
  // 公开分享页自己会区分：是自己的笔记给「编辑笔记」，别人的给「进入我的拾忆」
  await router.push(`/share/${token}`)
}
</script>

<template>
  <el-dialog v-model="visible" width="min(460px, 92vw)" align-center class="open-share-modal">
    <template #header>
      <div class="open-share-heading">
        <span class="open-share-icon"><el-icon :size="19"><Link /></el-icon></span>
        <h2>打开分享链接</h2>
      </div>
    </template>

    <div class="open-share-body">
      <p class="open-share-hint">粘贴别人发给你的分享链接，打开后即可查看这篇笔记。</p>
      <el-input
        ref="inputRef"
        v-model="input"
        clearable
        @keyup.enter="confirm"
      />
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :disabled="!extractToken(input)" @click="confirm">打开</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.open-share-heading {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.open-share-icon {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  flex: 0 0 auto;
  border-radius: 12px;
  background: var(--brand-soft);
  color: var(--brand);
}

.open-share-heading h2 {
  margin: 0;
  color: var(--text);
  font-size: 17px;
  font-weight: 700;
}

.open-share-hint {
  margin: 0 0 var(--space-3);
  color: var(--text-2);
  font-size: 13px;
}
</style>
