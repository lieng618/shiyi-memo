import { ref } from 'vue'

/** 笔记的查看方式：阅读（只读，不挂编辑器）或编辑 */
export type NoteViewMode = 'read' | 'edit'

const STORAGE_KEY = 'note-view-mode'

/**
 * 「新建笔记」「从分享页点编辑」这类入口必须直接进编辑态，
 * 但平时打开笔记又该沿用用户上次选的方式。
 * 于是留一个一次性意图：发起方设置，详情页挂载时取走。
 */
let forcedMode: NoteViewMode | null = null

export function forceNoteViewMode(mode: NoteViewMode) {
  forcedMode = mode
}

function storedMode(): NoteViewMode {
  try {
    return localStorage.getItem(STORAGE_KEY) === 'read' ? 'read' : 'edit'
  } catch {
    // 隐私模式下 localStorage 可能直接抛错，记不住偏好不影响用
    return 'edit'
  }
}

/**
 * 记住上次选的查看方式：习惯「只看不写」的人，之后点开笔记就是阅读态，
 * 真要改了再点一下「编辑」，不必每篇都来回切。
 */
export function useNoteViewMode() {
  const initial = forcedMode ?? storedMode()
  forcedMode = null
  const mode = ref<NoteViewMode>(initial)

  function setMode(next: NoteViewMode) {
    mode.value = next
    try {
      localStorage.setItem(STORAGE_KEY, next)
    } catch {
      /* 写不进去就只在这次会话里生效 */
    }
  }

  return { mode, setMode }
}
