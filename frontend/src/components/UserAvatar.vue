<script setup lang="ts">
import { computed } from 'vue'

/** 结构类型：备忘录用户和后台用户都能用 */
interface AvatarUser {
  nickname?: string | null
  email?: string | null
  avatarUrl?: string | null
}

const props = withDefaults(defineProps<{ user?: AvatarUser | null; size?: number }>(), { size: 36 })

const initial = computed(() => {
  const name = props.user?.nickname?.trim() || props.user?.email?.trim() || '拾'
  return name.slice(0, 1).toUpperCase()
})
</script>

<template>
  <img
    v-if="user?.avatarUrl"
    class="avatar"
    :src="user.avatarUrl"
    :alt="`${user.nickname} 的头像`"
    :width="size"
    :height="size"
    :style="{ width: `${size}px`, height: `${size}px` }"
  />
  <!-- 没有头像时用昵称首字兜底，纯装饰，旁边就是昵称 -->
  <span
    v-else
    class="avatar avatar-fallback"
    aria-hidden="true"
    :style="{ width: `${size}px`, height: `${size}px`, fontSize: `${Math.round(size * 0.42)}px` }"
  >
    {{ initial }}
  </span>
</template>

<style scoped>
.avatar {
  flex: 0 0 auto;
  border-radius: 50%;
  object-fit: cover;
  background: var(--panel-2);
  border: 1px solid var(--border);
}

.avatar-fallback {
  display: grid;
  place-items: center;
  background: var(--brand-fill);
  color: var(--on-brand-fill);
  border-color: transparent;
  font-weight: 700;
  line-height: 1;
}
</style>
