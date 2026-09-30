<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, User } from '@element-plus/icons-vue'
import { friendApi } from '@/api'
import type { Friend, UserSearchResult } from '@/types'
import { formatFull } from '@/utils/format'
import UserAvatar from '@/components/UserAvatar.vue'
import PageHeader from '@/components/PageHeader.vue'

const router = useRouter()

const friends = ref<Friend[]>([])
const loading = ref(true)

const keyword = ref('')
const searching = ref(false)
const searched = ref(false)
const results = ref<UserSearchResult[]>([])
/** 发过申请的搜索结果，按钮变灰防止重复点 */
const requested = ref<Set<number>>(new Set())

onMounted(() => void load())

async function load() {
  loading.value = true
  try {
    friends.value = await friendApi.list()
  } finally {
    loading.value = false
  }
}

async function search() {
  const kw = keyword.value.trim()
  if (!kw) {
    ElMessage.warning('输入昵称再搜索')
    return
  }
  searching.value = true
  try {
    results.value = await friendApi.search(kw)
    searched.value = true
  } finally {
    searching.value = false
  }
}

async function addFriend(result: UserSearchResult) {
  try {
    await friendApi.sendRequest(result.userId)
    requested.value.add(result.userId)
    ElMessage.success(`已向 ${result.nickname} 发送好友申请`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '发送失败')
  }
}

async function openChat(friend: Friend) {
  await router.push(`/friends/${friend.userId}`)
}

async function removeFriend(friend: Friend) {
  try {
    await ElMessageBox.confirm(`删除好友「${friend.nickname}」后，聊天记录会在对方那边保留。`, '删除好友', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await friendApi.remove(friend.userId)
    friends.value = friends.value.filter((item) => item.userId !== friend.userId)
    ElMessage.success('已删除好友')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
}
</script>

<template>
  <div class="friends-page">
    <PageHeader title="好友" back-to="/notes" back-label="返回笔记列表">
      <template #actions>
        <span class="muted tnum">{{ friends.length }} 位好友</span>
      </template>
    </PageHeader>

    <div class="friends-body">
      <div class="friends-inner">
        <!-- 搜人加好友 -->
        <section class="search-panel">
          <div class="search-control">
            <el-input
              v-model="keyword"
              placeholder="输入昵称搜索用户"
              clearable
              @keyup.enter="search"
            />
            <el-button type="primary" :loading="searching" @click="search">
              <el-icon><Search /></el-icon>
              <span>搜索</span>
            </el-button>
          </div>
          <ul v-if="searched" class="search-results">
            <li v-if="!results.length" class="muted search-empty">没有找到这个昵称的用户。</li>
            <li v-for="result in results" :key="result.userId" class="search-row">
              <UserAvatar :user="result" :size="34" />
              <span class="search-name">{{ result.nickname }}</span>
              <el-button
                type="primary"
                size="small"
                plain
                :disabled="requested.has(result.userId)"
                @click="addFriend(result)"
              >
                {{ requested.has(result.userId) ? '已申请' : '加好友' }}
              </el-button>
            </li>
          </ul>
        </section>

        <!-- 好友列表 -->
        <div v-if="loading" class="skeleton-wrap">
          <el-skeleton :rows="3" animated />
        </div>

        <div v-else-if="!friends.length" class="empty">
          <span class="empty-icon"><el-icon><User /></el-icon></span>
          <p>还没有好友，在上面搜索昵称加一个吧。</p>
        </div>

        <ul v-else class="friend-list">
          <li v-for="friend in friends" :key="friend.userId" class="friend-row">
            <el-badge :value="friend.unread" :hidden="friend.unread === 0" :max="99" class="friend-badge">
              <UserAvatar :user="friend" :size="42" />
            </el-badge>
            <div class="friend-meta">
              <span class="friend-name">{{ friend.nickname }}</span>
              <span class="muted friend-time">{{ formatFull(friend.friendedAt) }} 成为好友</span>
            </div>
            <div class="friend-actions">
              <el-button type="primary" size="small" @click="openChat(friend)">发消息</el-button>
              <el-button size="small" type="danger" plain @click="removeFriend(friend)">删除</el-button>
            </div>
          </li>
        </ul>
      </div>
    </div>
  </div>
</template>

<style scoped>
.friends-page {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  min-height: 100dvh;
  background: var(--bg);
}

.friends-body {
  flex: 1;
  overflow-y: auto;
}

.friends-inner {
  max-width: 680px;
  margin: 0 auto;
  padding: var(--space-5) var(--space-4) var(--space-12);
}

.search-panel {
  margin-bottom: var(--space-5);
  padding: var(--space-4);
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: var(--radius);
}

.search-control {
  display: flex;
  gap: var(--space-2);
}

.search-results {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  margin: var(--space-4) 0 0;
  padding: 0;
  list-style: none;
}

.search-empty {
  margin: 0;
  font-size: 13.5px;
}

.search-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-2) 0;
}

.search-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
}

.skeleton-wrap {
  padding: var(--space-4);
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: var(--radius);
}

.friend-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}

.friend-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: var(--radius);
}

.friend-badge :deep(.el-badge__content) {
  background: var(--danger-fill);
  color: #fff;
  border: none;
  font-variant-numeric: tabular-nums;
}

.friend-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 0;
}

.friend-name {
  font-weight: 650;
}

.friend-time {
  font-size: 12.5px;
}

.friend-actions {
  display: flex;
  gap: var(--space-2);
}

.empty {
  display: grid;
  justify-items: center;
  gap: var(--space-3);
  padding: var(--space-8) 0;
  color: var(--text-2);
}

.empty-icon {
  display: grid;
  place-items: center;
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: var(--panel-2);
  color: var(--text-3);
}
</style>
