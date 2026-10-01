<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Close } from '@element-plus/icons-vue'
import { loadSmoji, smojiData, smojiUrl } from '@/utils/smoji'

const emit = defineEmits<{
  select: [packId: string, itemId: string]
  close: []
}>()

const activePackId = ref('')
const keyword = ref('')
const loading = ref(true)
const failed = ref(false)

const packs = computed(() => smojiData.value?.packs ?? [])
const activePack = computed(() =>
  packs.value.find((pack) => pack.id === activePackId.value) ?? packs.value[0])
const visibleItems = computed(() => {
  const items = activePack.value?.items ?? []
  const value = keyword.value.trim().toLowerCase()
  return value ? items.filter((item) => item.label.toLowerCase().includes(value)) : items
})

onMounted(async () => {
  try {
    const data = await loadSmoji()
    activePackId.value = data.packs[0]?.id ?? ''
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
})

function choose(itemId: string) {
  if (!activePack.value) return
  emit('select', activePack.value.id, itemId)
}
</script>

<template>
  <div class="emoji-picker" role="dialog" aria-label="选择聊天表情">
    <header class="emoji-head">
      <div>
        <strong>聊天表情</strong>
        <span>{{ activePack?.label ?? '表情' }}</span>
      </div>
      <button type="button" class="close-btn" aria-label="关闭表情面板" @click="emit('close')">
        <el-icon :size="16"><Close /></el-icon>
      </button>
    </header>

    <div v-if="loading" class="emoji-state">
      <el-skeleton :rows="4" animated />
    </div>

    <div v-else-if="failed" class="emoji-state">
      表情数据加载失败，请刷新后重试。
    </div>

    <div v-else class="emoji-body">
      <nav class="pack-rail" aria-label="表情分组">
        <button
          v-for="pack in packs"
          :key="pack.id"
          type="button"
          class="pack-button"
          :class="{ active: pack.id === activePack?.id }"
          :title="pack.label"
          :aria-label="pack.label"
          @click="activePackId = pack.id"
        >
          <img
            v-if="pack.items[0]"
            :src="smojiUrl(pack.id, pack.items[0].id)"
            alt=""
            loading="lazy"
          />
        </button>
      </nav>

      <section class="emoji-content">
        <div class="emoji-toolbar">
          <strong>{{ activePack?.label }}</strong>
          <input
            v-model="keyword"
            type="search"
            placeholder="搜索当前分组"
            aria-label="搜索当前分组表情"
          />
        </div>

        <div class="emoji-grid">
          <button
            v-for="item in visibleItems"
            :key="item.id"
            type="button"
            class="emoji-item"
            :title="item.label"
            :aria-label="item.label"
            @click="choose(item.id)"
          >
            <img
              :src="smojiUrl(activePack?.id ?? '', item.id)"
              :alt="item.label"
              loading="lazy"
            />
          </button>
          <p v-if="!visibleItems.length" class="emoji-empty">没有匹配的表情。</p>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.emoji-picker {
  display: flex;
  flex-direction: column;
  width: min(520px, calc(100vw - 28px));
  height: min(410px, 68vh);
  min-height: 300px;
  color: var(--text);
}

.emoji-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  min-height: 50px;
  padding: 0 var(--space-3) 0 var(--space-4);
  border-bottom: 1px solid var(--border);
}

.emoji-head > div {
  display: flex;
  align-items: baseline;
  gap: var(--space-2);
  min-width: 0;
}

.emoji-head strong {
  font-size: 14px;
}

.emoji-head span {
  overflow: hidden;
  color: var(--text-3);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.close-btn {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--text-3);
  cursor: pointer;
}

.close-btn:hover {
  background: var(--panel-2);
  color: var(--text);
}

.emoji-state {
  flex: 1;
  padding: var(--space-4);
  color: var(--text-3);
  font-size: 13px;
}

.emoji-body {
  display: grid;
  grid-template-columns: 58px minmax(0, 1fr);
  flex: 1;
  min-height: 0;
}

.pack-rail {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-height: 0;
  padding: 7px 6px;
  overflow-y: auto;
  border-right: 1px solid var(--border);
  background: var(--panel-2);
}

.pack-button {
  display: grid;
  place-items: center;
  flex: 0 0 42px;
  width: 42px;
  height: 42px;
  padding: 4px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  cursor: pointer;
}

.pack-button:hover,
.pack-button.active {
  border-color: var(--border-strong);
  background: var(--panel);
}

.pack-button img {
  width: 30px;
  height: 30px;
  object-fit: contain;
}

.emoji-content {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}

.emoji-toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-height: 48px;
  padding: 0 var(--space-3);
  border-bottom: 1px solid var(--border);
}

.emoji-toolbar strong {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.emoji-toolbar input {
  width: 150px;
  height: 30px;
  padding: 0 9px;
  border: 1px solid var(--border);
  border-radius: 7px;
  background: var(--panel);
  color: var(--text);
  font-family: inherit;
  font-size: 12.5px;
}

.emoji-toolbar input:focus {
  border-color: var(--brand-fill);
  outline: none;
  box-shadow: 0 0 0 2px var(--brand-soft);
}

.emoji-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(48px, 1fr));
  align-content: start;
  gap: 4px;
  flex: 1;
  min-height: 0;
  padding: var(--space-3);
  overflow-y: auto;
}

.emoji-item {
  display: grid;
  place-items: center;
  aspect-ratio: 1;
  min-width: 0;
  padding: 5px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  cursor: pointer;
}

.emoji-item:hover,
.emoji-item:focus-visible {
  border-color: var(--border);
  background: var(--panel-2);
}

.emoji-item img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.emoji-empty {
  grid-column: 1 / -1;
  margin: var(--space-8) 0 0;
  color: var(--text-3);
  font-size: 13px;
  text-align: center;
}

@media (max-width: 640px) {
  .emoji-picker {
    height: min(360px, 58vh);
    min-height: 280px;
  }

  .emoji-body {
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: 54px minmax(0, 1fr);
  }

  .pack-rail {
    flex-direction: row;
    padding: 6px;
    overflow-x: auto;
    overflow-y: hidden;
    border-right: none;
    border-bottom: 1px solid var(--border);
  }

  .pack-button {
    flex-basis: 40px;
    width: 40px;
    height: 40px;
  }

  .pack-button img {
    width: 28px;
    height: 28px;
  }

  .emoji-toolbar input {
    width: 128px;
  }
}
</style>
