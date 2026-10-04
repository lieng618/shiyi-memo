<!--
  后台发信用的富文本编辑器，基于 wangEditor 5，实现同前台 RichEditor（精简工具栏）。
  差异点：上传走后台自己的接口（上传回调由父组件注入，和用户登录态完全隔离）；
  附件按钮插入指向 /api/files/{id}/download 的下载链接行，落库前由后端统一改写前缀。
-->
<script lang="ts">
  import { Boot } from '@wangeditor/editor'
  import type { IButtonMenu } from '@wangeditor/editor'

  /** 附件按钮与组件实例的桥接：菜单全局注册，exec 时找当前挂载的编辑器组件 */
  type AttachmentTrigger = { trigger: () => void; disabled: () => boolean }
  const triggerStore = globalThis as unknown as { __shiyiMsgAttachmentTrigger?: AttachmentTrigger | null }

  /** 附件上传菜单：实现和内置菜单同款（IButtonMenu），图标、悬停样式自动一致 */
  class InsertAttachmentMenu implements IButtonMenu {
    readonly title = '上传附件'
    readonly iconSvg =
      '<svg viewBox="0 0 1024 1024"><path d="M602.496 240.448A192 192 0 1 1 874.048 512l-316.8 316.8A256 256 0 0 1 195.2 466.752L602.496 59.456l45.248 45.248L240.448 512A192 192 0 0 0 512 783.552l316.8-316.8a128 128 0 1 0-181.056-181.056L353.6 579.904a32 32 0 1 0 45.248 45.248l294.144-294.144 45.312 45.248L444.096 670.4a96 96 0 1 1-135.744-135.744z"></path></svg>'
    readonly tag = 'button'

    getValue(): string {
      return ''
    }

    isActive(): boolean {
      return false
    }

    isDisabled(): boolean {
      const trigger = triggerStore.__shiyiMsgAttachmentTrigger
      return !trigger || trigger.disabled()
    }

    exec(): void {
      triggerStore.__shiyiMsgAttachmentTrigger?.trigger()
    }
  }

  try {
    // vite HMR 会重复执行模块，重复注册直接忽略
    Boot.registerMenu({ key: 'shiyiMsgAttachment', factory: () => new InsertAttachmentMenu() })
  } catch {
    /* 已注册过 */
  }

  /** wangEditor 的空段落，发送前用它判断「正文是不是只有空行」 */
  export function isEmptyRichText(html: string): boolean {
    const text = html.replace(/<[^>]*>/g, '').replace(/&nbsp;/g, ' ').trim()
    return text.length === 0 && !/<img|<video|<audio/i.test(html)
  }
</script>

<template>
  <div class="msg-editor">
    <div class="msg-editor-toolbar-row">
      <Toolbar class="msg-editor-toolbar" :editor="editorRef" mode="simple" :defaultConfig="toolbarConfig" />
      <input ref="fileInput" type="file" hidden @change="onFilePicked" />
    </div>
    <div v-if="uploading" class="msg-editor-progress">
      <el-progress :percentage="progress" :stroke-width="4" :show-text="false" />
      <span class="muted tnum">上传中 {{ progress }}%</span>
    </div>
    <Editor
      class="msg-editor-body"
      :style="{ height: '240px', overflowY: 'hidden' }"
      v-model="modelValue"
      mode="simple"
      :defaultConfig="editorConfig"
      @onCreated="onCreateEditor"
    />
  </div>
</template>

<script setup lang="ts">
  import '@wangeditor/editor/dist/css/style.css'
  import { onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
  import { ElMessage } from 'element-plus'
  import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
  import type { IDomEditor, IEditorConfig, IToolbarConfig } from '@wangeditor/editor'

  type UploadResult = { url: string; name?: string }

  interface Props {
    placeholder?: string
    /**
     * 图片上传：成功后返回可直接访问的 URL（后台前缀，编辑器原位插入）。
     * 附件上传：返回下载 URL 和展示名，编辑器插入为下载链接。
     * 两个回调的第二个参数用于回报上传进度。
     */
    uploadImage: (file: File, onProgress: (percent: number) => void) => Promise<UploadResult>
    uploadAttachment: (file: File, onProgress: (percent: number) => void) => Promise<UploadResult>
  }

  const props = withDefaults(defineProps<Props>(), {
    placeholder: '像写笔记一样随意：文字排版、插图、加附件…',
  })

  const modelValue = defineModel<string>({ required: true })

  const editorRef = shallowRef<IDomEditor>()
  const fileInput = ref<HTMLInputElement | null>(null)
  const uploading = ref(false)
  const progress = ref(0)

  const toolbarConfig: Partial<IToolbarConfig> = {
    toolbarKeys: [
      'headerSelect',
      '|',
      'bold',
      'italic',
      'underline',
      'through',
      '|',
      'color',
      'bgColor',
      '|',
      'bulletedList',
      'numberedList',
      'blockquote',
      '|',
      'emotion',
      'insertLink',
      'shiyiMsgAttachment',
      '|',
      'undo',
      'redo',
    ],
  }

  const editorConfig: Partial<IEditorConfig> = {
    placeholder: props.placeholder,
    MENU_CONF: {
      uploadImage: {
        async customUpload(file: File, insertFn: (url: string, alt: string, href: string) => void) {
          try {
            const result = await props.uploadImage(file, (percent) => (progress.value = percent))
            insertFn(result.url, file.name, '')
          } catch {
            ElMessage.error('图片上传失败')
          }
        },
      },
    },
  }

  const onCreateEditor = (editor: IDomEditor) => {
    editorRef.value = editor
  }

  /** 附件走隐藏 input：选完就上传，成功后在光标处插入下载链接 + 空格（同前台附件的插入方式） */
  function onFilePicked(event: Event) {
    const input = event.target as HTMLInputElement
    const file = input.files?.[0]
    input.value = ''
    if (!file) return
    void (async () => {
      uploading.value = true
      progress.value = 0
      try {
        const result = await props.uploadAttachment(file, (percent) => (progress.value = percent))
        const editor = editorRef.value
        if (editor) {
          try {
            editor.insertNode({
              type: 'link',
              url: result.url,
              target: '_blank',
              children: [{ text: result.name ?? file.name }],
            })
            editor.insertNode({ text: ' ' })
          } catch {
            ElMessage.warning('当前位置不能插入附件')
          }
        }
      } catch {
        ElMessage.error('附件上传失败')
      } finally {
        uploading.value = false
      }
    })()
  }

  onMounted(() => {
    triggerStore.__shiyiMsgAttachmentTrigger = {
      trigger: () => fileInput.value?.click(),
      disabled: () => uploading.value,
    }
  })

  onBeforeUnmount(() => {
    triggerStore.__shiyiMsgAttachmentTrigger = null
    const editor = editorRef.value
    if (editor) {
      editor.destroy()
    }
  })
</script>

<style scoped>
.msg-editor {
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--panel);
  overflow: hidden;
}

.msg-editor-toolbar-row {
  display: flex;
  align-items: center;
  border-bottom: 1px solid var(--border);
}

.msg-editor-progress {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 12px;
  font-size: 12px;
}

.msg-editor-progress .el-progress {
  flex: 1;
}
</style>
