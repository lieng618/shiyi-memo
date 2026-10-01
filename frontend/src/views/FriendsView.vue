<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, User } from '@element-plus/icons-vue'
import { friendApi } from '@/api'
import type { Friend, FriendRequest, UserSearchResult } from '@/types'
import { formatFull } from '@/utils/format'
import { useFriendSummary } from '@/composables/useFriendSummary'
import { onRealtime } from '@/composables/useRealtime'
import UserAvatar from '@/components/UserAvatar.vue'
import PageHeader from '@/components/PageHeader.vue'

type FriendTab = 'requests' | 'add' | 'friends'

const route = useRoute()
const router = useRouter()
const { refresh: refreshFriendSummary, setPending } = useFriendSummary()

const activeTab = ref<FriendTab>(tabFromQuery())
const friends = ref<Friend[]>([])
const loading = ref(true)

const incomingRequests = ref<FriendRequest[]>([])
const outgoingRequests = ref<FriendRequest[]>([])
const requestsLoading = ref(true)
const requestTab = ref<'incoming' | 'outgoing'>('incoming')

const keyword = ref('')
const searching = ref(false)
const searched = ref(false)
const results = ref<UserSearchResult[]>([])
const addingId = ref<number | null>(null)
let offChat: (() => void) | undefined
let offFriends: (() => void) | undefined

const pendingIncomingCount = computed(() =>
  incomingRequests.value.filter((item) => item.status === 'PENDING').length)
const outgoingIds = computed(() => new Set(
  outgoingRequests.value
    .filter((item) => item.status === 'PENDING')
    .map((item) => item.userId)))

onMounted(() => {
  void load()
  offChat = onRealtime('chat', () => {
    void loadFriends()
    void refreshFriendSummary()
  })
  offFriends = onRealtime('friends', () => void load())
})

onBeforeUnmount(() => {
  offChat?.()
  offFriends?.()
})

watch(() => route.query.tab, () => {
  activeTab.value = tabFromQuery()
})

function tabFromQuery(): FriendTab {
  const tab = String(route.query.tab ?? '')
  if (tab === 'requests' || tab === 'add') {
    return tab
  }
  return 'friends'
}

async function load() {
  loading.value = true
  requestsLoading.value = true
  try {
    const [friendList, requestList] = await Promise.all([
      friendApi.list(),
      friendApi.requests(),
    ])
    friends.value = friendList
    applyRequests(requestList.incoming, requestList.outgoing)
  } finally {
    loading.value = false
    requestsLoading.value = false
  }
}

async function loadFriends() {
  friends.value = await friendApi.list()
}

function applyRequests(incoming: FriendRequest[], outgoing: FriendRequest[]) {
  incomingRequests.value = incoming
  outgoingRequests.value = outgoing
  setPending(incoming.filter((item) => item.status === 'PENDING').length)
}

async function search() {
  const value = keyword.value.trim()
  if (!value) {
    ElMessage.warning('输入昵称再搜索')
    return
  }
  searching.value = true
  try {
    results.value = await friendApi.search(value)
    searched.value = true
  } finally {
    searching.value = false
  }
}

async function addFriend(result: UserSearchResult) {
  if (outgoingIds.value.has(result.userId)) return
  addingId.value = result.userId
  try {
    await friendApi.sendRequest(result.userId)
    const requestList = await friendApi.requests()
    applyRequests(requestList.incoming, requestList.outgoing)
    ElMessage.success(`已向 ${result.nickname} 发送好友申请`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '发送失败')
  } finally {
    addingId.value = null
  }
}

async function handleRequest(request: FriendRequest, accept: boolean) {
  try {
    if (accept) {
      await friendApi.accept(request.id)
      await loadFriends()
      ElMessage.success(`已添加 ${request.nickname} 为好友`)
    } else {
      await friendApi.reject(request.id)
      ElMessage.info('已拒绝好友申请')
    }
    request.status = accept ? 'ACCEPTED' : 'REJECTED'
    setPending(pendingIncomingCount.value)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作失败')
  }
}

async function cancelRequest(request: FriendRequest) {
  try {
    await friendApi.cancelRequest(request.id)
    request.status = 'CANCELED'
    ElMessage.success('已撤回好友申请')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '撤回失败')
  }
}

function requestResult(status: FriendRequest['status']) {
  if (status === 'ACCEPTED') return '已添加'
  if (status === 'REJECTED') return '已拒绝'
  if (status === 'CANCELED') return '已撤回'
  return '等待验证'
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
    <PageHeader title="好友" back-to="/notes" back-label="返回笔记列表" />

    <div class="friends-body">
      <div class="tabs-shell">
        <nav class="primary-tabs" role="tablist" aria-label="好友页面">
          <button
            id="friends-tab"
            type="button"
            role="tab"
            :aria-selected="activeTab === 'friends'"
            :class="{ active: activeTab === 'friends' }"
            @click="activeTab = 'friends'"
          >
            我的好友
          </button>
          <button
            id="requests-tab"
            type="button"
            role="tab"
            :aria-selected="activeTab === 'requests'"
            :class="{ active: activeTab === 'requests' }"
            @click="activeTab = 'requests'"
          >
            <span>好友申请</span>
            <span v-if="pendingIncomingCount" class="tab-count">{{ pendingIncomingCount }}</span>
          </button>
          <button
            id="add-tab"
            type="button"
            role="tab"
            :aria-selected="activeTab === 'add'"
            :class="{ active: activeTab === 'add' }"
            @click="activeTab = 'add'"
          >
            添加好友
          </button>
        </nav>
      </div>

      <div class="friends-inner">
        <section
          v-if="activeTab === 'friends'"
          class="tab-panel"
          role="tabpanel"
          aria-labelledby="friends-tab"
        >
          <div v-if="loading" class="panel-loading">
            <el-skeleton :rows="3" animated />
          </div>

          <div v-else-if="!friends.length" class="panel-empty panel-empty--tall">
            <span class="empty-icon"><el-icon><User /></el-icon></span>
            <p>还没有好友，去“添加好友”页签找一个人吧。</p>
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
        </section>

        <section
          v-else-if="activeTab === 'requests'"
          class="tab-panel"
          role="tabpanel"
          aria-labelledby="requests-tab"
        >
          <div class="request-toolbar">
            <div class="request-tabs" role="tablist" aria-label="好友申请分类">
              <button
                id="incoming-requests-tab"
                type="button"
                role="tab"
                :aria-selected="requestTab === 'incoming'"
                :class="{ active: requestTab === 'incoming' }"
                @click="requestTab = 'incoming'"
              >
                收到的申请
              </button>
              <button
                id="outgoing-requests-tab"
                type="button"
                role="tab"
                :aria-selected="requestTab === 'outgoing'"
                :class="{ active: requestTab === 'outgoing' }"
                @click="requestTab = 'outgoing'"
              >
                我发出的
              </button>
            </div>
          </div>

          <div v-if="requestsLoading" class="panel-loading">
            <el-skeleton :rows="3" animated />
          </div>

          <ul
            v-else-if="requestTab === 'incoming' && incomingRequests.length"
            class="request-list"
            aria-labelledby="incoming-requests-tab"
          >
            <li v-for="request in incomingRequests" :key="request.id" class="request-row">
              <UserAvatar :user="request" :size="40" />
              <div class="request-meta">
                <span class="request-name">{{ request.nickname }}</span>
                <span class="muted request-time">
                  {{ formatFull(request.createdAt) }} 申请加你为好友
                </span>
              </div>
              <div class="request-actions">
                <template v-if="request.status === 'PENDING'">
                  <el-button type="primary" size="small" @click="handleRequest(request, true)">同意</el-button>
                  <el-button size="small" @click="handleRequest(request, false)">拒绝</el-button>
                </template>
                <span v-else class="request-result">{{ requestResult(request.status) }}</span>
              </div>
            </li>
          </ul>

          <ul
            v-else-if="requestTab === 'outgoing' && outgoingRequests.length"
            class="request-list"
            aria-labelledby="outgoing-requests-tab"
          >
            <li v-for="request in outgoingRequests" :key="request.id" class="request-row">
              <UserAvatar :user="request" :size="40" />
              <div class="request-meta">
                <span class="request-name">{{ request.nickname }}</span>
                <span class="muted request-time">
                  {{ formatFull(request.createdAt) }} · {{ requestResult(request.status) }}
                </span>
              </div>
              <div class="request-actions">
                <span v-if="request.status !== 'PENDING'" class="request-result">
                  {{ requestResult(request.status) }}
                </span>
                <el-button
                  v-if="request.status === 'PENDING'"
                  size="small"
                  plain
                  @click="cancelRequest(request)"
                >
                  撤回
                </el-button>
              </div>
            </li>
          </ul>
        </section>

        <section
          v-else
          class="tab-panel"
          role="tabpanel"
          aria-labelledby="add-tab"
        >
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

          <ul v-if="searched && results.length" class="search-results">
            <li v-for="result in results" :key="result.userId" class="search-row">
              <UserAvatar :user="result" :size="38" />
              <div class="search-meta">
                <span class="search-name">{{ result.nickname }}</span>
                <span v-if="outgoingIds.has(result.userId)" class="muted search-state">等待对方验证</span>
              </div>
              <el-button
                type="primary"
                size="small"
                plain
                :loading="addingId === result.userId"
                :disabled="outgoingIds.has(result.userId)"
                @click="addFriend(result)"
              >
                加好友
              </el-button>
            </li>
          </ul>

          <div v-else-if="searched" class="panel-empty">没有找到这个昵称的用户。</div>
        </section>
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
  min-height: 0;
  overflow-y: auto;
}

.tabs-shell {
  position: sticky;
  top: 0;
  z-index: 5;
  border-bottom: 1px solid var(--border);
  background: color-mix(in srgb, var(--bg) 96%, transparent);
  backdrop-filter: blur(12px);
}

.primary-tabs {
  display: flex;
  align-items: stretch;
  gap: 28px;
  max-width: 760px;
  min-height: 50px;
  margin: 0 auto;
  padding: 0 var(--space-4);
  overflow-x: auto;
  overflow-y: hidden;
  scrollbar-width: none;
}

.primary-tabs::-webkit-scrollbar {
  display: none;
}

.primary-tabs button {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex: 0 0 auto;
  padding: 0;
  border: none;
  background: transparent;
  color: var(--text-3);
  font-family: inherit;
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
}

.primary-tabs button::after {
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  height: 2px;
  border-radius: 2px 2px 0 0;
  background: transparent;
  content: "";
}

.primary-tabs button:hover,
.primary-tabs button.active {
  color: var(--text);
}

.primary-tabs button.active::after {
  background: var(--brand-fill);
}

.tab-count {
  min-width: 19px;
  padding: 1px 6px;
  border-radius: 999px;
  background: var(--brand-soft);
  color: var(--brand-hover);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  text-align: center;
}

.friends-inner {
  max-width: 760px;
  margin: 0 auto;
  padding: var(--space-5) var(--space-4) var(--space-12);
}

.tab-panel {
  min-width: 0;
  padding: var(--space-5);
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: var(--radius);
}

.request-toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.request-tabs {
  display: inline-flex;
  gap: 2px;
  padding: 3px;
  border-radius: 9px;
  background: var(--panel-2);
}

.request-tabs button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 32px;
  padding: 0 12px;
  border: none;
  border-radius: 7px;
  background: transparent;
  color: var(--text-3);
  font-family: inherit;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.request-tabs button.active {
  background: var(--panel);
  color: var(--text);
  box-shadow: 0 1px 3px rgb(0 0 0 / 9%);
}

.panel-loading {
  margin-top: var(--space-4);
}

.request-list,
.search-results,
.friend-list {
  display: flex;
  flex-direction: column;
  margin: var(--space-5) 0 0;
  padding: 0;
  list-style: none;
}

.request-row,
.search-row,
.friend-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-width: 0;
  padding: var(--space-3) 0;
}

.request-meta,
.friend-meta,
.search-meta {
  display: flex;
  flex-direction: column;
  gap: 3px;
  flex: 1;
  min-width: 0;
}

.request-name,
.friend-name,
.search-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 650;
}

.request-time,
.friend-time,
.search-state {
  font-size: 12.5px;
}

.request-actions,
.friend-actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.request-result {
  color: var(--text-3);
  font-size: 12.5px;
  font-weight: 600;
  white-space: nowrap;
}

.panel-empty {
  margin-top: var(--space-5);
  padding: var(--space-6) 0 var(--space-2);
  color: var(--text-3);
  font-size: 13px;
  text-align: center;
}

.panel-empty--tall {
  display: grid;
  justify-items: center;
  gap: var(--space-3);
  padding: var(--space-10) 0 var(--space-8);
}

.panel-empty--tall p {
  margin: 0;
}

.search-control {
  display: flex;
  gap: var(--space-2);
}

.friend-list {
  margin-top: 0;
}

.friend-badge :deep(.el-badge__content) {
  background: var(--danger-fill);
  color: #fff;
  border: none;
  font-variant-numeric: tabular-nums;
}

.empty-icon {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: var(--panel-2);
  color: var(--text-3);
}

@media (max-width: 640px) {
  .primary-tabs {
    gap: 22px;
  }

  .tab-panel {
    padding: var(--space-4);
  }

  .request-row,
  .friend-row {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .request-actions,
  .friend-actions {
    width: 100%;
    padding-left: 52px;
  }
}
</style>
